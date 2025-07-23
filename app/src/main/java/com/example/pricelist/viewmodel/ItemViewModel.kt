package com.example.pricelist.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pricelist.data.ItemEntity
import com.example.pricelist.data.Repository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

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
    fun syncNow(context: Context, onComplete: () -> Unit) {
        viewModelScope.launch {
            try {
                repo.sync(context)
                _itemsFlow.value = repo.getAll()
            } finally {
                onComplete()
            }
        }
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




}
