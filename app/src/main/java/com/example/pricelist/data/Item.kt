package com.example.pricelist.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "items")
data class Item(
    @PrimaryKey val Code: String,
    val DiscPercent: Double,
    val MRP: Double,
    val MasterCode: String,
    val Name: String,
    val PRICE3: Double,
    val Unit: String,
    val imageExt: String,
    val imageH: Int,
    val imageW: Int,
    val imageYes: Boolean,
    val lastFBUpdate: Long = 0L
)
