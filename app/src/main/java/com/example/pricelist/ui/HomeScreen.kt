package com.example.pricelist.ui

import android.annotation.SuppressLint
import android.content.Context
import android.os.Environment
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MoreVert
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import coil.compose.rememberAsyncImagePainter
import com.example.pricelist.data.ItemEntity
import com.example.pricelist.util.AppPrefs
import com.example.pricelist.viewmodel.ItemViewModel
import com.example.pricelist.viewmodel.ItemViewModelFactory
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.ktx.storage
import kotlinx.coroutines.tasks.await
import java.io.File

const val ROUTE_BROCHURES = "brochures"

@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(navController: NavController) {
    val context = LocalContext.current
    val viewModel: ItemViewModel = viewModel(factory = ItemViewModelFactory(context))
    val items by viewModel.itemsFlow.collectAsState()
    val query by viewModel.query.collectAsState()
    val user = FirebaseAuth.getInstance().currentUser
    var selectedItem by remember { mutableStateOf<ItemEntity?>(null) }
    var showMenu by remember { mutableStateOf(false) }
    val firstSyncDone = remember { mutableStateOf(AppPrefs.isFirstSyncDone(context)) }
    val lastSync = remember { AppPrefs.getLastSyncTime(context) }
    var syncing by remember { mutableStateOf(false) }
    var showUpdatePrompt by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (firstSyncDone.value) {
            val serverUpdatedAt = viewModel.getLastServerUpdateTimestamp()
            if (serverUpdatedAt > lastSync) {
                showUpdatePrompt = true
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
                                showMenu = false
                                navController.navigate(ROUTE_BROCHURES)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Sign out") },
                            onClick = {
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
    ) {  innerPadding ->
        LazyColumn(
//            contentPadding = innerPadding, // ✅ fixes shape/cutoff issues
        modifier = Modifier.fillMaxSize().padding(innerPadding).padding(horizontal = 16.dp, vertical = 8.dp), // consistent horizontal padding,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { viewModel.onSearchChanged(it) },
                    label = { Text("Search items…") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        item {
            val isSearching = query.isNotBlank()

            if (!firstSyncDone.value &&  !isSearching && items.isEmpty()) {
                if (syncing) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                } else {
                    Button(
                        onClick = {
                            syncing = true
                            viewModel.syncNow(context) {
                                AppPrefs.setFirstSyncDone(context, true)
                                AppPrefs.setLastSyncTime(context, System.currentTimeMillis())
                                syncing = false
                                firstSyncDone.value = true // ✅ mark sync done
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
            } else if (showUpdatePrompt) {
                OutlinedButton(
                    onClick = {
                        syncing = true
                        viewModel.syncNow(context) {
                            syncing = false
                            showUpdatePrompt = false
                        }
                    },
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("New update available – Sync now?")
                }
            }


        }

        items(items) { item ->
            ItemCard(item) { selectedItem = it }
        }
            if (items.isEmpty() && query.isNotBlank()) {
                item {
                    Text(
                        text = "No items found.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 24.dp)
                    )
                }
            }    }


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


// -----------------------------
// 🔻 Reusable Composables
// -----------------------------
@Composable
fun ItemCard(item: ItemEntity, onImageClick: (ItemEntity) -> Unit) {
    val context = LocalContext.current
    val localFile = File(context.filesDir, "images/${item.MasterCode}${item.imageExt}")
    val imagePath = remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }

    LaunchedEffect(item.imageYes) {
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

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(4.dp)
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
                        .size(72.dp)
                        .padding(end = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (imagePath.value != null) {
                        AsyncImage(
                            model = File(imagePath.value!!),
                            contentDescription = null,
                            modifier = Modifier
                                .fillMaxSize()
                                .clickable { onImageClick(item) }
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
                Text(text = item.Name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(text = "₹${item.PRICE3} • Unit: ${item.Unit}", style = MaterialTheme.typography.bodyMedium)
                Text(text = "Code: ${item.Code}", style = MaterialTheme.typography.bodySmall, color = Color.Gray)

                if (item.DiscPercent != 0.0) {
                    DiscountBadge(item.DiscPercent)
                }
            }
        }
    }
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

