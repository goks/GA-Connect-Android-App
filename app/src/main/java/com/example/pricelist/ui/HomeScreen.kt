package com.example.pricelist.ui

import android.annotation.SuppressLint
import android.content.Context
import android.os.Environment
import android.util.Log
import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import coil.compose.rememberAsyncImagePainter
import com.example.pricelist.BuildConfig
import com.example.pricelist.ROUTE_ADMIN
import com.example.pricelist.data.ItemEntity
import com.example.pricelist.util.AnalyticsManager
import com.example.pricelist.util.AppUpdateInfo
import com.example.pricelist.util.AppUpdateManager
import com.example.pricelist.util.AppPrefs
import com.example.pricelist.util.NotificationPermissionUtil
import com.example.pricelist.util.StockAlertStore
import com.example.pricelist.util.StockChangeChecker
import com.example.pricelist.viewmodel.ItemViewModel
import com.example.pricelist.viewmodel.ItemViewModelFactory
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.ktx.storage
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.io.File
import java.util.Locale

const val ROUTE_BROCHURES = "brochures"
private const val ADMIN_EMAIL = "gokulav2@gmail.com"

@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(navController: NavController, highlightMasterCode: String? = null) {
    val context = LocalContext.current
    val viewModel: ItemViewModel = viewModel(factory = ItemViewModelFactory(context))
    val items by viewModel.itemsFlow.collectAsState()
    val query by viewModel.query.collectAsState()
    val user = FirebaseAuth.getInstance().currentUser
    var selectedItem by remember { mutableStateOf<ItemEntity?>(null) }
    var showMenu by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    val firstSyncDone = remember { mutableStateOf(AppPrefs.isFirstSyncDone(context)) }
    val lastSync = remember { AppPrefs.getLastSyncTime(context) }
    var syncing by remember { mutableStateOf(false) }
    var showUpdatePrompt by remember { mutableStateOf(false) }
    var newStockAvailable by remember { mutableStateOf(false) }
    var syncErrorMessage by remember { mutableStateOf<String?>(null) }
    var isAdmin by remember(user?.uid) {
        mutableStateOf(user?.email.equals(ADMIN_EMAIL, ignoreCase = true))
    }

    // Stock checker reference
    val stockChecker = remember { StockChangeChecker(context) }

    // 🔔 Get MasterCodes with stock alerts
    val alertMasterCodes = remember {
        StockAlertStore.getAlerts(context)
            .filter { it.delta > 0 }
            .map { it.masterCode }
    }
//    Log.d("HomeScreen", "Alert MasterCodes: $alertMasterCodes")

    // For scrolling/highlighting
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    // --- Scroll to Top Button State ---
    val showScrollToTop by remember {
        derivedStateOf { listState.firstVisibleItemIndex > 10 }
    }

    var hasAutoScrolled by remember { mutableStateOf(false) }
    // Remove animation state
    var redrawTrigger by remember { mutableStateOf(0) }
    // Auto-sync guard to avoid multiple automatic sync attempts
    var autoSyncStarted by remember { mutableStateOf(false) }

    // Auto-scroll logic without animation
    LaunchedEffect(highlightMasterCode, items) {
        if (highlightMasterCode != null && !hasAutoScrolled) {
            val idx = items.indexOfFirst { it.MasterCode == highlightMasterCode }
            if (idx >= 0) {
                coroutineScope.launch {
                    listState.animateScrollToItem(idx)
                    hasAutoScrolled = true
                }
            }
        }
        // Reset auto-scroll if highlightMasterCode changes
        if (highlightMasterCode == null) {
            hasAutoScrolled = false
        }
    }

    LaunchedEffect(Unit) {
        if (firstSyncDone.value) {
            val serverUpdatedAt = viewModel.getLastServerUpdateTimestamp()
            if (serverUpdatedAt > lastSync) {
                showUpdatePrompt = true
            }
        }
    }

    LaunchedEffect(user?.uid) {
        val uid = user?.uid ?: return@LaunchedEffect
        FirebaseFirestore.getInstance()
            .collection("users")
            .document(uid)
            .get()
            .addOnSuccessListener { snap ->
                isAdmin = snap.getBoolean("isAdmin") == true ||
                    snap.getString("role") == "administrator" ||
                    user.email.equals(ADMIN_EMAIL, ignoreCase = true)
            }
            .addOnFailureListener {
                isAdmin = user.email.equals(ADMIN_EMAIL, ignoreCase = true)
            }
    }

    // If first sync hasn't been done yet, start automatic sync once after login
    LaunchedEffect(items) {
        // If local DB is empty after login, attempt one auto-sync (covers cases where DB was wiped but AppPrefs says first sync done)
        if (!autoSyncStarted && items.isEmpty()) {
            autoSyncStarted = true
            syncing = true
            viewModel.syncNow(context) { success, errorMessage ->
                syncing = false
                if (success) {
                    // Persist first-sync and last sync time so the UI won't prompt again
                    AppPrefs.setFirstSyncDone(context, true)
                    AppPrefs.setLastSyncTime(context, System.currentTimeMillis())
                    firstSyncDone.value = true
                    syncErrorMessage = null
                } else {
                    // Sync failed — show a small toast so user knows
                    syncErrorMessage = errorMessage ?: "Auto-sync failed. Tap Sync from Firestore to retry."
                    Log.w("HomeScreen", "Auto-sync failed; items still empty. $errorMessage")
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("Welcome, ${user?.displayName ?: "Guest"}")
                },
                actions = {
                    IconButton(onClick = { showMenu = !showMenu }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Menu")
                    }

                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Download Brochures") },
                            onClick = {
                                AnalyticsManager.logButtonClick("menu_brochures")
                                showMenu = false
                                navController.navigate(ROUTE_BROCHURES)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("View Stock Alerts") },
                            onClick = {
                                AnalyticsManager.logButtonClick("menu_stock_alerts")
                                showMenu = false
                                navController.navigate("stock_alerts")
                            }
                        )
                        if (isAdmin) {
                            DropdownMenuItem(
                                text = { Text("Administrator") },
                                onClick = {
                                    AnalyticsManager.logButtonClick("menu_admin")
                                    showMenu = false
                                    navController.navigate(ROUTE_ADMIN)
                                }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("Settings") },
                            onClick = {
                                AnalyticsManager.logButtonClick("menu_settings")
                                showMenu = false
                                showSettings = true
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Sign out") },
                            onClick = {
                                AnalyticsManager.logButtonClick("menu_signout")
                                FirebaseAuth.getInstance().signOut()
                                navController.navigate("login") {
                                    popUpTo("home") { inclusive = true }
                                }
                            }
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(Modifier.fillMaxSize()) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().padding(innerPadding).padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    OutlinedTextField(
                        value = query,
                        onValueChange = {
                            viewModel.onSearchChanged(it)
                            AnalyticsManager.logSearch(it)
                        },
                        label = { Text("Search items…") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    syncErrorMessage?.let { msg ->
                        Spacer(Modifier.height(6.dp))
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(msg, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }

                item {
                    val isSearching = query.isNotBlank()

                    if (!firstSyncDone.value && !isSearching && items.isEmpty()) {
                        if (syncing) {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                            Spacer(Modifier.height(8.dp))
                        } else {
                            Button(
                                onClick = {
                                    syncing = true
                                    viewModel.syncNow(context) { success, errorMessage ->
                                        syncing = false
                                        if (success) {
                                            AppPrefs.setFirstSyncDone(context, true)
                                            AppPrefs.setLastSyncTime(context, System.currentTimeMillis())
                                            firstSyncDone.value = true
                                            syncErrorMessage = null
                                        } else {
                                            syncErrorMessage = errorMessage ?: "Sync failed. Check logs and retry."
                                            Log.w("HomeScreen", "Manual sync failed via button. $errorMessage")
                                        }
                                 }
                                 },
                                 enabled = !syncing,
                                 shape = RoundedCornerShape(8.dp),
                                 colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                             ) {
                                 Text("Sync from Firestore")
                             }
                            Spacer(Modifier.height(8.dp))
                        }
                    } else if (showUpdatePrompt || newStockAvailable) {
                        Column {
                            if (showUpdatePrompt) {
                                OutlinedButton(
                                    onClick = {
                                        syncing = true
                                        viewModel.syncNow(context) { success, errorMessage ->
                                            syncing = false
                                            if (success) {
                                                showUpdatePrompt = false
                                                syncErrorMessage = null
                                            } else {
                                                syncErrorMessage = errorMessage ?: "Sync failed. Check logs and retry."
                                            }

                                            // Also check for stock changes after sync
                                            if (success && NotificationPermissionUtil.checkNotificationPermission(context)) {
                                                stockChecker.checkForNewStock { hasNewStock ->
                                                    newStockAvailable = false
                                                }
                                            }
                                        }
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("New update available – Sync now?")
                                }
                            }

                            if (newStockAvailable && !showUpdatePrompt) {
                                Spacer(Modifier.height(8.dp))
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(8.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.primaryContainer
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            "New stock items have been added",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                items(items) { item ->
                    val highlight = highlightMasterCode != null && item.MasterCode == highlightMasterCode
                    // Remove animation parameter and use redraw trigger to force recomposition
                    ItemCard(item, alertMasterCodes.contains(item.MasterCode), highlight, redrawTrigger) { selectedItem = it }
                }

                if (items.isEmpty() && query.isNotBlank()) {
                    item {
                        Text(
                            text = "No items found.",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(top = 24.dp)
                        )
                    }
                }
            }
            // --- Floating Scroll to Top Button ---
            if (showScrollToTop) {
                FloatingActionButton(
                    onClick = {
                        coroutineScope.launch {
                            listState.animateScrollToItem(0)
                            // Trigger card redraw by incrementing the counter
                            redrawTrigger++
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(24.dp),
                    containerColor = MaterialTheme.colorScheme.primary
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowUpward,
                        contentDescription = "Scroll to Top",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }
    }

    if (showSettings) {
        SettingsDialog(onDismiss = { showSettings = false })
    }

    selectedItem?.let { item ->
        val imageFile = File(context.filesDir, "images/${item.MasterCode}${item.imageExt}")
        if (item.imageYes && imageFile.exists()) {
            ImageZoomDialog(
                file = imageFile,
                itemName = item.Name,
                imageW = item.imageW,
                imageH = item.imageH
            ) {
                selectedItem = null
            }
        }
    }
}

@Composable
private fun SettingsDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var checking by remember { mutableStateOf(false) }
    var downloading by remember { mutableStateOf(false) }
    var updateInfo by remember { mutableStateOf<AppUpdateInfo?>(null) }
    var statusText by remember { mutableStateOf<String?>(null) }
    val busy = checking || downloading

    AlertDialog(
        onDismissRequest = {
            if (!busy) onDismiss()
        },
        confirmButton = {
            TextButton(onClick = onDismiss, enabled = !busy) {
                Text("Close")
            }
        },
        title = { Text("Settings") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                    style = MaterialTheme.typography.bodyMedium
                )

                Button(
                    onClick = {
                        checking = true
                        updateInfo = null
                        statusText = "Checking GitHub releases..."
                        coroutineScope.launch {
                            try {
                                val update = AppUpdateManager.checkForUpdate(BuildConfig.VERSION_NAME)
                                updateInfo = update
                                statusText = if (update == null) {
                                    "You are already on the latest version."
                                } else {
                                    "Version ${update.versionName} is available."
                                }
                            } catch (e: Exception) {
                                Log.e("SettingsDialog", "Update check failed", e)
                                statusText = "Update check failed: ${e.message ?: "Unknown error"}"
                            } finally {
                                checking = false
                            }
                        }
                    },
                    enabled = !busy,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (checking) "Checking..." else "Check for new updates")
                }

                updateInfo?.let { update ->
                    OutlinedButton(
                        onClick = {
                            if (!AppUpdateManager.canInstallPackages(context)) {
                                statusText = "Allow installs from this app, then return and tap Download and install again."
                                AppUpdateManager.openInstallPermissionSettings(context)
                                return@OutlinedButton
                            }

                            downloading = true
                            statusText = "Downloading version ${update.versionName}..."
                            coroutineScope.launch {
                                try {
                                    val apk = AppUpdateManager.downloadApk(context, update)
                                    statusText = "Download complete. Confirm installation when prompted."
                                    AppUpdateManager.installApk(context, apk)
                                } catch (e: Exception) {
                                    Log.e("SettingsDialog", "Update download/install failed", e)
                                    statusText = "Update failed: ${e.message ?: "Unknown error"}"
                                } finally {
                                    downloading = false
                                }
                            }
                        },
                        enabled = !busy,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (downloading) "Downloading..." else "Download and install")
                    }
                }

                statusText?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (it.contains("failed", ignoreCase = true)) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }
        }
    )
}
// -----------------------------
// 🔻 Reusable Composables
// -----------------------------
@Composable
fun ItemCard(item: ItemEntity, hasStockAlert: Boolean = false, highlight: Boolean = false, redrawTrigger: Int = 0, onImageClick: (ItemEntity) -> Unit) {
    val context = LocalContext.current
    val localFile = remember(item.MasterCode, item.imageExt) {
        File(context.filesDir, "images/${item.MasterCode}${item.imageExt}")
    }
    val imagePath = remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }

    LaunchedEffect(item.MasterCode, item.imageExt, item.imageYes) {
        imagePath.value = null
        isLoading = false

        if (item.imageYes) {
            if (localFile.exists()) {
                imagePath.value = localFile.absolutePath
            } else {
                isLoading = true
                try {
                    val storageRef = Firebase.storage.reference.child("${item.MasterCode}${item.imageExt}")
                    storageRef.getFile(localFile).await()
                    imagePath.value = localFile.absolutePath
                } catch (e: Exception) {
                    Log.e("ImageLoad", "Failed to download image for ${item.Code}", e)
                } finally {
                    isLoading = false
                }
            }
        }
    }

    // Determine the target color based on state and animation
    val targetColor = if (highlight) Color(0xFFB3E5FC) else Color.Unspecified

    val animatedColor by animateColorAsState(
        targetValue = targetColor,
        animationSpec = tween(durationMillis = 1000),
        label = "cardHighlightAnimation"
    )

    var showTaxBreakdown by remember { mutableStateOf(false) }
    var showUnitPriceDialog by remember { mutableStateOf(false) }
    val taxPercent = item.TaxPercent
    val salePrice = item.PRICE3
    val taxPrice = salePrice * taxPercent / 100.0
    val totalPrice = salePrice + taxPrice
    val salePriceFormatted = String.format(Locale.getDefault(), "%.2f", salePrice)
    val taxPriceFormatted = String.format(Locale.getDefault(), "%.2f", taxPrice)
    val totalPriceFormatted = String.format(Locale.getDefault(), "%.2f", totalPrice)

    // Use redrawTrigger to avoid it being reported as unused; no-op side effect
    LaunchedEffect(redrawTrigger) { /* no-op: used to trigger recomposition */ }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(4.dp),
        colors = CardDefaults.cardColors(containerColor = animatedColor)
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (item.imageYes) {
                Box(
                    modifier = Modifier
                        .padding(end = 12.dp)
                        .size(72.dp)
                        .clickable(enabled = imagePath.value != null) {
                            onImageClick(item)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (imagePath.value != null) {
                        AsyncImage(
                            model = File(imagePath.value!!),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        if (isLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(
                                imageVector = Icons.Default.Image,
                                contentDescription = "Image loading",
                                tint = Color.Gray,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = item.Name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    if (hasStockAlert) {
                        Icon(
                            imageVector = Icons.Default.Notifications,
                            contentDescription = "Stock update",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .size(20.dp)
                                .padding(start = 6.dp)
                        )
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showTaxBreakdown = !showTaxBreakdown },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Sale ₹$salePriceFormatted",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "Unit: ${item.Unit}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (showTaxBreakdown) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer,
                        tonalElevation = 2.dp,
                        shadowElevation = 1.dp
                    ) {
                        Text(
                            text = "GST ${taxPercent.toInt()}%",
                            color = if (showTaxBreakdown) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onPrimaryContainer,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                }

                if (showTaxBreakdown) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            PriceBreakdownText("Sale", "₹$salePriceFormatted", Modifier.weight(1f))
                            PriceBreakdownText("GST", "₹$taxPriceFormatted", Modifier.weight(1f))
                            PriceBreakdownText(
                                label = "Total",
                                value = "₹$totalPriceFormatted",
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { showUnitPriceDialog = true },
                                emphasize = true,
                                helper = "Calculate"
                            )
                        }
                    }
                }
                Text(text = "Code: ${item.Code}", style = MaterialTheme.typography.bodySmall, color = Color.Gray)

                if (item.DiscPercent != 0.0) {
                    DiscountBadge(item.DiscPercent)
                }
            }
        }
    }

    if (showUnitPriceDialog) {
        UnitPriceDialog(
            itemName = item.Name,
            totalPrice = totalPrice,
            onDismiss = { showUnitPriceDialog = false }
        )
    }
}

@Composable
private fun PriceBreakdownText(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    emphasize: Boolean = false,
    helper: String? = null
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = if (emphasize) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (emphasize) FontWeight.Bold else FontWeight.Medium
        )
        helper?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun UnitPriceDialog(
    itemName: String,
    totalPrice: Double,
    onDismiss: () -> Unit
) {
    var input by remember { mutableStateOf("3") }
    var operation by remember { mutableStateOf(UnitPriceOperation.Divide) }
    val factor = input.toDoubleOrNull()
    val result = when {
        factor == null || factor == 0.0 -> null
        operation == UnitPriceOperation.Divide -> totalPrice / factor
        else -> totalPrice * factor
    }
    val totalPriceFormatted = String.format(Locale.getDefault(), "%.2f", totalPrice)
    val resultFormatted = result?.let { String.format(Locale.getDefault(), "%.2f", it) }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done")
            }
        },
        title = { Text(itemName) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Tax incl. price: ₹$totalPriceFormatted",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = operation == UnitPriceOperation.Divide,
                        onClick = { operation = UnitPriceOperation.Divide },
                        label = { Text("Divide") }
                    )
                    FilterChip(
                        selected = operation == UnitPriceOperation.Multiply,
                        onClick = { operation = UnitPriceOperation.Multiply },
                        label = { Text("Multiply") }
                    )
                }

                OutlinedTextField(
                    value = input,
                    onValueChange = { value ->
                        input = value.filter { it.isDigit() || it == '.' }
                    },
                    label = { Text("Value") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = if (operation == UnitPriceOperation.Divide) "Unit price" else "Calculated price",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = resultFormatted?.let { "₹$it" } ?: "Enter a valid value",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
        }
    )
}

private enum class UnitPriceOperation {
    Divide,
    Multiply
}

@Composable
fun DiscountBadge(percent: Double) {
    Surface(
        shape = CircleShape,
        color = Color(0xFFE53935),
        shadowElevation = 4.dp,
        modifier = Modifier.padding(top = 6.dp)
    ) {
        Text(
            text = "DISCOUNT ${percent.toInt()}%",
            color = Color.White,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

@Composable
fun ImageZoomDialog(
    file: File,
    itemName: String,
    imageW: Int,
    imageH: Int,
    onDismiss: () -> Unit
) {
    var scale by remember { mutableStateOf(1f) }
    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }

    val aspectRatio = imageW.toFloat() / imageH.coerceAtLeast(1)

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        title = null,
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(aspectRatio)
                        .clipToBounds()
                        .graphicsLayer(
                            scaleX = scale,
                            scaleY = scale,
                            translationX = offsetX,
                            translationY = offsetY
                        )
                        .pointerInput(Unit) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                scale = (scale * zoom).coerceIn(1f, 5f)
                                offsetX += pan.x
                                offsetY += pan.y
                            }
                        }
                ) {
                    Image(
                        painter = rememberAsyncImagePainter(model = file),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                Text(
                    text = itemName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }
        }
    )
}

// 🔻 Firebase brochure listing and download
fun loadBrochures(onComplete: (List<String>) -> Unit) {
    FirebaseStorage.getInstance().reference.child("brochures/")
        .listAll()
        .addOnSuccessListener { list ->
            val names = list.items.map { it.name }
            onComplete(names)
        }
        .addOnFailureListener { onComplete(emptyList()) }
}

fun downloadPdf(filename: String, context: Context, onComplete: () -> Unit) {
    val storageRef = FirebaseStorage.getInstance().reference.child("brochures/$filename")
    val localFile = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), filename)

    storageRef.getFile(localFile)
        .addOnSuccessListener {
            Toast.makeText(context, "Downloaded to: ${localFile.path}", Toast.LENGTH_LONG).show()
            onComplete()
        }
        .addOnFailureListener {
            Toast.makeText(context, "Download failed: ${it.message}", Toast.LENGTH_LONG).show()
            onComplete()
        }
}
