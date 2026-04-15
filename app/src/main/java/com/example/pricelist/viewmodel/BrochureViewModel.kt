package com.example.pricelist.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pricelist.data.Brochure
import com.example.pricelist.data.BrochureRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class BrochureDocument(
    val uri: Uri,
    val contentType: String
)

class BrochureViewModel(
    private val repo: BrochureRepository = BrochureRepository()
) : ViewModel() {

    private val _list = MutableStateFlow<List<Brochure>>(emptyList())
    val  list : StateFlow<List<Brochure>> = _list

    private val _downloadProgress = MutableStateFlow<Map<String,Float>>(emptyMap())
    val  downloadProgress : StateFlow<Map<String,Float>> = _downloadProgress

    private val _openDoc = MutableSharedFlow<BrochureDocument>()
    val  openDoc : SharedFlow<BrochureDocument> = _openDoc     // UI collects & opens via Intent

    /** load once when screen starts */
    fun refresh() = viewModelScope.launch {
        _list.value = repo.fetchBrochures()
    }

    /** download (if needed) then emit Uri */
    fun downloadAndOpen(ctx: Context, b: Brochure) = viewModelScope.launch {
        val uri = repo.getLocalFile(ctx, b) { pct ->
            _downloadProgress.update { it + (b.id to pct) }
        }
        _downloadProgress.update { it - b.id }   // remove progress entry
        _openDoc.emit(BrochureDocument(uri, b.contentType.ifBlank { "application/pdf" }))
    }
}
