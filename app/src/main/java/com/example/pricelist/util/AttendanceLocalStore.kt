package com.example.pricelist.util

import android.content.Context
import com.google.gson.Gson
import java.io.File

data class LocalAttendanceData(
    val lastSyncAt: Long = 0L,
    val staffMaster: List<CachedStaff> = emptyList(),
    val attendanceList: List<CachedRecord> = emptyList(),
    val devices: List<CachedDevice> = emptyList()
)

data class CachedStaff(
    val id: String,
    val name: String,
    val email: String = "",
    val role: String = ""
)

data class CachedRecord(
    val id: String,
    val staffId: String,
    val staffName: String,
    val staffEmail: String,
    val dateKey: String,
    val lastActive: Long,
    val inTime: String = "",
    val outTime: String = "",
    val status: String = "Present",
    val remarks: String = "",
    val locationName: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val deviceName: String = "",
    val appVersion: String = "",
    val searchCount: Long = 0L,
    val totalClicks: Long = 0L
)

data class CachedDevice(
    val id: String,
    val name: String,
    val location: String = "",
    val status: String = "Online",
    val lastActive: Long = 0L
)

object AttendanceLocalStore {
    private const val FILE_NAME = "attendance_cache.json"
    private val gson = Gson()

    fun save(context: Context, data: LocalAttendanceData) {
        runCatching {
            val file = File(context.filesDir, FILE_NAME)
            file.writeText(gson.toJson(data))
        }
    }

    fun load(context: Context): LocalAttendanceData {
        return runCatching {
            val file = File(context.filesDir, FILE_NAME)
            if (!file.exists()) return LocalAttendanceData()
            val json = file.readText()
            gson.fromJson(json, LocalAttendanceData::class.java) ?: LocalAttendanceData()
        }.getOrDefault(LocalAttendanceData())
    }

    fun clear(context: Context) {
        runCatching {
            File(context.filesDir, FILE_NAME).delete()
        }
    }
}
