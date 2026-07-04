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

    @Query("SELECT * FROM items WHERE MasterCode = :code")
    suspend fun getItemByMasterCode(code: String): ItemEntity?

    @Query("SELECT MasterCode FROM items")
    suspend fun getAllMasterCodes(): List<String>

    @Delete
    suspend fun deleteItems(items: List<ItemEntity>)

    @Query("DELETE FROM items WHERE MasterCode IN (:codes)")
    suspend fun deleteByMasterCodes(codes: List<String>)

    @Query("UPDATE items SET PriceA = :priceA, PriceB = :priceB, PriceC = :priceC, PurchasePrice = :purchasePrice WHERE MasterCode = :masterCode")
    suspend fun updateSensitiveFields(masterCode: String, priceA: Double, priceB: Double, priceC: Double, purchasePrice: Double)

    @Query("UPDATE items SET PriceA = 0, PriceB = 0, PriceC = 0, PurchasePrice = 0")
    suspend fun clearSensitiveFields()

    @Transaction
    suspend fun updateSensitiveFieldsBatch(updates: List<SensitivePriceUpdate>) {
        updates.forEach { 
            updateSensitiveFields(it.masterCode, it.priceA, it.priceB, it.priceC, it.purchasePrice)
        }
    }
}

data class SensitivePriceUpdate(
    val masterCode: String,
    val priceA: Double,
    val priceB: Double,
    val priceC: Double,
    val purchasePrice: Double
)

