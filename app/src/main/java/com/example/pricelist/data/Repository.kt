package com.example.pricelist.data

import android.content.Context
import android.util.Log
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
        Log.d("Repository", "Fetching from Firestore")
        val items = firestore
            .collection("items")
            .limit(500)
            .   get()
            .await()
            .documents
            .mapNotNull { doc ->
                try {
                    val data = doc.data ?: return@mapNotNull null
                    ItemEntity(
                        Code        = data["Code"] as? String ?: "",
                        DiscPercent = (data["DiscPercent"] as? Number)?.toDouble() ?: 0.0,
                        MRP         = (data["MRP"] as? Number)?.toDouble() ?: 0.0,
                        MasterCode  = data["MasterCode"]?.toString() ?: "",
                        Name        = data["Name"] as? String ?: "",
                        PRICE3      = (data["PRICE3"] as? Number)?.toDouble() ?: 0.0,
                        Unit        = data["Unit"] as? String ?: "",
                        imageExt    = data["imageExt"] as? String ?: "",
                        imageH      = (data["imageH"] as? Number)?.toInt() ?: 0,
                        imageW      = (data["imageW"] as? Number)?.toInt() ?: 0,
                        imageYes    = data["imageYes"] as? Boolean ?: false
                    )
                } catch (e: Exception) {
                    Log.e("Repository", "Error parsing document: ${doc.id}", e)
                    null
                }
            }
        Log.d("Repository", "Fetched ${items.size} items")

        dao.clearAll()
        dao.insertAll(items)

        val imageDir = File(context.filesDir, "images").apply { mkdirs() }
        // Images are fetched lazily per item card



    }

    fun getLastServerUpdateTimestamp(): Long {
        return System.currentTimeMillis() // Replace with Firestore field if you're storing that
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
