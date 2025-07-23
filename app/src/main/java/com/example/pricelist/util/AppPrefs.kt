package com.example.pricelist.util

import android.content.Context
import androidx.core.content.edit

object AppPrefs {
    private const val PREFS_NAME = "app_prefs"
    private const val KEY_FIRST_SYNC_DONE = "first_sync_done"
    private const val KEY_LAST_SYNC_TIME = "last_sync_time"

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
}
