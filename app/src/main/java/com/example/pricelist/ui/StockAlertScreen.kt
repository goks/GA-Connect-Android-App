// app/src/main/java/com/example/pricelist/ui/StockAlertsScreen.kt
package com.example.pricelist.ui

import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.pricelist.util.NotificationPermissionUtil
import com.example.pricelist.util.StockAlertStore
import com.example.pricelist.util.StockChangeChecker
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StockAlertsScreen(onBack: (String?) -> Unit) {
    val context = LocalContext.current
    val stockChecker = remember { StockChangeChecker(context) }
    var alerts by remember { mutableStateOf(StockAlertStore.getAlerts(context).filter { it.delta > 0 }) }
    val coroutineScope = rememberCoroutineScope()

    val grouped = alerts.sortedByDescending { it.updatedAt }
        .groupBy { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(it.updatedAt)) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Stock Alerts") },
                navigationIcon = {
                    IconButton(onClick = { onBack(null) }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                    }
                }
            )
        },
        bottomBar = {
            Row(
                Modifier.fillMaxWidth().padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Button(onClick = {
                    stockChecker.checkForNewStock {
                        coroutineScope.launch {
                            delay(300) // short delay to allow alerts to be written
                            alerts = StockAlertStore.getAlerts(context).filter { it.delta > 0 }
                        }
                    }
                }) {
                    Text("Fetch Stock")
                }
                Button(onClick = {
                    StockAlertStore.clearAlerts(context)
                    alerts = emptyList()
                    Toast.makeText(context, "History deleted", Toast.LENGTH_SHORT).show()
                }) {
                    Text("Delete History")
                }
            }
        }
    ) { pad ->
        if (alerts.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No stock changes found.", style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            LazyColumn(Modifier.padding(pad).fillMaxSize()) {
                grouped.forEach { (date, items) ->
                    item {
                        Text(
                            text = date,
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp)
                        )
                    }
                    items(items) { alert ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                .clickable {
                                    Log.d("StockAlertsScreen", "Alert clicked: ${alert.message} masterCode: ${alert.masterCode}")
                                    onBack(alert.masterCode)
                                },
                            shape = MaterialTheme.shapes.medium,
                            elevation = CardDefaults.cardElevation(1.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        alert.message.split(" ").dropLast(3).joinToString(" "), // Full item name from message
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 2
                                    )
                                    Text(
                                        "${alert.delta} ${alert.unit} added",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1
                                    )
                                }
                                Text(
                                    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(alert.updatedAt)),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.outline,
                                    modifier = Modifier.padding(start = 8.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}