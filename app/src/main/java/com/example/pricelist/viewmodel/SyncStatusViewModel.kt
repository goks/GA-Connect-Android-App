package com.example.pricelist.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pricelist.data.SyncRun
import com.example.pricelist.data.SyncStatusParser
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SyncStatusUiState(
    val latestMap: Map<String, SyncRun> = emptyMap(),
    val historyList: List<SyncRun> = emptyList(),
    val selectedKind: String = "price_list",
    val isLoading: Boolean = true,
    val isOffline: Boolean = false,
    val permissionDenied: Boolean = false,
    val errorMessage: String? = null,
    val nowMillis: Long = System.currentTimeMillis()
)

class SyncStatusViewModel : ViewModel() {

    private val db by lazy { FirebaseFirestore.getInstance() }

    private val _uiState = MutableStateFlow(SyncStatusUiState())
    val uiState: StateFlow<SyncStatusUiState> = _uiState.asStateFlow()

    private var statusListener: ListenerRegistration? = null
    private var historyListener: ListenerRegistration? = null
    private var isListening = false

    init {
        startTicker()
        startListening()
    }

    fun setSelectedKind(kind: String) {
        _uiState.value = _uiState.value.copy(selectedKind = kind)
    }

    fun startListening() {
        if (isListening) return
        isListening = true

        _uiState.value = _uiState.value.copy(isLoading = true, permissionDenied = false, errorMessage = null)

        // 1. Live latest status listener (DB_Service/sync_status)
        statusListener = db.collection("DB_Service").document("sync_status")
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    if (err.message?.contains("PERMISSION_DENIED", true) == true) {
                        _uiState.value = _uiState.value.copy(
                            permissionDenied = true,
                            isLoading = false,
                            errorMessage = "Sign-in / permissions required to view sync status."
                        )
                    } else {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            errorMessage = err.message
                        )
                    }
                    return@addSnapshotListener
                }

                val isFromCache = snap?.metadata?.isFromCache == true
                val data = snap?.data
                val parsedLatest = SyncStatusParser.parseLatestStatusDoc(data)

                _uiState.value = _uiState.value.copy(
                    latestMap = parsedLatest,
                    isLoading = false,
                    isOffline = isFromCache,
                    permissionDenied = false
                )
            }

        // 2. Index-free History Listener (sync_runs ordered by startedAt DESC limit 40)
        historyListener = db.collection("sync_runs")
            .orderBy("startedAt", Query.Direction.DESCENDING)
            .limit(40)
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    if (err.message?.contains("PERMISSION_DENIED", true) == true) {
                        _uiState.value = _uiState.value.copy(permissionDenied = true)
                    }
                    return@addSnapshotListener
                }

                val isFromCache = snap?.metadata?.isFromCache == true
                val docs = snap?.documents.orEmpty()
                val runs = docs.map { doc ->
                    SyncStatusParser.parseRunFromMap(doc.data.orEmpty(), fallbackRunId = doc.id)
                }

                _uiState.value = _uiState.value.copy(
                    historyList = runs,
                    isOffline = isFromCache
                )
            }
    }

    fun stopListening() {
        statusListener?.remove()
        historyListener?.remove()
        statusListener = null
        historyListener = null
        isListening = false
    }

    private fun startTicker() {
        viewModelScope.launch {
            while (true) {
                delay(1000L)
                _uiState.value = _uiState.value.copy(nowMillis = System.currentTimeMillis())
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopListening()
    }
}
