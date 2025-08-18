// app/src/main/java/com/example/pricelist/util/StockAlert.kt
package com.example.pricelist.util

data class StockAlert(
    val message: String,
    val timestamp: Long,
    val updatedAt: Long,
    val delta: Int, // Represents the change in stock quantity
    val unit: String, // Unit of the item
    val masterCode: String // Add masterCode for navigation
//    StockAlertStore.addAlert(context, StockAlert(message, System.currentTimeMillis()))
)