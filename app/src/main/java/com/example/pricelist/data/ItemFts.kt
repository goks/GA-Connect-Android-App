package com.example.pricelist.data

import androidx.room.Entity
import androidx.room.Fts4

@Fts4(contentEntity = ItemEntity::class)
@Entity(tableName = "itemsFts")
data class ItemFts(
    val Name: String,
    val Code:String
)
