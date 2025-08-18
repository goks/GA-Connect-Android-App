package com.example.pricelist.util
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.pricelist.MainActivity
import com.example.pricelist.R
import com.example.pricelist.data.AppDatabase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone


class StockChangeChecker(private val context: Context) {
    private val TAG = "StockChangeChecker"
    private val CHANNEL_ID = "stock_updates_channel"
    private val NOTIFICATION_ID = 1001

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Stock Updates"
            val descriptionText = "Notifications for new stock updates"
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
            Log.d(TAG, "Notification channel created")
        }
    }

    fun checkForNewStock(onComplete: (Boolean) -> Unit) {
        // Ensure user is authenticated
        val user = FirebaseAuth.getInstance().currentUser
        if (user == null) {
            Log.w(TAG, "User not authenticated, skipping stock check")
            onComplete(false)
            return
        }

        Log.d(TAG, "Checking for new stock changes")
        val db = FirebaseFirestore.getInstance()
        val lastStockSyncTime = AppPrefs.getStockLastSyncTime(context)

        // Get the document directly
        db.collection("DB_Service")
            .document("stock_changes_snapshot")
            .get()
            .addOnSuccessListener { documentSnapshot ->
                if (!documentSnapshot.exists()) {
                    Log.w(TAG, "Stock changes snapshot document doesn't exist")
                    onComplete(false)
                    return@addOnSuccessListener
                }

                // Get the "changes" array field directly from the document
                @Suppress("UNCHECKED_CAST")
                val changes = (documentSnapshot.get("changes") as? List<*>)?.mapNotNull {
                    it as? Map<String, Any>
                }

                // Get the updatedAt timestamp from the document
                val updatedAtValue = documentSnapshot.get("updatedAt")
                Log.d(TAG, "Document updatedAt value: $updatedAtValue")
                val documentUpdatedAtMillis = when (updatedAtValue) {
                    is com.google.firebase.Timestamp -> updatedAtValue.toDate().time
                    is String -> {
                        try {
                            val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSSSS", Locale.getDefault())
                            sdf.timeZone = TimeZone.getTimeZone("UTC")
                            sdf.parse(updatedAtValue)?.time
                        } catch (_: Exception) {
                            try {
                                SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS", Locale.getDefault()).apply {
                                    timeZone = TimeZone.getTimeZone("UTC")
                                }.parse(updatedAtValue)?.time
                            } catch (_: Exception) {
                                null
                            }
                        }
                    }
                    else -> null
                } ?: System.currentTimeMillis()
                Log.d(TAG, "Document updatedAt value: $documentUpdatedAtMillis")

                if (changes == null || changes.isEmpty()) {
                    Log.d(TAG, "No changes found in the document")
                    Toast.makeText(context, "All recent stock updates are already fetched.", Toast.LENGTH_SHORT).show()
                    onComplete(false)
                    return@addOnSuccessListener
                }

                // Check if the document has been updated since last sync
                if (documentUpdatedAtMillis <= lastStockSyncTime) {
                    Log.d(TAG, "No new changes since last sync. Document updated at: $documentUpdatedAtMillis, Last sync: $lastStockSyncTime")
                    Toast.makeText(context, "All recent stock updates are already fetched.", Toast.LENGTH_SHORT).show()
                    onComplete(false)
                    return@addOnSuccessListener
                }

                Log.d(TAG, "Retrieved ${changes.size} stock change documents")
                val newStockItems = mutableListOf<Map<String, Any>>()
                for (change in changes) {
                    newStockItems.add(change)
                }
                if (newStockItems.isNotEmpty()) {
                    Log.i(TAG, "Alerting user about new stock: $newStockItems")
                    CoroutineScope(Dispatchers.IO).launch {
                        alertUserAboutNewStock(newStockItems, documentUpdatedAtMillis)
                        AppPrefs.setStockLastSyncTime(context, documentUpdatedAtMillis)
                    }
                    onComplete(true)
                } else {
                    Log.d(TAG, "No new stock found since last sync ($lastStockSyncTime)")
                    onComplete(false)
                }
            }
            .addOnFailureListener { e ->
                handleFirestoreError(e)
                onComplete(false)
            }
    }

    private fun handleFirestoreError(e: Exception) {
        if (e is FirebaseFirestoreException && e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
            Log.e(
                TAG,
                "Permission denied accessing stock changes. Check Firestore security rules.",
                e
            )
        } else {
            Log.e(TAG, "Error checking for stock changes", e)
        }
    }

    private suspend fun alertUserAboutNewStock(items: List<Map<String, Any>>, documentUpdatedAtMillis: Long) {
        val db = AppDatabase.getInstance(context)
        for (item in items) {
            val name = item["Name"] as? String ?: continue
            val delta = (item["delta"] as? Number)?.toInt() ?: 0
            val masterCodeRaw = item["MasterCode"]
            val masterCode = when (masterCodeRaw) {
                is Number -> masterCodeRaw.toString()
                is String -> masterCodeRaw
                else -> ""
            }
            val itemEntity = db.itemDao().getItemByMasterCode(masterCode)
            val unit = itemEntity?.Unit ?: "unit"
            val message = "$name $delta $unit added."
            Log.d(TAG, "New stock item: $name, updated at: $documentUpdatedAtMillis with delta: ${item["delta"]} and unit: ${itemEntity?.MasterCode}  ")

            StockAlertStore.addAlert(
                context,
                StockAlert(
                    message,
                    System.currentTimeMillis(),
                    documentUpdatedAtMillis,
                    delta,
                    unit,
                    masterCode // Pass masterCode for navigation
                )
            )
        }
        if (items.isNotEmpty()) {
            val count = items.size
            val notifMsg = if (count == 1) "1 item stock updated" else "$count items stock updated"
            showNotification(notifMsg)
        }
        CoroutineScope(Dispatchers.Main).launch {
            Toast.makeText(context, "New stock added: ${items.size} items", Toast.LENGTH_LONG).show()
        }
    }

    private fun showNotification(message: String) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("open_stock_alerts", true)
        }
        val pendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE)
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Stock Update")
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
        with(NotificationManagerCompat.from(context)) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
                    android.content.pm.PackageManager.PERMISSION_GRANTED
                ) {
                    notify(NOTIFICATION_ID, builder.build())
                }
            } else {
                notify(NOTIFICATION_ID, builder.build())
            }
        }
    }
}