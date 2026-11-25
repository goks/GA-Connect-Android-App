package com.example.pricelist.ui

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.pricelist.data.Brochure
import com.example.pricelist.viewmodel.BrochureViewModel
import com.example.pricelist.viewmodel.BrochureVMFactory
import kotlinx.coroutines.flow.collectLatest
import java.io.File
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrochureScreen(onBack: () -> Unit) {

    val ctx = LocalContext.current
    val vm: BrochureViewModel = viewModel(factory = BrochureVMFactory())
    val list by vm.list.collectAsState()
    val progress by vm.downloadProgress.collectAsState()

    // one‑shot open
    LaunchedEffect(Unit) {
        vm.openDoc.collectLatest { doc ->
            val view = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(doc.uri, doc.contentType)
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
                    items(list, key = { it.id }) { b ->
                        val localFile = remember(b.file) { File(ctx.filesDir, "brochures/${b.file}") }
                        val isDownloaded = remember(b.file) { localFile.exists() && localFile.length() > 0 }

                        ListItem(
                            headlineContent = {
                                Text(b.name, fontWeight = FontWeight.SemiBold)
                            },
                            supportingContent = { BrochureSummary(b, isDownloaded, if (isDownloaded) localFile.length() else 0L) },
                            leadingContent  = {
                                if (progress[b.id] != null) {
                                    CircularProgressIndicator(
                                        progress = { progress[b.id]!! },
                                        modifier = Modifier.size(24.dp),
                                        strokeWidth = 3.dp
                                    )
                                } else if (isDownloaded) {
                                    Icon(
                                        Icons.Default.CheckCircle,
                                        contentDescription = "Downloaded",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                } else {
                                    Icon(Icons.Default.CloudDownload, null)
                                }
                            },
                            trailingContent = {
                                IconButton(
                                    onClick = {
                                        val shareText = "Brochure: ${b.name}\n${b.description}${if (b.brochureDate.isNotBlank()) "\nDate: ${b.brochureDate}" else ""}"
                                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(Intent.EXTRA_SUBJECT, b.name)
                                            putExtra(Intent.EXTRA_TEXT, shareText)
                                        }
                                        ctx.startActivity(Intent.createChooser(shareIntent, "Share Brochure"))
                                    }
                                ) {
                                    Icon(Icons.Default.Share, contentDescription = "Share Brochure")
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { vm.downloadAndOpen(ctx, b) }
                        )
                        HorizontalDivider(thickness = 0.5.dp)
                    }
                }
            }
        }
    }
}

@Composable
private fun BrochureSummary(brochure: Brochure, isDownloaded: Boolean, fileSizeBytes: Long) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        if (brochure.description.isNotBlank()) {
            Text(brochure.description, style = MaterialTheme.typography.bodyMedium)
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (brochure.brochureDate.isNotBlank()) {
                Text("Date: ${brochure.brochureDate}", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            }
            Text(
                if (brochure.contentType.startsWith("image/")) "Image" else "PDF",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
            if (isDownloaded && fileSizeBytes > 0) {
                val sizeMb = fileSizeBytes / (1024f * 1024f)
                Text(
                    "Cached (${String.format(Locale.getDefault(), "%.1f", sizeMb)} MB)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 10.sp
                )
            }
        }
    }
}
