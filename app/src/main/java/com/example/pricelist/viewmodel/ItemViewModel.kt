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
import com.example.pricelist.util.AppPrefs
import kotlinx.coroutines.ExperimentalCoroutinesApi

@OptIn(ExperimentalCoroutinesApi::class)
class ItemViewModel(private val repo: Repository) : ViewModel() {

    /* ---------- state flows ---------- */
    private val _query = MutableStateFlow("")
    val   query    : StateFlow<String>           = _query.asStateFlow()

    private val _refreshTrigger = MutableStateFlow(0)

    // Derived flow that combines search query and data updates
    val itemsFlow: StateFlow<List<ItemEntity>> = combine(_query, _refreshTrigger) { q, _ -> q }
        .flatMapLatest { q ->
            flow {
                emit(if (q.isBlank()) repo.getAll() else repo.search(q))
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /* ---------------------------------------------------- */
    /* 🔄  Manual sync (Firestore ➜ Room)                    */
    /* ---------------------------------------------------- */
    fun syncNow(
        context: Context,
        isAdmin: Boolean,
        onComplete: (success: Boolean, errorMessage: String?) -> Unit
    ) {
        viewModelScope.launch {
            var ok = false
            var errorMsg: String? = null

            try {
                // Phase 1: Metadata and Changed Items Sync
                withTimeout(120_000L) {
                    repo.sync(context, isAdmin)
                    // Trigger a refresh of the visible items while preserving the search query
                    _refreshTrigger.value++
                    ok = true

                    // ✅ Save sync flag
                    AppPrefs.setFirstSyncDone(context, true)
                }
                
                // Phase 2: Background Enrichment for Admins
                if (ok) {
                    launch {
                        try {
                            repo.enrichSensitiveDataInBackground(isAdmin)
                            // Trigger another refresh to show enriched data (prices etc)
                            _refreshTrigger.value++
                        } catch (e: Exception) {
                            Log.e("SyncNow", "Background enrichment failed", e)
                        }
                    }
                }

            } catch (t: TimeoutCancellationException) {
                Log.e("SyncNow", "Sync timed-out (120s)")
                errorMsg = "Sync timed out"
            } catch (e: Exception) {
                Log.e("SyncNow", "Sync error", e)
                errorMsg = e.message ?: "Unknown sync error"
            } finally {
                onComplete(ok, errorMsg)
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
        _query.value = text
    }

    fun checkIfUpdateAvailable(context: Context, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            try {
                val localLastSync = AppPrefs.getLastSyncTime(context)

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