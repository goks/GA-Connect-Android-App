package com.example.pricelist.util

import android.content.Context
import androidx.core.content.edit

object AppPrefs {
    private const val PREFS_NAME = "app_prefs"
    private const val KEY_FIRST_SYNC_DONE = "first_sync_done"
    private const val KEY_LAST_SYNC_TIME = "last_sync_time"
    private const val KEY_STOCK_LAST_SYNC_TIME = "stock_last_sync_time"
    private const val KEY_SHOW_MULTIPLE_PRICES = "show_multiple_prices"
    private const val KEY_SHOW_PURCHASE_PRICE = "show_purchase_price"
    private const val KEY_SHOW_STOCK = "show_stock"
    private const val KEY_SCHEMA_MIGRATION_DONE = "schema_migration_v3_done"

    fun isFirstSyncDone(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_FIRST_SYNC_DONE, false)
    }

    fun setFirstSyncDone(context: Context, done: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit {
                putBoolean(KEY_FIRST_SYNC_DONE, done)
            }
    }

    fun getLastSyncTime(context: Context): Long {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getLong(KEY_LAST_SYNC_TIME, 0L)
    }

    fun setLastSyncTime(context: Context, time: Long) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit {
                putLong(KEY_LAST_SYNC_TIME, time)
            }
    }

    fun getStockLastSyncTime(context: Context): Long {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getLong(KEY_STOCK_LAST_SYNC_TIME, 0L)
    }

    fun setStockLastSyncTime(context: Context, time: Long) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit {
                putLong(KEY_STOCK_LAST_SYNC_TIME, time)
            }
    }

    fun isShowMultiplePricesEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_SHOW_MULTIPLE_PRICES, false)
    }

    fun setShowMultiplePricesEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit {
                putBoolean(KEY_SHOW_MULTIPLE_PRICES, enabled)
            }
    }

    fun isShowPurchasePriceEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_SHOW_PURCHASE_PRICE, false)
    }

    fun setShowPurchasePriceEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit {
                putBoolean(KEY_SHOW_PURCHASE_PRICE, enabled)
            }
    }

    fun isShowStockEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_SHOW_STOCK, false)
    }

    fun setShowStockEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit {
                putBoolean(KEY_SHOW_STOCK, enabled)
            }
    }

    fun isSchemaMigrationDone(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_SCHEMA_MIGRATION_DONE, false)
    }

    fun setSchemaMigrationDone(context: Context, done: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit {
                putBoolean(KEY_SCHEMA_MIGRATION_DONE, done)
            }
    }
}
