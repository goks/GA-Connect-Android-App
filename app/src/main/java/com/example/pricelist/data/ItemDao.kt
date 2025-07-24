package com.example.pricelist.data

import androidx.room.*

@Dao
interface ItemDao {
    @Query("SELECT * FROM items")
    suspend fun getAllItems(): List<ItemEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<ItemEntity>)

    @Query("DELETE FROM items")
    suspend fun clearAll()

    @Query("SELECT items.* FROM items JOIN itemsFts ON items.rowid = itemsFts.rowid WHERE itemsFts MATCH :query")
    suspend fun searchItems(query: String): List<ItemEntity>

    @Query("SELECT * FROM items WHERE Code = :code")
    suspend fun getItemByCode(code: String): ItemEntity?
}

