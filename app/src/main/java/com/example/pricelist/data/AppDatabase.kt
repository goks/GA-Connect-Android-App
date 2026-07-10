package com.example.pricelist.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [ItemEntity::class, ItemFts::class], // include FTS entity
    // Bumped DB version to 3 for `Group` column
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun itemDao(): ItemDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE items ADD COLUMN `TaxPercent` REAL NOT NULL DEFAULT 0.0")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Add Group column to items table
                db.execSQL("ALTER TABLE items ADD COLUMN `Group` TEXT NOT NULL DEFAULT 'General'")
                
                // Recreate FTS table to include the new Group column
                db.execSQL("DROP TABLE IF EXISTS itemsFts")
                db.execSQL("CREATE VIRTUAL TABLE IF NOT EXISTS itemsFts USING FTS4(content=`items`, Name, Code, `Group`)")
                db.execSQL("INSERT INTO itemsFts(rowid, Name, Code, `Group`) SELECT rowid, Name, Code, `Group` FROM items")
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "price_list.db"
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                 .fallbackToDestructiveMigration()
                 .build()
                 .also { INSTANCE = it }
            }
        }
    }
}
