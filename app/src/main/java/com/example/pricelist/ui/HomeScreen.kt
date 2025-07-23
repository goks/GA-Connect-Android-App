package com.example.pricelist.ui

import android.util.Log
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
import com.example.pricelist.viewmodel.ItemViewModel
import com.example.pricelist.viewmodel.ItemViewModelFactory
import com.google.firebase.auth.FirebaseAuth
import java.io.File
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.ktx.storage
import kotlinx.coroutines.tasks.await


@Composable
fun HomeScreen(navController: NavController) {
    var syncing by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val viewModel: ItemViewModel = viewModel(factory = ItemViewModelFactory(context))
    val items by viewModel.itemsFlow.collectAsState()
    val query by viewModel.query.collectAsState()
    val user = FirebaseAuth.getInstance().currentUser

    var selectedItem by remember { mutableStateOf<ItemEntity?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Welcome, ${user?.displayName ?: "Guest"}",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )

        OutlinedTextField(
            value = query,
            onValueChange = { viewModel.onSearchChanged(it) },   // keep spaces
            label = { Text("Search items…") },
            modifier = Modifier.fillMaxWidth()
        )


        if (syncing) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
        }
        Button(
            onClick = {
                syncing = true
                viewModel.syncNow(context) {
                    syncing = false
                }
            },
            enabled = !syncing,
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
        ) {
            Text("Sync from Firestore")
        }
        Spacer(Modifier.height(8.dp))

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(items) { item ->
                ItemCard(item) { selectedItem = it }
            }
        }
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
                        // Placeholder or loading spinner
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
