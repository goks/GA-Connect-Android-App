package com.example.pricelist.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.pricelist.viewmodel.BrochureViewModel
import com.example.pricelist.viewmodel.BrochureVMFactory
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrochureScreen(onBack: () -> Unit) {

    val ctx     = LocalContext.current
    val vm: BrochureViewModel = viewModel(factory = BrochureVMFactory())
    val list   by vm.list.collectAsState()
    val progress by vm.downloadProgress.collectAsState()

    // one‑shot open
    LaunchedEffect(Unit) {
        vm.openDoc.collectLatest { uri ->
            val view = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/pdf")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            ctx.startActivity(view)
        }
    }

    LaunchedEffect(Unit) { vm.refresh() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Brochures") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                    }
                }
            )
        }
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            if (list.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn {
                    items(  list, key = { it.id }) { b ->
                        ListItem(
                            headlineContent = { Text(b.name) },
                            leadingContent  = {
                                if (progress[b.id] != null) {
                                    CircularProgressIndicator(
                                        progress = { progress[b.id]!! },
                                        modifier = Modifier.size(24.dp),
                                        strokeWidth = 3.dp
                                    )
                                } else {
                                    Icon(Icons.Default.CloudDownload, null)
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { vm.downloadAndOpen(ctx, b) }
                        )
                        Divider()
                    }
                }
            }
        }
    }
}
