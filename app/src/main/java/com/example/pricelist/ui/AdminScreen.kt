package com.example.pricelist.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Mouse
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.pricelist.data.Brochure
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.ktx.storage
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val ADMIN_EMAIL = "gokulav2@gmail.com"

private data class UserActivity(
    val id: String,
    val name: String,
    val email: String,
    val whitelisted: Boolean,
    val role: String,
    val lastLogin: Long,
    val last30Days: List<String>,
    val device: String,
    val androidVer: String,
    val appVersion: String,
    val dailyStats: List<DailyStats> = emptyList()
)

private data class DailyStats(
    val date: String,
    val searchCount: Long,
    val totalClicks: Long,
    val lastActive: Long,
    val latitude: Double,
    val longitude: Double,
    val appVersion: String,
    val buttonClicks: Map<String, Long> = emptyMap()
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val auth = FirebaseAuth.getInstance()
    val db = remember { FirebaseFirestore.getInstance() }
    val storage = remember { Firebase.storage.reference.child("brochures") }
    val dateFormat = remember { SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()) }

    var allowed by remember { mutableStateOf(auth.currentUser?.email.equals(ADMIN_EMAIL, ignoreCase = true)) }
    var loadingUsers by remember { mutableStateOf(true) }
    var users by remember { mutableStateOf<List<UserActivity>>(emptyList()) }
    var statusText by remember { mutableStateOf<String?>(null) }
    var selectedTab by remember { mutableIntStateOf(0) }
    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var selectedFileName by remember { mutableStateOf("") }
    var brochureName by remember { mutableStateOf("") }
    var brochureDescription by remember { mutableStateOf("") }
    var brochureDate by remember { mutableStateOf("") }
    var brochures by remember { mutableStateOf<List<Brochure>>(emptyList()) }
    var loadingBrochures by remember { mutableStateOf(false) }
    var editingBrochure by remember { mutableStateOf<Brochure?>(null) }
    var uploading by remember { mutableStateOf(false) }

    fun loadUsers() {
        loadingUsers = true
        scope.launch {
            try {
                val result = db.collection("users")
                    .orderBy("lastLogin", Query.Direction.DESCENDING)
                    .get()
                    .await()

                val userList = result.documents.map { doc ->
                    // Fetch daily stats for each user
                    val statsResult = db.collection("usage_logs")
                        .document(doc.id)
                        .collection("daily_stats")
                        .orderBy("last_active", Query.Direction.DESCENDING)
                        .limit(7) // Show last 7 days of activity
                        .get()
                        .await()

                    val dailyStats = statsResult.documents.map { sDoc ->
                        DailyStats(
                            date = sDoc.getString("date") ?: sDoc.id,
                            searchCount = sDoc.getLong("search_count") ?: 0L,
                            totalClicks = sDoc.getLong("total_clicks") ?: 0L,
                            lastActive = sDoc.getTimestamp("last_active")?.toDate()?.time ?: 0L,
                            latitude = sDoc.getDouble("latitude") ?: 0.0,
                            longitude = sDoc.getDouble("longitude") ?: 0.0,
                            appVersion = sDoc.getString("appVersion").orEmpty(),
                            buttonClicks = (sDoc.get("button_clicks") as? Map<*, *>)
                                ?.map { it.key.toString() to (it.value as? Long ?: 0L) }
                                ?.toMap().orEmpty()
                        )
                    }

                    UserActivity(
                        id = doc.id,
                        name = doc.getString("name").orEmpty(),
                        email = doc.getString("email").orEmpty(),
                        whitelisted = doc.getBoolean("whitelisted") == true,
                        role = doc.getString("role").orEmpty(),
                        lastLogin = doc.getLong("lastLogin") ?: 0L,
                        last30Days = (doc.get("last30Days") as? List<*>)
                            ?.mapNotNull { it as? String }
                            ?.sortedDescending()
                            .orEmpty(),
                        device = doc.getString("device").orEmpty(),
                        androidVer = doc.getString("androidVer").orEmpty(),
                        appVersion = doc.getString("appVersion").orEmpty(),
                        dailyStats = dailyStats
                    )
                }
                users = userList
            } catch (e: Exception) {
                statusText = "Could not load users: ${e.message}"
            } finally {
                loadingUsers = false
            }
        }
    }

    fun clearBrochureForm() {
        editingBrochure = null
        selectedUri = null
        selectedFileName = ""
        brochureName = ""
        brochureDescription = ""
        brochureDate = ""
    }

    fun loadBrochures() {
        loadingBrochures = true
        db.collection("brochures")
            .orderBy("name")
            .get()
            .addOnSuccessListener { result ->
                brochures = result.documents.mapNotNull { doc ->
                    doc.toObject(Brochure::class.java)?.copy(id = doc.id)
                }
                loadingBrochures = false
            }
            .addOnFailureListener {
                statusText = "Could not load brochures: ${it.message}"
                loadingBrochures = false
            }
    }

    fun editBrochure(brochure: Brochure) {
        editingBrochure = brochure
        selectedUri = null
        selectedFileName = ""
        brochureName = brochure.name
        brochureDescription = brochure.description
        brochureDate = brochure.brochureDate
        selectedTab = 1
    }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            selectedUri = uri
            selectedFileName = displayNameForUri(context, uri)
            if (brochureName.isBlank()) {
                brochureName = selectedFileName.substringBeforeLast('.', selectedFileName)
            }
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }
    }

    LaunchedEffect(Unit) {
        val user = auth.currentUser
        allowed = user?.email.equals(ADMIN_EMAIL, ignoreCase = true)
        if (allowed && user != null) {
            db.collection("users").document(user.uid).set(
                mapOf(
                    "email" to (user.email ?: ADMIN_EMAIL),
                    "name" to (user.displayName ?: "Administrator"),
                    "whitelisted" to true,
                    "role" to "administrator",
                    "isAdmin" to true
                ),
                SetOptions.merge()
            )
            loadUsers()
            loadBrochures()
        } else {
            loadingUsers = false
            loadingBrochures = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Administrator") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        if (!allowed) {
            Column(
                modifier = Modifier
                    .padding(padding)
                    .padding(16.dp)
                    .fillMaxSize()
            ) {
                Text("Administrator access is not enabled for this account.")
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Users") }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Brochures") }
                )
            }

            statusText?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (it.contains("failed", true) || it.contains("could not", true)) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            if (selectedTab == 0) {
                Column(Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("User activity", style = MaterialTheme.typography.titleMedium)
                        TextButton(onClick = { loadUsers() }) {
                            Text("Refresh")
                        }
                    }

                    if (loadingUsers) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(users, key = { it.id }) { user ->
                            UserActivityCard(user, dateFormat) { userId, isWhitelisted ->
                                scope.launch {
                                    try {
                                        db.collection("users").document(userId)
                                            .update("whitelisted", isWhitelisted)
                                            .await()
                                        users = users.map {
                                            if (it.id == userId) it.copy(whitelisted = isWhitelisted) else it
                                        }
                                        statusText = "User ${user.email} ${if (isWhitelisted) "whitelisted" else "restricted"}"
                                    } catch (e: Exception) {
                                        statusText = "Whitelist update failed: ${e.message}"
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Text("Curate brochures", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Upload a PDF or image. It will be saved to Firebase Storage and listed in Firestore for every user.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    item {
                        OutlinedTextField(
                            value = brochureName,
                            onValueChange = { brochureName = it },
                            label = { Text("Brochure title") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    item {
                        OutlinedTextField(
                            value = brochureDescription,
                            onValueChange = { brochureDescription = it },
                            label = { Text("Description") },
                            minLines = 2,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    item {
                        OutlinedTextField(
                            value = brochureDate,
                            onValueChange = { brochureDate = it },
                            label = { Text("Date") },
                            placeholder = { Text("Example: 2026-04-15") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    item {
                        Button(
                            onClick = { picker.launch(arrayOf("application/pdf", "image/*")) },
                            enabled = !uploading,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            val currentFile = editingBrochure?.file?.takeIf { it.isNotBlank() }
                            val label = when {
                                selectedFileName.isNotBlank() -> selectedFileName
                                currentFile != null -> "Replace attachment: $currentFile"
                                else -> "Choose PDF or image"
                            }
                            Text(label)
                        }
                    }

                    item {
                        Button(
                            onClick = {
                                val title = brochureName.trim()
                                val existing = editingBrochure
                                val pickedUri = selectedUri

                                if (title.isBlank()) {
                                    statusText = "Enter a brochure title."
                                    return@Button
                                }

                                if (existing == null && pickedUri == null) {
                                    statusText = "Choose a PDF or image."
                                    return@Button
                                }

                                uploading = true
                                statusText = if (existing == null) "Uploading brochure..." else "Saving brochure..."
                                scope.launch {
                                    try {
                                        var storageName = existing?.file.orEmpty()
                                        var contentType = existing?.contentType ?: "application/pdf"

                                        if (pickedUri != null) {
                                            val originalName = selectedFileName.ifBlank { "brochure" }
                                            val safeName = safeStorageName(originalName)
                                            val newStorageName = "${System.currentTimeMillis()}-$safeName"
                                            contentType = context.contentResolver.getType(pickedUri) ?: "application/octet-stream"

                                            storage.child(newStorageName).putFile(pickedUri).await()

                                            if (existing?.file?.isNotBlank() == true) {
                                                runCatching { storage.child(existing.file).delete().await() }
                                            }

                                            storageName = newStorageName
                                        }

                                        val payload = mapOf(
                                            "name" to title,
                                            "description" to brochureDescription.trim(),
                                            "brochureDate" to brochureDate.trim(),
                                            "file" to storageName,
                                            "contentType" to contentType,
                                            "uploadedBy" to (auth.currentUser?.email ?: ADMIN_EMAIL),
                                            "ts" to System.currentTimeMillis()
                                        )

                                        if (existing == null) {
                                            db.collection("brochures").add(payload).await()
                                        } else {
                                            db.collection("brochures").document(existing.id).set(payload, SetOptions.merge()).await()
                                        }

                                        statusText = "Brochure synced successfully."
                                        clearBrochureForm()
                                        loadBrochures()
                                    } catch (e: Exception) {
                                        statusText = "Upload failed: ${e.message}"
                                    } finally {
                                        uploading = false
                                    }
                                }
                            },
                            enabled = !uploading,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(if (uploading) "Syncing..." else if (editingBrochure == null) "Add brochure" else "Save brochure")
                        }
                    }

                    if (editingBrochure != null) {
                        item {
                            TextButton(onClick = { clearBrochureForm() }, enabled = !uploading) {
                                Text("Cancel edit")
                            }
                        }
                    }

                    if (uploading || loadingBrochures) {
                        item {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        }
                    }

                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Published brochures", style = MaterialTheme.typography.titleMedium)
                            TextButton(onClick = { loadBrochures() }, enabled = !loadingBrochures) {
                                Text("Refresh")
                            }
                        }
                    }

                    items(brochures, key = { it.id }) { brochure ->
                        AdminBrochureCard(
                            brochure = brochure,
                            onEdit = { editBrochure(brochure) },
                            onDelete = {
                                uploading = true
                                statusText = "Deleting brochure..."
                                scope.launch {
                                    try {
                                        if (brochure.file.isNotBlank()) {
                                            runCatching { storage.child(brochure.file).delete().await() }
                                        }
                                        db.collection("brochures").document(brochure.id).delete().await()
                                        if (editingBrochure?.id == brochure.id) {
                                            clearBrochureForm()
                                        }
                                        statusText = "Brochure deleted."
                                        loadBrochures()
                                    } catch (e: Exception) {
                                        statusText = "Delete failed: ${e.message}"
                                    } finally {
                                        uploading = false
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun UserActivityCard(
    user: UserActivity,
    dateFormat: SimpleDateFormat,
    onToggleWhitelist: (String, Boolean) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (user.whitelisted)
                MaterialTheme.colorScheme.surfaceVariant
            else
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
        )
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        user.name.ifBlank { "Unnamed user" },
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(user.email, style = MaterialTheme.typography.bodySmall)
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            if (user.whitelisted) "Whitelisted" else "Restricted",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (user.whitelisted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                        )
                        Switch(
                            checked = user.whitelisted,
                            onCheckedChange = { onToggleWhitelist(user.id, it) },
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }

                    IconButton(onClick = { expanded = !expanded }) {
                        Icon(
                            if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = "Expand details"
                        )
                    }
                }
            }

            Text(
                "Last Active: ${if (user.lastLogin > 0) dateFormat.format(Date(user.lastLogin)) else "Never"}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier.padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    HorizontalDivider(
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)
                    )

                    // Device Info
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Info, "", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Device: ${user.device} (Android ${user.androidVer}) | App: ${user.appVersion}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    if (user.dailyStats.isNotEmpty()) {
                        Text(
                            "Recent Usage Stats",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )

                        user.dailyStats.forEach { stats ->
                            DailyStatsItem(stats, dateFormat)
                        }
                    } else {
                        Text(
                            "No detailed usage logs found.",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DailyStatsItem(stats: DailyStats, dateFormat: SimpleDateFormat) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Column(Modifier.padding(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    stats.date.replace("_", "/"),
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    "Last activity: ${dateFormat.format(Date(stats.lastActive))}${if (stats.appVersion.isNotEmpty()) " (v${stats.appVersion})" else ""}",
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 10.sp
                )
            }

            Spacer(Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                StatChip(Icons.Default.Search, "${stats.searchCount} Searches")
                StatChip(Icons.Default.Mouse, "${stats.totalClicks} Clicks")
                if (stats.latitude != 0.0) {
                    StatChip(Icons.Default.LocationOn, "Location Logged")
                }
            }

            if (stats.buttonClicks.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "Clicks: " + stats.buttonClicks.entries.joinToString(", ") { "${it.key}: ${it.value}" },
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun StatChip(icon: ImageVector, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.secondary)
        Spacer(Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, fontSize = 11.sp)
    }
}

@Composable
private fun AdminBrochureCard(
    brochure: Brochure,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(brochure.name.ifBlank { "Untitled brochure" }, fontWeight = FontWeight.SemiBold)
            if (brochure.description.isNotBlank()) {
                Text(brochure.description, style = MaterialTheme.typography.bodyMedium)
            }
            if (brochure.brochureDate.isNotBlank()) {
                Text("Date: ${brochure.brochureDate}", style = MaterialTheme.typography.bodySmall)
            }
            Text(
                "Attachment: ${brochure.file.ifBlank { "Not set" }}",
                style = MaterialTheme.typography.bodySmall
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onEdit) {
                    Text("Edit")
                }
                TextButton(onClick = onDelete) {
                    Text("Delete")
                }
            }
        }
    }
}

private fun displayNameForUri(context: Context, uri: Uri): String {
    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (index >= 0 && cursor.moveToFirst()) {
            return cursor.getString(index)
        }
    }
    return uri.lastPathSegment?.substringAfterLast('/') ?: "brochure"
}

private fun safeStorageName(name: String): String =
    name.lowercase(Locale.US)
        .replace(Regex("[^a-z0-9._-]+"), "-")
        .trim('-')
        .ifBlank { "brochure" }
