package com.example.pricelist.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "items")
data class ItemEntity(
    val Code: String = "",
    val DiscPercent: Double = 0.0,
    val MRP: Double = 0.0,
    @PrimaryKey val MasterCode: String = "",
    val Name: String = "",
    val PRICE3: Double = 0.0,
    val Unit: String = "",
    val imageExt: String = "",
    val imageH: Int = 0,
    val imageW: Int = 0,
    val imageYes: Boolean = false,
    val lastFBUpdate: Long = 0L

)
