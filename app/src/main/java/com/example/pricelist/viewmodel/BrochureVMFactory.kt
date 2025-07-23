package com.example.pricelist.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

class BrochureVMFactory : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(c: Class<T>): T = BrochureViewModel() as T
}
