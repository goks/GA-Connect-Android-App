package com.example.pricelist.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.OfflineBolt
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.pricelist.data.DerivedSyncState
import com.example.pricelist.data.SyncRun
import com.example.pricelist.viewmodel.SyncStatusViewModel
import com.google.firebase.Timestamp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@Composable
fun SyncStatusScreen(
    modifier: Modifier = Modifier,
    vm: SyncStatusViewModel = viewModel()
) {
    val state by vm.uiState.collectAsStateWithLifecycle()

    DisposableEffect(Unit) {
        vm.startListening()
        onDispose {
            vm.stopListening()
        }
    }

    // Default operations
    val defaultKinds = listOf("price_list", "sql_backup", "attendance")
    val detectedKinds = (state.latestMap.keys + state.historyList.map { it.kind }).distinct()
    val allKinds = (defaultKinds + detectedKinds).distinct()

    val currentRun = state.latestMap[state.selectedKind]
    val selectedHistory = state.historyList
        .filter { it.kind.equals(state.selectedKind, ignoreCase = true) }
        .take(10)

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Top Offline / Permission Banner
        if (state.permissionDenied) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        state.errorMessage ?: "Sign-in / permissions required to view sync status.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        } else if (state.isOffline) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.OfflineBolt, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color.Gray)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "Offline — showing cached status",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.Gray
                    )
                }
            }
        }

        // 1. Operation Selector Pills
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(allKinds, key = { it }) { kind ->
                val runForKind = state.latestMap[kind]
                val label = runForKind?.getEffectiveKindLabel() ?: when (kind) {
                    "price_list" -> "Price List"
                    "sql_backup" -> "SQL Backups"
                    "attendance" -> "Attendance"
                    else -> kind.replace("_", " ").replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }
                }

                FilterChip(
                    selected = state.selectedKind.equals(kind, ignoreCase = true),
                    onClick = { vm.setSelectedKind(kind) },
                    label = { Text(label, fontWeight = FontWeight.SemiBold) }
                )
            }
        }

        if (state.isLoading && currentRun == null && selectedHistory.isEmpty()) {
            Box(modifier = Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            // 2. Status Card for Selected Operation
            StatusCard(
                run = currentRun,
                nowMillis = state.nowMillis
            )

            // 3. Recent Runs History
            Text(
                "Recent Runs History",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 4.dp)
            )

            if (selectedHistory.isEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Text(
                        "No history runs recorded for this operation.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(selectedHistory, key = { it.runId.ifBlank { "${it.startedAt?.toDate()?.time}_${it.kind}" } }) { historyRun ->
                        HistoryRunRow(run = historyRun, nowMillis = state.nowMillis)
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusCard(
    run: SyncRun?,
    nowMillis: Long
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(3.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (run == null || run.state.isBlank()) {
                Text(
                    "No runs yet — this operation hasn't been reported",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                val derivedState = run.getDerivedState(nowMillis)
                val isRunning = derivedState == DerivedSyncState.RUNNING

                val infiniteTransition = rememberInfiniteTransition(label = "pulse")
                val pulseAlpha by infiniteTransition.animateFloat(
                    initialValue = 0.5f,
                    targetValue = 1.0f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(800),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "alpha"
                )

                // Top Header Row: Stage/Label + Derived Badge
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                        Text(
                            text = run.getEffectiveKindLabel(),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        val stageText = buildString {
                            if (run.stageIndex != null && run.stageTotal != null && run.stageTotal > 0) {
                                append("${run.stageIndex}/${run.stageTotal} · ")
                            }
                            append(run.stage.orEmpty().ifBlank { "Processing" })
                        }
                        Text(
                            text = stageText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Derived State Badge with Pulsing on Running
                    Surface(
                        color = parseHexColor(derivedState.colorHex),
                        shape = RoundedCornerShape(12.dp),
                        modifier = if (isRunning) Modifier.graphicsLayer(alpha = pulseAlpha) else Modifier
                    ) {
                        Text(
                            text = derivedState.label,
                            color = Color.White,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }

                // Trigger & Duration Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${run.getFormattedTrigger()} · ${run.getFormattedDuration(nowMillis)}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    run.startedAt?.let {
                        Text(
                            text = formatTimestamp(it),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Progress Bar (if total > 0)
                if (run.progress != null && run.progress.total > 0) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        LinearProgressIndicator(
                            progress = { (run.progress.done.toFloat() / run.progress.total.toFloat()).coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth().height(6.dp),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "${run.progress.done} / ${run.progress.total}",
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.align(Alignment.End),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Counter Tiles Row
                val counterPairs = run.getOrderedCounterPairs()
                if (counterPairs.isNotEmpty()) {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(counterPairs, key = { it.first }) { (key, count) ->
                            Surface(
                                color = MaterialTheme.colorScheme.surface,
                                shape = RoundedCornerShape(8.dp),
                                shadowElevation = 1.dp
                            ) {
                                Column(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = "$count",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = run.getCounterLabel(key),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontSize = 10.sp,
                                        color = Color.Gray
                                    )
                                }
                            }
                        }
                    }
                }

                // Error Box with Copy Button
                if (!run.error.isNullOrBlank()) {
                    val context = LocalContext.current
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = run.error,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    val clip = ClipData.newPlainText("Sync Error Log", run.error)
                                    clipboard.setPrimaryClip(clip)
                                    Toast.makeText(context, "Error log copied to clipboard", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "Copy Error Log", modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }

                // Footer Info
                val footerInfo = listOfNotNull(
                    run.host.takeIf { it.isNotBlank() },
                    run.database.takeIf { it.isNotBlank() },
                    run.appVersion.takeIf { it.isNotBlank() }?.let { "v$it" }
                ).joinToString(" · ")

                if (footerInfo.isNotBlank()) {
                    HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f))
                    Text(
                        text = footerInfo,
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun HistoryRunRow(
    run: SyncRun,
    nowMillis: Long
) {
    var expanded by remember { mutableStateOf(false) }
    val derivedState = run.getDerivedState(nowMillis)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded }
            .animateContentSize(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(parseHexColor(derivedState.colorHex), CircleShape)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = derivedState.label,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "(${run.getFormattedTrigger()})",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.Gray
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${formatTimestamp(run.startedAt)} · ${run.getFormattedDuration(nowMillis)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.Gray
                    )
                    Icon(
                        if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier.padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f))
                    if (!run.stage.isNullOrBlank()) {
                        Text(
                            text = "Stage: ${run.stage}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    val counterPairs = run.getOrderedCounterPairs()
                    if (counterPairs.isNotEmpty()) {
                        val counterText = counterPairs.joinToString(" · ") { (k, v) -> "$v ${run.getCounterLabel(k)}" }
                        Text(
                            text = counterText,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    if (!run.error.isNullOrBlank()) {
                        Text(
                            text = "Error: ${run.error}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    val meta = listOfNotNull(
                        run.host.takeIf { it.isNotBlank() }?.let { "Host: $it" },
                        run.database.takeIf { it.isNotBlank() }?.let { "DB: $it" },
                        run.appVersion.takeIf { it.isNotBlank() }?.let { "Version: v$it" }
                    ).joinToString(" | ")

                    if (meta.isNotBlank()) {
                        Text(
                            text = meta,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.Gray,
                            fontSize = 10.sp
                        )
                    }
                }
            }
        }
    }
}

private fun parseHexColor(hex: String): Color {
    return try {
        val clean = hex.removePrefix("#")
        val colorInt = clean.toLong(16).toInt() or 0xFF000000.toInt()
        Color(colorInt)
    } catch (e: Exception) {
        Color(0xFF5F6B6D)
    }
}

private fun formatTimestamp(timestamp: Timestamp?): String {
    if (timestamp == null) return "-"
    val sdf = SimpleDateFormat("dd-MM-yyyy HH:mm", Locale.getDefault())
    sdf.timeZone = TimeZone.getDefault()
    return sdf.format(timestamp.toDate())
}
