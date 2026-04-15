package com.example.pricelist.data

import android.content.Context
import android.util.Log
import com.example.pricelist.util.AppPrefs
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.ktx.storage
import kotlinx.coroutines.tasks.await
import java.io.File

class Repository(
    private val dao: ItemDao
) {
    private val firestore by lazy { FirebaseFirestore.getInstance() }
    private val storageRef by lazy { Firebase.storage.reference }

    private val debugLog = false // Set true for testing search scores

    /**
     * Sync Firestore → Room and download images **only if they are not already cached**.
     */
    suspend fun sync(context: Context) {
        val lastSync = AppPrefs.getLastSyncTime(context)
        val imageDir = File(context.filesDir, "images").apply { mkdirs() }


        Log.d("Repository", "Delta sync since: $lastSync")
        val updatedItems  = firestore
            .collection("items")
            .whereGreaterThan("lastFBUpdate", com.google.firebase.Timestamp(lastSync / 1000, 0))
            .get()
            .await()
            .documents
            .mapNotNull { doc ->
                try {
                    val data = doc.data ?: return@mapNotNull null
                    val lastUpdated = (data["lastFBUpdate"] as? com.google.firebase.Timestamp)
                        ?.toDate()?.time ?: 0L

                    ItemEntity(
                        Code = data["Code"] as? String ?: "",
                        DiscPercent = (data["DiscPercent"] as? Number)?.toDouble() ?: 0.0,
                        MRP = (data["MRP"] as? Number)?.toDouble() ?: 0.0,
                        MasterCode = data["MasterCode"]?.toString() ?: "",
                        Name = data["Name"] as? String ?: "",
                        PRICE3 = (data["PRICE3"] as? Number)?.toDouble() ?: 0.0,
                        Unit = data["Unit"] as? String ?: "",
                        imageExt = data["imageExt"] as? String ?: "",
                        imageH = (data["imageH"] as? Number)?.toInt() ?: 0,
                        imageW = (data["imageW"] as? Number)?.toInt() ?: 0,
                        imageYes = data["imageYes"] as? Boolean ?: false,
                        // Read TaxPercent if present in Firestore
                        TaxPercent = (data["TaxPercent"] as? Number)?.toDouble() ?: 0.0,
                        lastFBUpdate = lastUpdated
                    )
                } catch (e: Exception) {
                    Log.e("Repository", "Error parsing document: ${doc.id}", e)
                    null
                }
            }

        Log.d("Repository", "Delta fetched ${updatedItems .size} items")

        // 2️⃣ Get all MasterCodes from Firestore
        val allActiveMasterCodes = try {
            val doc = firestore.collection("DB_Service")
                .document("active_ids_snapshot")
                .get()
                .await()

            doc.get("activeMasterCodes") as? List<String> ?: emptyList()
        } catch (e: Exception) {
            Log.e("Repository", "Failed to fetch activeMasterCodes", e)
            emptyList()
        }.toSet()

        // If first sync: clear all, else: just insert or update
        if (lastSync == 0L) {
            dao.clearAll()
        }
        dao.insertAll(updatedItems )
        // ✅ Update last sync time (if there are any items)
        if (updatedItems .isNotEmpty()) {
            val latestUpdate = updatedItems .maxOfOrNull { it.lastFBUpdate } ?: System.currentTimeMillis()
            AppPrefs.setLastSyncTime(context, latestUpdate)
            Log.d("Repository", "Updated local lastSync to $latestUpdate")
        }
        Log.d("Repository", "Delta sync since: $lastSync (${java.util.Date(lastSync)})")

        // 3️⃣ Get all MasterCodes from local Room DB
        val localItems = dao.getAllItems()
        val localMasterCodes = localItems.map { it.MasterCode }.toSet()

        // 4️⃣ Identify items that exist in Room but not in Firestore → DELETE them
        val itemsToDelete = localMasterCodes.subtract(allActiveMasterCodes)
        Log.d("Repository", "itemsToDelete ${itemsToDelete.size} , localItems ${localItems.size}, firestoreMasterCodes ${allActiveMasterCodes.size}")
        if (itemsToDelete.isNotEmpty()) {
            Log.d("Repository", "Removing ${itemsToDelete.size} deleted items from local DB")
            dao.deleteByMasterCodes(itemsToDelete.toList())
        }
        // 4️⃣ Add new items that are in Firestore but not in Room
        val itemsToAdd = allActiveMasterCodes.subtract(localMasterCodes)
        val newItems = mutableListOf<ItemEntity>()
        if (itemsToAdd.isNotEmpty()) {
            Log.d("Repository", "Fetching ${itemsToAdd.size} new items from Firestore")
            val chunks = itemsToAdd.chunked(10) // Firestore `in` clause supports max 10
            for (chunk in chunks) {
                val snapshot = firestore.collection("items")
                    .whereIn(FieldPath.documentId(), chunk.toList())
                    .get()
                    .await()

                val parsed = snapshot.documents.mapNotNull { doc ->
                    parseFirestoreItem(doc)
                }
                newItems += parsed
            }
            dao.insertAll(newItems)
        }
    }
    private fun parseFirestoreItem(doc: DocumentSnapshot): ItemEntity? {
        return try {
            val data = doc.data ?: return null
            val lastUpdated = (data["lastFBUpdate"] as? com.google.firebase.Timestamp)
                ?.toDate()?.time ?: 0L

            ItemEntity(
                Code = data["Code"] as? String ?: "",
                DiscPercent = (data["DiscPercent"] as? Number)?.toDouble() ?: 0.0,
                MRP = (data["MRP"] as? Number)?.toDouble() ?: 0.0,
                MasterCode = data["MasterCode"]?.toString() ?: "",
                Name = data["Name"] as? String ?: "",
                PRICE3 = (data["PRICE3"] as? Number)?.toDouble() ?: 0.0,
                Unit = data["Unit"] as? String ?: "",
                imageExt = data["imageExt"] as? String ?: "",
                imageH = (data["imageH"] as? Number)?.toInt() ?: 0,
                imageW = (data["imageW"] as? Number)?.toInt() ?: 0,
                imageYes = data["imageYes"] as? Boolean ?: false,
                // Read TaxPercent when parsing individual items
                TaxPercent = (data["TaxPercent"] as? Number)?.toDouble() ?: 0.0,
                lastFBUpdate = lastUpdated
            )
        } catch (e: Exception) {
            Log.e("Repository", "Error parsing document: ${doc.id}", e)
            null
        }
    }

    suspend fun getLastServerUpdateTimestamp(): Long {
        return try {
            val doc = firestore.collection("DB_Service")
                .document("serverSideData")
                .get().await()

            val isoString = doc.getString("latestImportFromServer")
            if (isoString != null) {
                val trimmed = isoString.takeWhile { it != '.' } + "." + isoString.substringAfter('.').take(3)
                val sdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS", java.util.Locale.getDefault())
                sdf.timeZone = java.util.TimeZone.getTimeZone("UTC")
                val date = sdf.parse(trimmed)
                date?.time ?: 0L
            } else {
                Log.e("Repository", "Field is null")
                0L
            }
        } catch (e: Exception) {
            Log.e("Repository", "Failed to fetch last server update", e)
            0L
        }
    }



    suspend fun getAll(): List<ItemEntity> = dao.getAllItems()

    suspend fun search(rawQuery: String): List<ItemEntity> {
        val queryNorm = rawQuery.normalised()
        val queryTokens = rawQuery
            .lowercase().trim()
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }

        if (queryTokens.isEmpty()) return dao.getAllItems()
        val all = dao.getAllItems()

        return all.map { item ->
            val haystackFull = "${item.Name} ${item.Code} ${item.MasterCode}".lowercase()
            val haystackTokens = haystackFull.split(Regex("\\s+"))
            val haystackNorm = haystackFull.normalised()

            var score = 0

            // 1️⃣ Exact token match → +3
            score += queryTokens.count { qt -> haystackTokens.any { it == qt } } * 3

            // 2️⃣ Partial token match → +2
            score += queryTokens.count { qt -> haystackTokens.any { it.contains(qt) && it != qt } } * 2

            // 3️⃣ Whole query collapsed match → +4
            if (haystackNorm.contains(queryNorm)) score += 4

            // 4️⃣ Levenshtein one-edit away match → +2
            if (levenshtein(queryNorm, haystackNorm) == 1) score += 2

            if (debugLog) Log.d("SEARCH", "${item.Name} → score $score")
            item to score
        }
            .filter { it.second > 0 }
            .sortedByDescending { it.second }
            .map { it.first }
    }

    private fun String.normalised(): String =
        lowercase().replace(Regex("[^a-z0-9]"), "") // remove non-alphanum

    private fun levenshtein(a: String, b: String): Int {
        if (a == b) return 0
        if (kotlin.math.abs(a.length - b.length) > 1) return 2

        var i = 0; var edits = 0; var j = 0
        while (i < a.length && j < b.length) {
            if (a[i] == b[j]) {
                i++; j++
            } else {
                edits++
                if (edits > 1) return edits
                when {
                    a.length > b.length -> i++
                    a.length < b.length -> j++
                    else -> { i++; j++ }
                }
            }
        }
        if (i < a.length || j < b.length) edits++
        return edits
    }
}
