package com.example.pricelist.util

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.util.Log
import com.google.android.gms.location.LocationServices
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FieldValue
import java.util.*

import com.google.firebase.firestore.SetOptions
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object AnalyticsManager {
    private var firebaseAnalytics: FirebaseAnalytics? = null
    private var currentDocPath: String? = null

    fun initialize(context: Context) {
        firebaseAnalytics = FirebaseAnalytics.getInstance(context)
        FirebaseAuth.getInstance().currentUser?.let { user ->
            firebaseAnalytics?.setUserId(user.uid)
            firebaseAnalytics?.setUserProperty("app_version", com.example.pricelist.BuildConfig.VERSION_NAME)
            firebaseAnalytics?.setUserProperty("user_email", user.email)
        }
    }

    private fun getDailyDocId(): String {
        val sdf = SimpleDateFormat("yyyy_MM_dd", Locale.getDefault())
        return sdf.format(Date())
    }

    fun logEvent(name: String, params: Bundle? = null) {
        firebaseAnalytics?.logEvent(name, params)
        Log.d("Analytics", "Event logged: $name")
    }

    fun logButtonClick(buttonName: String) {
        val bundle = Bundle().apply {
            putString("button_name", buttonName)
            putString("user_email", FirebaseAuth.getInstance().currentUser?.email)
        }
        logEvent("button_click", bundle)
        
        currentDocPath?.let { path ->
            val db = FirebaseFirestore.getInstance()
            db.document(path)
                .update(
                    "button_clicks.$buttonName", FieldValue.increment(1),
                    "total_clicks", FieldValue.increment(1),
                    "last_active", FieldValue.serverTimestamp()
                )
        }
    }

    fun logSearch(query: String) {
        if (query.isBlank()) return
        val bundle = Bundle().apply {
            putString(FirebaseAnalytics.Param.SEARCH_TERM, query)
        }
        logEvent(FirebaseAnalytics.Event.SEARCH, bundle)

        currentDocPath?.let { path ->
            val db = FirebaseFirestore.getInstance()
            db.document(path)
                .update(
                    "search_count", FieldValue.increment(1),
                    "last_active", FieldValue.serverTimestamp()
                )
        }
    }

    @SuppressLint("MissingPermission")
    fun logSessionStart(context: Context) {
        val user = FirebaseAuth.getInstance().currentUser ?: return
        val db = FirebaseFirestore.getInstance()
        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
        
        val dateId = getDailyDocId()
        // Structure: usage_logs/{userId}/daily_stats/{yyyy_MM_dd}
        val docPath = "usage_logs/${user.uid}/daily_stats/$dateId"
        currentDocPath = docPath

        // Calculate expiration date (180 days from now)
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.DAY_OF_YEAR, 180)
        val expireAt = calendar.time

        val logSession: (Double, Double) -> Unit = { lat, lon ->
            val sessionData = hashMapOf(
                "userId" to user.uid,
                "email" to user.email,
                "date" to dateId,
                "last_active" to FieldValue.serverTimestamp(),
                "expireAt" to expireAt, // TTL field
                "latitude" to lat,
                "longitude" to lon,
                "deviceName" to android.os.Build.MODEL,
                "appVersion" to com.example.pricelist.BuildConfig.VERSION_NAME,
                // These will be initialized only if the document is new
                "search_count" to FieldValue.increment(0),
                "total_clicks" to FieldValue.increment(0)
            )

            db.document(docPath)
                .set(sessionData, SetOptions.merge())
                .addOnSuccessListener {
                    Log.d("Analytics", "Daily log updated: $docPath")
                }
                .addOnFailureListener { e ->
                    Log.e("Analytics", "Failed to update daily log", e)
                }
        }

        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            logSession(location?.latitude ?: 0.0, location?.longitude ?: 0.0)
        }.addOnFailureListener {
            logSession(0.0, 0.0)
        }

        logEvent(FirebaseAnalytics.Event.APP_OPEN)
    }
}
