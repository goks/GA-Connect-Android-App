package com.example.pricelist.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.pricelist.data.AppDatabase
import com.example.pricelist.data.Repository

class ItemViewModelFactory(context: Context) : ViewModelProvider.Factory {
    private val repo = Repository(AppDatabase.getInstance(context).itemDao())

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ItemViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ItemViewModel(repo) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
