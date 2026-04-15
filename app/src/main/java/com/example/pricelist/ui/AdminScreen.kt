package com.example.pricelist.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
    val androidVer: String
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
    var selectedTab by remember { mutableStateOf(0) }
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
        db.collection("users")
            .orderBy("lastLogin", Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { result ->
                users = result.documents.map { doc ->
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
                        androidVer = doc.getString("androidVer").orEmpty()
                    )
                }
                loadingUsers = false
            }
            .addOnFailureListener {
                statusText = "Could not load users: ${it.message}"
                loadingUsers = false
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
                            UserActivityCard(user, dateFormat)
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
private fun UserActivityCard(user: UserActivity, dateFormat: SimpleDateFormat) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(user.name.ifBlank { "Unnamed user" }, fontWeight = FontWeight.SemiBold)
            Text(user.email, style = MaterialTheme.typography.bodyMedium)
            Text(
                "Last login: ${if (user.lastLogin > 0) dateFormat.format(Date(user.lastLogin)) else "Not logged"}",
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                "Access: ${if (user.whitelisted) "Whitelisted" else "Pending"} ${user.role.ifBlank { "" }}",
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                "Last 30 days usage: ${user.last30Days.size} login(s)",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium
            )
            user.last30Days.take(5).forEach { login ->
                Text(
                    "- $login",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            if (user.device.isNotBlank() || user.androidVer.isNotBlank()) {
                Text(
                    "Device: ${user.device} Android ${user.androidVer}",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
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
