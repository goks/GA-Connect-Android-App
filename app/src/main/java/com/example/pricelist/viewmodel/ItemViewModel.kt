package com.example.pricelist.viewmodel

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pricelist.data.ItemEntity
import com.example.pricelist.data.Repository
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import androidx.core.content.edit

class ItemViewModel(private val repo: Repository) : ViewModel() {

    /* ---------- state flows ---------- */
    private val _itemsFlow = MutableStateFlow<List<ItemEntity>>(emptyList())
    val   itemsFlow : StateFlow<List<ItemEntity>> = _itemsFlow

    private val _query = MutableStateFlow("")
    val   query    : StateFlow<String>           = _query.asStateFlow()

    /* ---------- fuzzy search engine ---------- */


    /* ---------------------------------------------------- */
    /* 1️⃣  initial load (Room cache)                        */
    /* ---------------------------------------------------- */
    init {
        viewModelScope.launch {
            val local = repo.getAll()
            _itemsFlow.value = local
        }
    }

    /* ---------------------------------------------------- */
    /* 🔄  Manual sync (Firestore ➜ Room)                    */
    /* ---------------------------------------------------- */
    fun syncNow(
        context: Context,
        onComplete: (success: Boolean) -> Unit
    ) {
        viewModelScope.launch {
            val prefs = context.getSharedPreferences("sync_prefs", Context.MODE_PRIVATE)
            var ok = false

            try {
                withTimeout(15_000L) {
                    repo.sync(context)
                    _itemsFlow.value = repo.getAll()
                    ok = true

                    // ✅ Save sync flag and timestamp
                    prefs.edit {
                        putBoolean("hasSyncedOnce", true)
                            .putLong("lastSyncedTime", System.currentTimeMillis())
                    }
                }
            } catch (t: TimeoutCancellationException) {
                Log.e("SyncNow", "Sync timed‑out (15 s)")
            } catch (e: Exception) {
                Log.e("SyncNow", "Sync error", e)
            } finally {
                onComplete(ok)
            }
        }
    }
    // In ItemViewModel.kt
    suspend fun getLastServerUpdateTimestamp(): Long {
        return repo.getLastServerUpdateTimestamp()
    }



    /* ---------------------------------------------------- */
    /* 🔍  Search handler                                    */
    /* ---------------------------------------------------- */
    fun onSearchChanged(text: String) {
        viewModelScope.launch {
            _query.value = text
            _itemsFlow.value = repo.search(text)
        }
    }

    fun checkIfUpdateAvailable(context: Context, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            try {
                val localLastSync = context
                    .getSharedPreferences("sync_prefs", Context.MODE_PRIVATE)
                    .getLong("lastSyncedTime", 0L)

                val remoteLastUpdate = repo.getLastServerUpdateTimestamp()

                Log.d("UpdateCheck", "Local: $localLastSync → Remote: $remoteLastUpdate")

                onResult(remoteLastUpdate > localLastSync)
            } catch (e: Exception) {
                Log.e("UpdateCheck", "Failed to check updates", e)
                onResult(false) // fallback: assume no update
            }
        }
    }




}
