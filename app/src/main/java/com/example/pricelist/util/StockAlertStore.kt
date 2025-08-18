// app/src/main/java/com/example/pricelist/util/StockAlertStore.kt
package com.example.pricelist.util

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

object StockAlertStore {
    private const val PREFS_NAME = "stock_alerts"
    private const val KEY_ALERTS = "alerts"
    private val gson = Gson()
    private val monthMillis = 30L * 24 * 60 * 60 * 1000

    fun addAlert(context: Context, alert: StockAlert) {
        val alerts = getAlerts(context).toMutableList()
        alerts.add(alert)
        saveAlerts(context, alerts)
    }

    fun getAlerts(context: Context): List<StockAlert> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_ALERTS, "[]")
        val type = object : TypeToken<List<StockAlert>>() {}.type
        val allAlerts: List<StockAlert> = gson.fromJson(json, type)
        val now = System.currentTimeMillis()
        // Purge alerts older than 1 month
        return allAlerts.filter { now - it.timestamp <= monthMillis }
    }

    private fun saveAlerts(context: Context, alerts: List<StockAlert>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = gson.toJson(alerts)
        prefs.edit().putString(KEY_ALERTS, json).apply()
    }

    fun clearAlerts(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().remove(KEY_ALERTS).apply()
    }
}