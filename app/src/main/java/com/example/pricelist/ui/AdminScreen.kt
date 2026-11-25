package com.example.pricelist.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.pricelist.data.Brochure
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.example.pricelist.util.AttendanceLocalStore
import com.example.pricelist.util.CachedDevice
import com.example.pricelist.util.CachedRecord
import com.example.pricelist.util.CachedStaff
import com.example.pricelist.util.LocalAttendanceData
import com.google.firebase.firestore.SetOptions
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.ktx.storage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private const val ADMIN_EMAIL = "gokulav2@gmail.com"

private data class UserActivity(
    val id: String,
    val name: String,
    val email: String,
    val whitelisted: Boolean,
    val isAdmin: Boolean,
    val role: String,
    val lastLogin: Long,
    val last30Days: List<String>,
    val device: String,
    val androidVer: String,
    val appVersion: String,
    val dailyStats: List<DailyStats> = emptyList()
)

private data class DailyStats(
    val date: String,
    val searchCount: Long,
    val totalClicks: Long,
    val lastActive: Long,
    val latitude: Double,
    val longitude: Double,
    val appVersion: String,
    val buttonClicks: Map<String, Long> = emptyMap()
)

private data class AttendanceStaff(
    val id: String,
    val name: String,
    val email: String = "",
    val role: String = ""
)

private data class AttendanceDevice(
    val id: String,
    val name: String,
    val location: String = "",
    val status: String = "Online",
    val lastActive: Long = 0L
)

private data class AttendanceRecord(
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
    val totalClicks: Long = 0L,
    val buttonClicks: Map<String, Long> = emptyMap()
)

private fun getTodayDateKey(): String {
    val sdf = SimpleDateFormat("yyyy_MM_dd", Locale.getDefault())
    return sdf.format(Date())
}

private fun normalizeDateKey(dateStr: String): String {
    return parseAnyDateToKey(dateStr)
}

private fun parseAnyDateToKey(rawDate: String): String {
    val clean = rawDate.trim()
    if (clean.matches(Regex("\\d{4}_\\d{2}_\\d{2}"))) return clean
    if (clean.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))) return clean.replace("-", "_")
    if (clean.matches(Regex("\\d{2}/\\d{2}/\\d{4}"))) {
        return try {
            val sdfIn = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
            val sdfOut = SimpleDateFormat("yyyy_MM_dd", Locale.getDefault())
            val date = sdfIn.parse(clean)
            if (date != null) sdfOut.format(date) else clean
        } catch (e: Exception) { clean }
    }
    if (clean.matches(Regex("\\d{2}-\\d{2}-\\d{4}"))) {
        return try {
            val sdfIn = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())
            val sdfOut = SimpleDateFormat("yyyy_MM_dd", Locale.getDefault())
            val date = sdfIn.parse(clean)
            if (date != null) sdfOut.format(date) else clean
        } catch (e: Exception) { clean }
    }
    if (clean.length >= 10 && clean.substring(0, 10).matches(Regex("\\d{4}-\\d{2}-\\d{2}"))) {
        return clean.substring(0, 10).replace("-", "_")
    }
    return clean.replace("-", "_").replace("/", "_")
}

private fun formatDateKey(dateKey: String): String {
    val norm = normalizeDateKey(dateKey)
    return try {
        val sdfIn = SimpleDateFormat("yyyy_MM_dd", Locale.getDefault())
        val date = sdfIn.parse(norm) ?: return norm
        val sdfOut = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
        sdfOut.format(date)
    } catch (e: Exception) {
        norm
    }
}

private fun formatShortDate(dateKey: String): String {
    val norm = normalizeDateKey(dateKey)
    return try {
        val sdfIn = SimpleDateFormat("yyyy_MM_dd", Locale.getDefault())
        val date = sdfIn.parse(norm) ?: return norm
        val sdfOut = SimpleDateFormat("dd MMM", Locale.getDefault())
        sdfOut.format(date)
    } catch (e: Exception) {
        norm
    }
}

private fun formatDayOfWeek(dateKey: String): String {
    val norm = normalizeDateKey(dateKey)
    return try {
        val sdfIn = SimpleDateFormat("yyyy_MM_dd", Locale.getDefault())
        val date = sdfIn.parse(norm) ?: return ""
        val sdfOut = SimpleDateFormat("EEEE", Locale.getDefault())
        sdfOut.format(date)
    } catch (e: Exception) {
        ""
    }
}

private fun addDaysToDateKey(dateKey: String, days: Int): String {
    return try {
        val norm = normalizeDateKey(dateKey)
        val sdfIn = SimpleDateFormat("yyyy_MM_dd", Locale.getDefault())
        val cal = Calendar.getInstance()
        val date = sdfIn.parse(norm) ?: Date()
        cal.time = date
        cal.add(Calendar.DAY_OF_YEAR, days)
        sdfIn.format(cal.time)
    } catch (e: Exception) {
        getTodayDateKey()
    }
}

private fun getStartOfWeekDateKey(dateKey: String): String {
    return try {
        val norm = normalizeDateKey(dateKey)
        val sdfIn = SimpleDateFormat("yyyy_MM_dd", Locale.getDefault())
        val cal = Calendar.getInstance()
        val date = sdfIn.parse(norm) ?: Date()
        cal.time = date
        cal.firstDayOfWeek = Calendar.MONDAY
        cal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        sdfIn.format(cal.time)
    } catch (e: Exception) {
        getTodayDateKey()
    }
}

private fun getMonthKey(dateKey: String): String {
    val norm = normalizeDateKey(dateKey)
    return if (norm.length >= 7) norm.substring(0, 7) else norm
}

private fun formatMonthKey(monthKey: String): String {
    return try {
        val norm = monthKey.replace("-", "_")
        val sdfIn = SimpleDateFormat("yyyy_MM", Locale.getDefault())
        val date = sdfIn.parse(norm) ?: return norm
        val sdfOut = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
        sdfOut.format(date)
    } catch (e: Exception) {
        monthKey
    }
}

private fun addMonthsToMonthKey(monthKey: String, months: Int): String {
    return try {
        val norm = monthKey.replace("-", "_")
        val sdfIn = SimpleDateFormat("yyyy_MM", Locale.getDefault())
        val cal = Calendar.getInstance()
        val date = sdfIn.parse(norm) ?: Date()
        cal.time = date
        cal.add(Calendar.MONTH, months)
        sdfIn.format(cal.time)
    } catch (e: Exception) {
        val sdf = SimpleDateFormat("yyyy_MM", Locale.getDefault())
        sdf.format(Date())
    }
}

// The attendance importer writes status/punch/uid as integers, so never use
// DocumentSnapshot.getString() on those fields: it hard-casts to String and
// throws ClassCastException for numeric values.
private fun DocumentSnapshot.anyString(field: String): String? = when (val v = get(field)) {
    null -> null
    is String -> v
    is Number -> v.toString()
    is Boolean -> v.toString()
    else -> null
}

private fun DocumentSnapshot.anyLong(field: String): Long? = when (val v = get(field)) {
    null -> null
    is Number -> v.toLong()
    is Boolean -> if (v) 1L else 0L
    is String -> v.trim().toLongOrNull()
    else -> null
}

private fun DocumentSnapshot.anyDouble(field: String): Double? = when (val v = get(field)) {
    null -> null
    is Number -> v.toDouble()
    is String -> v.trim().toDoubleOrNull()
    else -> null
}

private fun DocumentSnapshot.epochMillis(field: String): Long? = when (val v = get(field)) {
    null -> null
    is Timestamp -> v.toDate().time
    is Date -> v.time
    is Number -> v.toLong()
    is String -> v.trim().toLongOrNull()
    else -> null
}

private fun parseAttendanceDoc(
    doc: DocumentSnapshot,
    staffMasterMap: Map<String, AttendanceStaff> = emptyMap()
): AttendanceRecord? {
    return try {
        val docPath = runCatching { doc.reference.path }.getOrDefault("")
        if (docPath.contains("users/") || docPath.contains("app_users/") || docPath.contains("usage_logs/")) {
            return null
        }
        val rawDocId = doc.id
        val parentDocId = runCatching { doc.reference.parent.parent?.id }.getOrNull()?.takeIf { it.isNotBlank() }

        // attendance_logs stores the device employee id in `employee_id`. This is
        // the "master id" that must be resolved against attendance_employees.
        // Older/alternate field names are kept only as fallbacks.
        val rawEmpId = doc.anyString("employee_id")
            ?: doc.anyString("employeeId")
            ?: doc.anyString("masterId")
            ?: doc.anyString("master_id")
            ?: doc.anyString("employeeMasterId")
            ?: doc.anyString("employee_master_id")
            ?: doc.anyString("empId")
            ?: doc.anyString("staffId")
            ?: doc.anyString("code")
            ?: doc.anyString("userId")
            ?: doc.anyString("uid")
            ?: parentDocId
            ?: rawDocId

        val docName = doc.anyString("staffName")
            ?: doc.anyString("name")
            ?: doc.anyString("employeeName")
            ?: doc.anyString("userName")
            ?: doc.anyString("staff_name")
            ?: doc.anyString("user")
            ?: ""

        val docEmail = doc.anyString("email")
            ?: doc.anyString("staffEmail")
            ?: doc.anyString("userEmail")
            ?: ""

        // Map to Employee Name & Email via Employee ID from staffMasterMap
        val masterStaff = staffMasterMap[rawEmpId]
            ?: staffMasterMap.values.find {
                it.id.equals(rawEmpId, ignoreCase = true) ||
                (docName.isNotBlank() && it.name.equals(docName, ignoreCase = true)) ||
                (docEmail.isNotBlank() && it.email.equals(docEmail, ignoreCase = true))
            }

        val staffId = masterStaff?.id ?: rawEmpId
        val staffName = masterStaff?.name ?: docName.ifBlank { docEmail.substringBefore("@").ifBlank { staffId } }
        val staffEmail = masterStaff?.email?.ifBlank { docEmail } ?: docEmail

        // The attendance importer stores the punch as a Firestore Timestamp in
        // punch_time. Use it to derive the calendar day, rather than the hashed
        // log document ID, which cannot be matched by the daily view.
        val punchTime = doc.epochMillis("punch_time")
            ?: doc.epochMillis("punchTime")
            ?: doc.epochMillis("checkIn")
            ?: doc.epochMillis("timestamp")
            ?: 0L

        // Ignore punches from a mis-set device clock (the machine has produced
        // dates far in the future, e.g. 2119). Keep genuine history.
        val nowMillis = System.currentTimeMillis()
        if (punchTime > nowMillis + 86_400_000L) {
            return null
        }

        val rawDate = doc.anyString("date")
            ?: doc.anyString("dateKey")
            ?: doc.anyString("day")
            ?: doc.anyString("attendanceDate")
            ?: doc.anyString("punch_time_local")
            ?: rawDocId

        val dateKey = if (punchTime > 0) {
            SimpleDateFormat("yyyy_MM_dd", Locale.getDefault()).format(Date(punchTime))
        } else {
            parseAnyDateToKey(rawDate)
        }

        val uniqueId = when {
            docPath.isNotBlank() && staffId.isNotBlank() -> "${docPath}_${staffId}_${dateKey}"
            docPath.isNotBlank() -> "${docPath}_${rawDocId}_${dateKey}"
            staffId.isNotBlank() && staffId != rawDocId -> "${rawDocId}_${staffId}_${dateKey}"
            else -> "${rawDocId}_${staffEmail.ifBlank { staffName }}_${dateKey}"
        }

        val lastActive = punchTime
            .takeIf { it > 0 }
            ?: doc.epochMillis("last_active")
            ?: doc.epochMillis("timestamp")
            ?: doc.epochMillis("time")
            ?: doc.epochMillis("checkIn")
            ?: doc.anyLong("last_active")
            ?: doc.anyLong("ts")
            ?: 0L

        // In the eSSL/pyzk feed, punch=0 is a check-in while 1/3/5 are outs
        // (4 is overtime-in). Use punch_time for whichever side this log is.
        val punchKind = doc.anyLong("punch")
        val isCheckOut = punchKind == 1L || punchKind == 3L || punchKind == 5L

        val inTime = doc.anyString("inTime")
            ?: doc.anyString("checkIn")
            ?: doc.anyString("timeIn")
            ?: if (!isCheckOut && lastActive > 0) SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(lastActive)) else ""

        val outTime = doc.anyString("outTime")
            ?: doc.anyString("checkOut")
            ?: doc.anyString("timeOut")
            ?: if (isCheckOut && lastActive > 0) SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(lastActive)) else ""

        // status is numeric in the importer's data; anyString() converts it safely.
        val status = doc.anyString("status")
            ?: doc.anyString("attendanceStatus")
            ?: "Present"

        val remarks = doc.anyString("remarks")
            ?: doc.anyString("note")
            ?: doc.anyString("description")
            ?: ""

        val locationName = doc.anyString("location")
            ?: doc.anyString("locationName")
            ?: doc.anyString("place")
            ?: ""

        val lat = doc.anyDouble("latitude") ?: doc.anyDouble("lat") ?: 0.0
        val lon = doc.anyDouble("longitude") ?: doc.anyDouble("lon") ?: doc.anyDouble("lng") ?: 0.0

        val deviceName = doc.anyString("deviceName") ?: doc.anyString("device").orEmpty()
        val appVersion = doc.anyString("appVersion").orEmpty()
        val searchCount = doc.anyLong("search_count") ?: doc.anyLong("searchCount") ?: 0L
        val totalClicks = doc.anyLong("total_clicks") ?: doc.anyLong("totalClicks") ?: 0L

        val buttonClicks = (doc.get("button_clicks") as? Map<*, *>)
            ?.map { it.key.toString() to ((it.value as? Number)?.toLong() ?: 0L) }
            ?.toMap().orEmpty()

        AttendanceRecord(
            id = uniqueId,
            staffId = staffId,
            staffName = staffName,
            staffEmail = staffEmail,
            dateKey = dateKey,
            lastActive = lastActive,
            inTime = inTime,
            outTime = outTime,
            status = status,
            remarks = remarks,
            locationName = locationName,
            latitude = lat,
            longitude = lon,
            deviceName = deviceName,
            appVersion = appVersion,
            searchCount = searchCount,
            totalClicks = totalClicks,
            buttonClicks = buttonClicks
        )
    } catch (e: Exception) {
        null
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminScreen(onBack: () -> Unit, initialTab: Int = 0) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val auth = FirebaseAuth.getInstance()
    val db = remember { FirebaseFirestore.getInstance() }
    val storage = remember { Firebase.storage.reference.child("brochures") }
    val dateFormat = remember { SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()) }
    val timeFormat = remember { SimpleDateFormat("hh:mm a", Locale.getDefault()) }

    var allowed by remember { mutableStateOf(auth.currentUser?.email.equals(ADMIN_EMAIL, ignoreCase = true)) }
    var loadingUsers by remember { mutableStateOf(true) }
    var users by remember { mutableStateOf<List<UserActivity>>(emptyList()) }
    var statusText by remember { mutableStateOf<String?>(null) }
    var selectedTab by remember { mutableIntStateOf(initialTab.coerceIn(0, 3)) } // 0: Users, 1: Attendance, 2: Brochures, 3: Sync Status
    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var selectedFileName by remember { mutableStateOf("") }
    var brochureName by remember { mutableStateOf("") }
    var brochureDescription by remember { mutableStateOf("") }
    var brochureDate by remember { mutableStateOf("") }
    var brochures by remember { mutableStateOf<List<Brochure>>(emptyList()) }
    var loadingBrochures by remember { mutableStateOf(false) }
    var editingBrochure by remember { mutableStateOf<Brochure?>(null) }
    var uploading by remember { mutableStateOf(false) }

    // Independent Attendance Master & Logs State
    var attendanceList by remember { mutableStateOf<List<AttendanceRecord>>(emptyList()) }
    var attendanceStaffMaster by remember { mutableStateOf<List<AttendanceStaff>>(emptyList()) }
    var attendanceDevices by remember { mutableStateOf<List<AttendanceDevice>>(emptyList()) }
    var machineSyncState by remember { mutableStateOf<String?>(null) }
    var loadingAttendance by remember { mutableStateOf(false) }
    var attendanceViewMode by remember { mutableIntStateOf(0) } // 0: Daily, 1: Weekly, 2: Monthly
    var selectedDailyDateKey by remember { mutableStateOf(getTodayDateKey()) }
    var selectedWeekStartKey by remember { mutableStateOf(getStartOfWeekDateKey(getTodayDateKey())) }
    var selectedMonthKey by remember { mutableStateOf(getMonthKey(getTodayDateKey())) }

    fun loadAttendanceRecords(forceFullSync: Boolean = false) {
        loadingAttendance = true
        scope.launch(Dispatchers.IO) {
            try {
                // 0. Load Local Disk Cache First for 0ms Instant UI Load
                val localCache = AttendanceLocalStore.load(context)
                if (localCache.attendanceList.isNotEmpty() || localCache.staffMaster.isNotEmpty()) {
                    val formattedCacheTime = if (localCache.lastSyncAt > 0) {
                        SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date(localCache.lastSyncAt))
                    } else null

                    withContext(Dispatchers.Main) {
                        attendanceList = localCache.attendanceList.map { c ->
                            AttendanceRecord(
                                id = c.id,
                                staffId = c.staffId,
                                staffName = c.staffName,
                                staffEmail = c.staffEmail,
                                dateKey = c.dateKey,
                                lastActive = c.lastActive,
                                inTime = c.inTime,
                                outTime = c.outTime,
                                status = c.status,
                                remarks = c.remarks,
                                locationName = c.locationName,
                                latitude = c.latitude,
                                longitude = c.longitude,
                                deviceName = c.deviceName,
                                appVersion = c.appVersion,
                                searchCount = c.searchCount,
                                totalClicks = c.totalClicks
                            )
                        }
                        attendanceStaffMaster = localCache.staffMaster.map { s ->
                            AttendanceStaff(id = s.id, name = s.name, email = s.email, role = s.role)
                        }
                        attendanceDevices = localCache.devices.map { d ->
                            AttendanceDevice(id = d.id, name = d.name, location = d.location, status = d.status, lastActive = d.lastActive)
                        }
                        if (formattedCacheTime != null) {
                            machineSyncState = "Last Synced: $formattedCacheTime"
                        }
                    }
                }

                // 1. Fetch Last Machine/Server Sync State from attendance_sync_state -> last_sync_at
                var serverLastSyncAt = 0L
                runCatching {
                    val syncSnap = db.collection("attendance_sync_state").get().await()
                    for (doc in syncSnap.documents) {
                        val ts = doc.epochMillis("last_sync_at")
                            ?: doc.epochMillis("lastSync")
                            ?: doc.epochMillis("timestamp")
                            ?: doc.epochMillis("last_sync")
                            ?: doc.epochMillis("last_attendance_at")
                            ?: 0L
                        if (ts > serverLastSyncAt) {
                            serverLastSyncAt = ts
                        }
                    }
                }

                val hasLocalData = localCache.attendanceList.isNotEmpty()
                val isServerUnchanged = serverLastSyncAt > 0 && serverLastSyncAt <= localCache.lastSyncAt

                // 2. Smart Check: If server has no new syncs since last pull, DO NOT re-download logs!
                if (hasLocalData && isServerUnchanged && !forceFullSync) {
                    val formatted = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date(localCache.lastSyncAt))
                    withContext(Dispatchers.Main) {
                        machineSyncState = "Last Synced: $formatted"
                        loadingAttendance = false
                    }
                    return@launch
                }

                // 3. Download fresh logs from Firestore if server has new data or forced refresh
                val recordMap = mutableMapOf<String, AttendanceRecord>()
                val staffMasterMap = mutableMapOf<String, AttendanceStaff>()
                val deviceList = mutableListOf<AttendanceDevice>()

                runCatching {
                    val empSnap = db.collection("attendance_employees").get().await()
                    for (doc in empSnap.documents) {
                        val masterId = doc.anyString("masterId") ?: doc.anyString("master_id") ?: doc.id
                        val empId = doc.anyString("employee_id")
                            ?: doc.anyString("empId")
                            ?: doc.anyString("employeeId")
                            ?: doc.anyString("staffId")
                            ?: doc.anyString("code")
                            ?: doc.anyString("userId")
                            ?: masterId

                        val empName = doc.anyString("name")
                            ?: doc.anyString("empName")
                            ?: doc.anyString("employeeName")
                            ?: doc.anyString("staffName")
                            ?: doc.anyString("userName")
                            ?: doc.anyString("email")?.substringBefore("@")
                            ?: empId

                        val empEmail = doc.anyString("email") ?: doc.anyString("staffEmail") ?: doc.anyString("empEmail") ?: ""
                        val role = doc.anyString("role") ?: doc.anyString("designation") ?: doc.anyString("department") ?: doc.anyString("dept") ?: ""

                        if (empId.isNotBlank() || masterId.isNotBlank()) {
                            val employee = AttendanceStaff(
                                id = empId.ifBlank { masterId },
                                name = empName,
                                email = empEmail,
                                role = role
                            )
                            listOf(
                                doc.id,
                                masterId,
                                empId,
                                doc.anyString("employee_id"),
                                doc.anyString("employeeId"),
                                doc.anyString("staffId"),
                                doc.anyString("code"),
                                doc.anyString("userId")
                            ).filterNotNull().filter { it.isNotBlank() }
                                .forEach { staffMasterMap[it] = employee }
                        }
                    }
                }

                // Fetch Device Details
                runCatching {
                    val devSnap = db.collection("attendance_devices").get().await()
                    for (doc in devSnap.documents) {
                        val devId = doc.anyString("deviceId") ?: doc.anyString("id") ?: doc.id
                        val devName = doc.anyString("name") ?: doc.anyString("deviceName") ?: devId
                        val loc = doc.anyString("location") ?: doc.anyString("place") ?: ""
                        val status = doc.anyString("status") ?: doc.anyString("state") ?: "Online"
                        val lastAct = doc.epochMillis("lastActive") ?: doc.epochMillis("last_sync_at") ?: doc.epochMillis("timestamp") ?: 0L
                        deviceList.add(AttendanceDevice(id = devId, name = devName, location = loc, status = status, lastActive = lastAct))
                    }
                }

                // Fetch Attendance Logs
                runCatching {
                    val logSnap = db.collection("attendance_logs").get().await()
                    for (doc in logSnap.documents) {
                        val rec = parseAttendanceDoc(doc, staffMasterMap)
                        if (rec != null) {
                            val recKey = "${rec.staffId}_${rec.dateKey}"
                            val existing = recordMap[recKey]
                            if (existing == null) {
                                recordMap[recKey] = rec
                            } else {
                                val earlier = if (existing.lastActive <= rec.lastActive) existing else rec
                                val later = if (existing.lastActive >= rec.lastActive) existing else rec
                                recordMap[recKey] = later.copy(
                                    inTime = earlier.inTime.ifBlank { later.inTime },
                                    outTime = later.outTime.ifBlank { earlier.outTime },
                                    lastActive = maxOf(existing.lastActive, rec.lastActive)
                                )
                            }
                        }
                    }
                }

                val fetchedRecords = recordMap.values.sortedByDescending { it.lastActive }

                // Populate Attendance Master staff list independently from records if missing
                for (rec in fetchedRecords) {
                    val key = if (rec.staffId.isNotBlank() && rec.staffId != rec.dateKey) rec.staffId else rec.staffName.ifBlank { rec.id }
                    if (key.isNotBlank() && !staffMasterMap.containsKey(key)) {
                        staffMasterMap[key] = AttendanceStaff(
                            id = key,
                            name = rec.staffName.ifBlank { key },
                            email = rec.staffEmail
                        )
                    }
                }

                val finalStaffMaster = staffMasterMap.values
                    .distinctBy { it.id.trim().lowercase(Locale.ROOT) }
                    .sortedBy { it.name }

                val finalSyncTime = if (serverLastSyncAt > 0) serverLastSyncAt else System.currentTimeMillis()

                // Save to Local Disk Cache / Database
                AttendanceLocalStore.save(
                    context,
                    LocalAttendanceData(
                        lastSyncAt = finalSyncTime,
                        staffMaster = finalStaffMaster.map { CachedStaff(it.id, it.name, it.email, it.role) },
                        attendanceList = fetchedRecords.map { r ->
                            CachedRecord(
                                id = r.id,
                                staffId = r.staffId,
                                staffName = r.staffName,
                                staffEmail = r.staffEmail,
                                dateKey = r.dateKey,
                                lastActive = r.lastActive,
                                inTime = r.inTime,
                                outTime = r.outTime,
                                status = r.status,
                                remarks = r.remarks,
                                locationName = r.locationName,
                                latitude = r.latitude,
                                longitude = r.longitude,
                                deviceName = r.deviceName,
                                appVersion = r.appVersion,
                                searchCount = r.searchCount,
                                totalClicks = r.totalClicks
                            )
                        },
                        devices = deviceList.map { CachedDevice(it.id, it.name, it.location, it.status, it.lastActive) }
                    )
                )

                val formattedDate = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date(finalSyncTime))

                withContext(Dispatchers.Main) {
                    attendanceList = fetchedRecords
                    attendanceStaffMaster = finalStaffMaster
                    attendanceDevices = deviceList
                    machineSyncState = "Last Synced: $formattedDate"
                }
            } catch (e: Exception) {
                // Ignore background errors
            } finally {
                withContext(Dispatchers.Main) {
                    loadingAttendance = false
                }
            }
        }
    }

    fun loadUsers() {
        loadingUsers = true
        scope.launch {
            try {
                val result = db.collection("users")
                    .orderBy("lastLogin", Query.Direction.DESCENDING)
                    .get()
                    .await()

                val userList = result.documents.map { doc ->
                    val statsResult = db.collection("usage_logs")
                        .document(doc.id)
                        .collection("daily_stats")
                        .orderBy("last_active", Query.Direction.DESCENDING)
                        .limit(30)
                        .get()
                        .await()

                    val dailyStats = statsResult.documents.map { sDoc ->
                        DailyStats(
                            date = sDoc.getString("date") ?: sDoc.id,
                            searchCount = sDoc.getLong("search_count") ?: 0L,
                            totalClicks = sDoc.getLong("total_clicks") ?: 0L,
                            lastActive = sDoc.getTimestamp("last_active")?.toDate()?.time ?: 0L,
                            latitude = sDoc.getDouble("latitude") ?: 0.0,
                            longitude = sDoc.getDouble("longitude") ?: 0.0,
                            appVersion = sDoc.getString("appVersion").orEmpty(),
                            buttonClicks = (sDoc.get("button_clicks") as? Map<*, *>)
                                ?.map { it.key.toString() to (it.value as? Long ?: 0L) }
                                ?.toMap().orEmpty()
                        )
                    }

                    UserActivity(
                        id = doc.id,
                        name = doc.getString("name").orEmpty(),
                        email = doc.getString("email").orEmpty(),
                        whitelisted = doc.getBoolean("whitelisted") == true,
                        isAdmin = doc.getBoolean("isAdmin") == true,
                        role = doc.getString("role").orEmpty(),
                        lastLogin = doc.getLong("lastLogin") ?: 0L,
                        last30Days = (doc.get("last30Days") as? List<*>)
                            ?.mapNotNull { it as? String }
                            ?.sortedDescending()
                            .orEmpty(),
                        device = doc.getString("device").orEmpty(),
                        androidVer = doc.getString("androidVer").orEmpty(),
                        appVersion = doc.getString("appVersion").orEmpty(),
                        dailyStats = dailyStats
                    )
                }
                users = userList
            } catch (e: Exception) {
                statusText = "Could not load users: ${e.message}"
            } finally {
                loadingUsers = false
            }
        }
    }

    fun clearBrochureForm() {
        editingBrochure = null
        selectedUri = null
        selectedFileName = ""
        brochureName = ""
        brochureDescription = ""
        brochureDate = ""
    }

    fun loadBrochures() {
        loadingBrochures = true
        db.collection("brochures")
            .orderBy("name")
            .get()
            .addOnSuccessListener { result ->
                brochures = result.documents.mapNotNull { doc ->
                    doc.toObject(Brochure::class.java)?.copy(id = doc.id)
                }
                loadingBrochures = false
            }
            .addOnFailureListener {
                statusText = "Could not load brochures: ${it.message}"
                loadingBrochures = false
            }
    }

    fun editBrochure(brochure: Brochure) {
        editingBrochure = brochure
        selectedUri = null
        selectedFileName = ""
        brochureName = brochure.name
        brochureDescription = brochure.description
        brochureDate = brochure.brochureDate
        selectedTab = 2
    }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            selectedUri = uri
            selectedFileName = displayNameForUri(context, uri)
            if (brochureName.isBlank()) {
                brochureName = selectedFileName.substringBeforeLast('.', selectedFileName)
            }
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }
    }

    LaunchedEffect(Unit) {
        val user = auth.currentUser
        allowed = user?.email.equals(ADMIN_EMAIL, ignoreCase = true)
        if (allowed && user != null) {
            db.collection("users").document(user.uid).set(
                mapOf(
                    "email" to (user.email ?: ADMIN_EMAIL),
                    "name" to (user.displayName ?: "Administrator"),
                    "whitelisted" to true,
                    "role" to "administrator",
                    "isAdmin" to true
                ),
                SetOptions.merge()
            )
            loadUsers()
            loadAttendanceRecords()
            loadBrochures()
        } else {
            loadingUsers = false
            loadingBrochures = false
            loadingAttendance = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Administrator") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        if (!allowed) {
            Column(
                modifier = Modifier
                    .padding(padding)
                    .padding(16.dp)
                    .fillMaxSize()
            ) {
                Text("Administrator access is not enabled for this account.")
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Users") }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Attendance") }
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = { Text("Brochures") }
                )
                Tab(
                    selected = selectedTab == 3,
                    onClick = { selectedTab = 3 },
                    text = { Text("Sync Status") }
                )
            }

            statusText?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (it.contains("failed", true) || it.contains("could not", true)) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            when (selectedTab) {
                0 -> {
                    var pendingOnly by remember { mutableStateOf(false) }
                    val pendingCount = remember(users) { users.count { !it.whitelisted } }
                    val filteredUsers = remember(users, pendingOnly) {
                        if (pendingOnly) users.filter { !it.whitelisted } else users
                    }

                    Column(Modifier.fillMaxSize()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("User activity", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                if (pendingCount > 0) {
                                    FilterChip(
                                        selected = pendingOnly,
                                        onClick = { pendingOnly = !pendingOnly },
                                        label = { Text("Pending ($pendingCount)", fontSize = 11.sp) },
                                        modifier = Modifier.height(28.dp)
                                    )
                                }
                            }
                            TextButton(onClick = { loadUsers() }) {
                                Text("Refresh")
                            }
                        }

                        if (loadingUsers) {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        }

                        LazyColumn(
                            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(filteredUsers, key = { it.id }) { user ->
                                UserActivityCard(
                                    user = user,
                                    dateFormat = dateFormat,
                                    onToggleWhitelist = { userId, isWhitelisted ->
                                        scope.launch {
                                            try {
                                                db.collection("users").document(userId)
                                                    .update("whitelisted", isWhitelisted)
                                                    .await()
                                                users = users.map {
                                                    if (it.id == userId) it.copy(whitelisted = isWhitelisted) else it
                                                }
                                                statusText = "User ${user.email} ${if (isWhitelisted) "whitelisted" else "restricted"}"
                                            } catch (e: Exception) {
                                                statusText = "Whitelist update failed: ${e.message}"
                                            }
                                        }
                                    },
                                    onToggleAdmin = { userId, isAdminValue ->
                                        if (user.email.equals(ADMIN_EMAIL, ignoreCase = true) && !isAdminValue) {
                                            statusText = "Cannot demote primary administrator."
                                        } else {
                                            scope.launch {
                                                try {
                                                    val newRole = if (isAdminValue) "administrator" else "staff"
                                                    db.collection("users").document(userId)
                                                        .update(mapOf(
                                                            "isAdmin" to isAdminValue,
                                                            "role" to newRole
                                                        ))
                                                        .await()
                                                    users = users.map {
                                                        if (it.id == userId) it.copy(isAdmin = isAdminValue, role = newRole) else it
                                                    }
                                                    statusText = "User ${user.email} role set to $newRole"
                                                } catch (e: Exception) {
                                                    statusText = "Admin update failed: ${e.message}"
                                                }
                                            }
                                        }
                                    }
                                )
                            }
                        }
                    }
                }

                1 -> {
                    AttendanceTabContent(
                        attendanceStaffMaster = attendanceStaffMaster,
                        attendanceList = attendanceList,
                        attendanceDevices = attendanceDevices,
                        machineSyncState = machineSyncState,
                        loading = loadingAttendance,
                        timeFormat = timeFormat,
                        onRefresh = { loadAttendanceRecords() },
                        selectedViewMode = attendanceViewMode,
                        onViewModeChange = { attendanceViewMode = it },
                        selectedDailyDateKey = selectedDailyDateKey,
                        onDailyDateChange = { selectedDailyDateKey = it },
                        selectedWeekStartKey = selectedWeekStartKey,
                        onWeekStartChange = { selectedWeekStartKey = it },
                        selectedMonthKey = selectedMonthKey,
                        onMonthChange = { selectedMonthKey = it }
                    )
                }

                2 -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        item {
                            Text("Curate brochures", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Upload a PDF or image. It will be saved to Firebase Storage and listed in Firestore for every user.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        item {
                            OutlinedTextField(
                                value = brochureName,
                                onValueChange = { brochureName = it },
                                label = { Text("Brochure title") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        item {
                            OutlinedTextField(
                                value = brochureDescription,
                                onValueChange = { brochureDescription = it },
                                label = { Text("Description") },
                                minLines = 2,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        item {
                            OutlinedTextField(
                                value = brochureDate,
                                onValueChange = { brochureDate = it },
                                label = { Text("Date") },
                                placeholder = { Text("Example: 2026-04-15") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        item {
                            Button(
                                onClick = { picker.launch(arrayOf("application/pdf", "image/*")) },
                                enabled = !uploading,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                val currentFile = editingBrochure?.file?.takeIf { it.isNotBlank() }
                                val label = when {
                                    selectedFileName.isNotBlank() -> selectedFileName
                                    currentFile != null -> "Replace attachment: $currentFile"
                                    else -> "Choose PDF or image"
                                }
                                Text(label)
                            }
                        }

                        item {
                            Button(
                                onClick = {
                                    val title = brochureName.trim()
                                    val existing = editingBrochure
                                    val pickedUri = selectedUri

                                    if (title.isBlank()) {
                                        statusText = "Enter a brochure title."
                                        return@Button
                                    }

                                    if (existing == null && pickedUri == null) {
                                        statusText = "Choose a PDF or image."
                                        return@Button
                                    }

                                    uploading = true
                                    statusText = if (existing == null) "Uploading brochure..." else "Saving brochure..."
                                    scope.launch {
                                        try {
                                            var storageName = existing?.file.orEmpty()
                                            var contentType = existing?.contentType ?: "application/pdf"

                                            if (pickedUri != null) {
                                                val originalName = selectedFileName.ifBlank { "brochure" }
                                                val safeName = safeStorageName(originalName)
                                                val newStorageName = "${System.currentTimeMillis()}-$safeName"
                                                contentType = context.contentResolver.getType(pickedUri) ?: "application/octet-stream"

                                                storage.child(newStorageName).putFile(pickedUri).await()

                                                if (existing?.file?.isNotBlank() == true) {
                                                    runCatching { storage.child(existing.file).delete().await() }
                                                }

                                                storageName = newStorageName
                                            }

                                            val payload = mapOf(
                                                "name" to title,
                                                "description" to brochureDescription.trim(),
                                                "brochureDate" to brochureDate.trim(),
                                                "file" to storageName,
                                                "contentType" to contentType,
                                                "uploadedBy" to (auth.currentUser?.email ?: ADMIN_EMAIL),
                                                "ts" to System.currentTimeMillis()
                                            )

                                            if (existing == null) {
                                                db.collection("brochures").add(payload).await()
                                            } else {
                                                db.collection("brochures").document(existing.id).set(payload, SetOptions.merge()).await()
                                            }

                                            statusText = "Brochure synced successfully."
                                            clearBrochureForm()
                                            loadBrochures()
                                        } catch (e: Exception) {
                                            statusText = "Upload failed: ${e.message}"
                                        } finally {
                                            uploading = false
                                        }
                                    }
                                },
                                enabled = !uploading,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(if (uploading) "Syncing..." else if (editingBrochure == null) "Add brochure" else "Save brochure")
                            }
                        }

                        if (editingBrochure != null) {
                            item {
                                TextButton(onClick = { clearBrochureForm() }, enabled = !uploading) {
                                    Text("Cancel edit")
                                }
                            }
                        }

                        if (uploading || loadingBrochures) {
                            item {
                                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                            }
                        }

                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Published brochures", style = MaterialTheme.typography.titleMedium)
                                TextButton(onClick = { loadBrochures() }, enabled = !loadingBrochures) {
                                    Text("Refresh")
                                }
                            }
                        }

                        items(brochures, key = { it.id }) { brochure ->
                            AdminBrochureCard(
                                brochure = brochure,
                                onEdit = { editBrochure(brochure) },
                                onDelete = {
                                    uploading = true
                                    statusText = "Deleting brochure..."
                                    scope.launch {
                                        try {
                                            if (brochure.file.isNotBlank()) {
                                                runCatching { storage.child(brochure.file).delete().await() }
                                            }
                                            db.collection("brochures").document(brochure.id).delete().await()
                                            if (editingBrochure?.id == brochure.id) {
                                                clearBrochureForm()
                                            }
                                            statusText = "Brochure deleted."
                                            loadBrochures()
                                        } catch (e: Exception) {
                                            statusText = "Delete failed: ${e.message}"
                                        } finally {
                                            uploading = false
                                        }
                                    }
                                }
                            )
                        }
                    }
                }

                3 -> {
                    SyncStatusScreen()
                }
            }
        }
    }
}

@Composable
private fun AttendanceTabContent(
    attendanceStaffMaster: List<AttendanceStaff>,
    attendanceList: List<AttendanceRecord>,
    attendanceDevices: List<AttendanceDevice>,
    machineSyncState: String?,
    loading: Boolean,
    timeFormat: SimpleDateFormat,
    onRefresh: () -> Unit,
    selectedViewMode: Int,
    onViewModeChange: (Int) -> Unit,
    selectedDailyDateKey: String,
    onDailyDateChange: (String) -> Unit,
    selectedWeekStartKey: String,
    onWeekStartChange: (String) -> Unit,
    selectedMonthKey: String,
    onMonthChange: (String) -> Unit
) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Compact Top Bar with Sync Info & Mode Switcher
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Attendance Logs", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                if (!machineSyncState.isNullOrBlank()) {
                    Text(
                        machineSyncState,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 11.sp
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                IconButton(onClick = {
                    val targetKey = normalizeDateKey(selectedDailyDateKey)
                    val pCount = attendanceList.count { it.dateKey == targetKey }
                    val summaryText = "Daily Attendance Summary (${formatDateKey(selectedDailyDateKey)}):\nPresent: $pCount / ${attendanceStaffMaster.size}\nGenerated via Price List Staff App"
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_SUBJECT, "Attendance Summary")
                        putExtra(Intent.EXTRA_TEXT, summaryText)
                    }
                    context.startActivity(Intent.createChooser(shareIntent, "Share Summary"))
                }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Share, contentDescription = "Share Summary", modifier = Modifier.size(18.dp))
                }
                FilterChip(
                    selected = selectedViewMode == 0,
                    onClick = { onViewModeChange(0) },
                    label = { Text("Daily", fontSize = 11.sp) },
                    modifier = Modifier.height(28.dp)
                )
                FilterChip(
                    selected = selectedViewMode == 1,
                    onClick = { onViewModeChange(1) },
                    label = { Text("Weekly", fontSize = 11.sp) },
                    modifier = Modifier.height(28.dp)
                )
                FilterChip(
                    selected = selectedViewMode == 2,
                    onClick = { onViewModeChange(2) },
                    label = { Text("Monthly", fontSize = 11.sp) },
                    modifier = Modifier.height(28.dp)
                )
                IconButton(onClick = onRefresh, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh", modifier = Modifier.size(18.dp))
                }
            }
        }

        if (attendanceDevices.isNotEmpty()) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(attendanceDevices, key = { it.id }) { device ->
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = MaterialTheme.shapes.extraSmall
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                Icons.Default.Devices,
                                contentDescription = null,
                                modifier = Modifier.size(12.dp),
                                tint = if (device.status.equals("Online", true)) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                            )
                            Text(
                                text = "${device.name}${if (device.location.isNotBlank()) " (${device.location})" else ""}",
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 10.sp
                            )
                        }
                    }
                }
            }
        }

        if (loading) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth().height(2.dp))
        }

        when (selectedViewMode) {
            0 -> DailyAttendanceView(
                attendanceStaffMaster = attendanceStaffMaster,
                attendanceList = attendanceList,
                selectedDailyDateKey = selectedDailyDateKey,
                onDailyDateChange = onDailyDateChange,
                timeFormat = timeFormat
            )
            1 -> WeeklyAttendanceView(
                attendanceStaffMaster = attendanceStaffMaster,
                attendanceList = attendanceList,
                selectedWeekStartKey = selectedWeekStartKey,
                onWeekStartChange = onWeekStartChange,
                timeFormat = timeFormat
            )
            2 -> MonthlyAttendanceView(
                attendanceStaffMaster = attendanceStaffMaster,
                attendanceList = attendanceList,
                selectedMonthKey = selectedMonthKey,
                onMonthChange = onMonthChange
            )
        }
    }
}

private data class UnifiedDailyAttendance(
    val id: String,
    val staffId: String,
    val staffName: String,
    val staffEmail: String,
    val isPresent: Boolean,
    val record: AttendanceRecord? = null,
    val punchInTime: Long = 0L
)

@Composable
private fun DailyAttendanceView(
    attendanceStaffMaster: List<AttendanceStaff>,
    attendanceList: List<AttendanceRecord>,
    selectedDailyDateKey: String,
    onDailyDateChange: (String) -> Unit,
    timeFormat: SimpleDateFormat
) {
    val targetDateKey = remember(selectedDailyDateKey) { normalizeDateKey(selectedDailyDateKey) }
    val presentRecords = remember(attendanceList, targetDateKey) {
        attendanceList.filter { it.dateKey == targetDateKey }
    }

    // Sort option: 0 = Name (A-Z), 1 = Punched In Time, 2 = Status (Present first)
    var sortOption by remember { mutableIntStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }
    var statusFilter by remember { mutableIntStateOf(0) } // 0 = All, 1 = Present, 2 = Absent

    // Combine Master Employees + Today's Logs into a single Unified Attendance List
    val unifiedList = remember(attendanceStaffMaster, presentRecords, sortOption, searchQuery, statusFilter) {
        val mappedList = mutableListOf<UnifiedDailyAttendance>()
        val processedRecordIds = mutableSetOf<String>()

        // 1. Process all Master Employees from attendance_employees
        for (staff in attendanceStaffMaster) {
            val rec = presentRecords.find {
                it.staffId.equals(staff.id, ignoreCase = true) ||
                (it.staffName.isNotBlank() && it.staffName.equals(staff.name, ignoreCase = true))
            }
            if (rec != null) {
                processedRecordIds.add(rec.id)
                mappedList.add(
                    UnifiedDailyAttendance(
                        id = "pres_${staff.id}_${rec.id}",
                        staffId = staff.id,
                        staffName = staff.name.ifBlank { rec.staffName },
                        staffEmail = staff.email.ifBlank { rec.staffEmail },
                        isPresent = true,
                        record = rec,
                        punchInTime = rec.lastActive
                    )
                )
            } else {
                mappedList.add(
                    UnifiedDailyAttendance(
                        id = "abs_${staff.id}",
                        staffId = staff.id,
                        staffName = staff.name,
                        staffEmail = staff.email,
                        isPresent = false,
                        record = null,
                        punchInTime = 0L
                    )
                )
            }
        }

        // 2. Include any additional records from attendance_logs not in master
        for (rec in presentRecords) {
            if (rec.id !in processedRecordIds) {
                mappedList.add(
                    UnifiedDailyAttendance(
                        id = "extra_${rec.id}",
                        staffId = rec.staffId,
                        staffName = rec.staffName.ifBlank { rec.staffId },
                        staffEmail = rec.staffEmail,
                        isPresent = true,
                        record = rec,
                        punchInTime = rec.lastActive
                    )
                )
            }
        }

        // 3. Filter by search query & status
        val filtered = mappedList.filter { item ->
            val matchesQuery = searchQuery.isBlank() ||
                item.staffName.contains(searchQuery, ignoreCase = true) ||
                item.staffId.contains(searchQuery, ignoreCase = true)
            val matchesStatus = when (statusFilter) {
                1 -> item.isPresent
                2 -> !item.isPresent
                else -> true
            }
            matchesQuery && matchesStatus
        }

        // 4. Apply selected sort option
        when (sortOption) {
            0 -> filtered.sortedBy { it.staffName.lowercase(Locale.getDefault()) }
            1 -> filtered.sortedWith(
                compareByDescending<UnifiedDailyAttendance> { it.isPresent }
                    .thenBy { if (it.punchInTime > 0) it.punchInTime else Long.MAX_VALUE }
                    .thenBy { it.staffName.lowercase(Locale.getDefault()) }
            )
            2 -> filtered.sortedWith(
                compareByDescending<UnifiedDailyAttendance> { it.isPresent }
                    .thenBy { it.staffName.lowercase(Locale.getDefault()) }
            )
            else -> filtered
        }
    }

    val presentCount = remember(presentRecords) { presentRecords.size }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        // Ultra-Compact Date Navigation + Sort Controls Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
            shape = RoundedCornerShape(8.dp)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = { onDailyDateChange(addDaysToDateKey(selectedDailyDateKey, -1)) },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(Icons.Default.ChevronLeft, contentDescription = "Prev", modifier = Modifier.size(16.dp))
                        }
                        Text(
                            formatShortDate(selectedDailyDateKey),
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                        IconButton(
                            onClick = { onDailyDateChange(addDaysToDateKey(selectedDailyDateKey, 1)) },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(Icons.Default.ChevronRight, contentDescription = "Next", modifier = Modifier.size(16.dp))
                        }
                        TextButton(
                            onClick = { onDailyDateChange(getTodayDateKey()) },
                            contentPadding = PaddingValues(horizontal = 2.dp),
                            modifier = Modifier.height(24.dp)
                        ) {
                            Text("Today", fontSize = 11.sp)
                        }
                    }

                    Text(
                        "Present: $presentCount / ${unifiedList.size}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 11.sp
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text("Filter:", style = MaterialTheme.typography.labelSmall, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    FilterChip(
                        selected = statusFilter == 0,
                        onClick = { statusFilter = 0 },
                        label = { Text("All", fontSize = 10.sp) },
                        modifier = Modifier.height(24.dp)
                    )
                    FilterChip(
                        selected = statusFilter == 1,
                        onClick = { statusFilter = 1 },
                        label = { Text("Present", fontSize = 10.sp) },
                        modifier = Modifier.height(24.dp)
                    )
                    FilterChip(
                        selected = statusFilter == 2,
                        onClick = { statusFilter = 2 },
                        label = { Text("Absent", fontSize = 10.sp) },
                        modifier = Modifier.height(24.dp)
                    )

                    Spacer(Modifier.weight(1f))

                    Text("Sort:", style = MaterialTheme.typography.labelSmall, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    FilterChip(
                        selected = sortOption == 0,
                        onClick = { sortOption = 0 },
                        label = { Text("Name", fontSize = 10.sp) },
                        modifier = Modifier.height(24.dp)
                    )
                    FilterChip(
                        selected = sortOption == 1,
                        onClick = { sortOption = 1 },
                        label = { Text("Time In", fontSize = 10.sp) },
                        modifier = Modifier.height(24.dp)
                    )
                }
            }
        }

        // Unified Dense Attendance Staff List
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (unifiedList.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Text(
                            "No attendance records found.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            } else {
                items(unifiedList, key = { it.id }) { item ->
                    if (item.isPresent && item.record != null) {
                        DailyAttendanceCard(record = item.record, timeFormat = timeFormat)
                    } else {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.15f)
                            ),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        item.staffName,
                                        fontWeight = FontWeight.SemiBold,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    if (item.staffEmail.isNotBlank()) {
                                        Text(
                                            item.staffEmail,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontSize = 10.sp
                                        )
                                    }
                                }
                                Surface(
                                    color = MaterialTheme.colorScheme.errorContainer,
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        "ABSENT",
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DailyAttendanceCard(
    record: AttendanceRecord,
    timeFormat: SimpleDateFormat
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(6.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        record.staffName,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    if (record.staffEmail.isNotBlank()) {
                        Text(
                            record.staffEmail,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 10.sp
                        )
                    }
                }

                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier.size(12.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            record.inTime.ifBlank { if (record.lastActive > 0) timeFormat.format(Date(record.lastActive)) else record.status },
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            if (record.locationName.isNotBlank() || record.remarks.isNotBlank()) {
                Text(
                    text = listOfNotNull(
                        record.locationName.takeIf { it.isNotBlank() }?.let { "Location: $it" },
                        record.remarks.takeIf { it.isNotBlank() }?.let { "Note: $it" }
                    ).joinToString(" | "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 10.sp
                )
            }
        }
    }
}

@Composable
private fun WeeklyAttendanceView(
    attendanceStaffMaster: List<AttendanceStaff>,
    attendanceList: List<AttendanceRecord>,
    selectedWeekStartKey: String,
    onWeekStartChange: (String) -> Unit,
    timeFormat: SimpleDateFormat
) {
    val weekDates = remember(selectedWeekStartKey) {
        (0..6).map { addDaysToDateKey(selectedWeekStartKey, it) }
    }
    val weekEndKey = weekDates.last()
    val weekSet = remember(weekDates) { weekDates.map { normalizeDateKey(it) }.toSet() }
    val weekRecords = remember(attendanceList, weekSet) {
        attendanceList.filter { it.dateKey in weekSet }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Week Navigation Bar
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { onWeekStartChange(addDaysToDateKey(selectedWeekStartKey, -7)) }) {
                    Icon(Icons.Default.ChevronLeft, contentDescription = "Previous Week")
                }

                Text(
                    "Week: ${formatShortDate(selectedWeekStartKey)} - ${formatShortDate(weekEndKey)}",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleSmall
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { onWeekStartChange(getStartOfWeekDateKey(getTodayDateKey())) }) {
                        Text("This Week")
                    }
                    IconButton(onClick = { onWeekStartChange(addDaysToDateKey(selectedWeekStartKey, 7)) }) {
                        Icon(Icons.Default.ChevronRight, contentDescription = "Next Week")
                    }
                }
            }
        }

        // Weekly Summary Metrics
        val activeStaffCount = remember(weekRecords) { weekRecords.map { if (it.staffId.isNotBlank()) it.staffId else it.staffName }.toSet().size }
        val totalAttendedDaysCount = remember(weekRecords) { weekRecords.map { "${it.staffId}_${it.dateKey}" }.toSet().size }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Active Staff", style = MaterialTheme.typography.labelSmall)
                    Text("$activeStaffCount / ${attendanceStaffMaster.size}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Total Days Attended", style = MaterialTheme.typography.labelSmall)
                    Text("$totalAttendedDaysCount", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Attendance Staff Cards with 7-Day Indicators
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(attendanceStaffMaster, key = { it.id }) { staff ->
                WeeklyUserAttendanceCard(
                    staff = staff,
                    weekDates = weekDates,
                    weekRecords = weekRecords,
                    timeFormat = timeFormat
                )
            }
        }
    }
}

@Composable
private fun WeeklyUserAttendanceCard(
    staff: AttendanceStaff,
    weekDates: List<String>,
    weekRecords: List<AttendanceRecord>,
    timeFormat: SimpleDateFormat
) {
    var expanded by remember { mutableStateOf(false) }
    val userLogs = remember(weekRecords, staff) {
        weekRecords.filter {
            it.staffId == staff.id ||
            it.staffName.equals(staff.name, ignoreCase = true) ||
            (staff.email.isNotBlank() && it.staffEmail.equals(staff.email, ignoreCase = true))
        }
    }
    val daysPresentCount = remember(userLogs) { userLogs.map { it.dateKey }.toSet().size }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        staff.name,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleSmall
                    )
                    if (staff.email.isNotBlank()) {
                        Text(
                            staff.email,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Surface(
                    color = if (daysPresentCount > 0) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        "$daysPresentCount / 7 Days",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (daysPresentCount > 0) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(onClick = { expanded = !expanded }) {
                    Icon(
                        if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = "Expand details"
                    )
                }
            }

            // 7 Days Chips Row
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(weekDates) { dateKey ->
                    val normKey = normalizeDateKey(dateKey)
                    val rec = userLogs.find { it.dateKey == normKey }
                    val isPresent = rec != null

                    Surface(
                        color = if (isPresent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                        contentColor = if (isPresent) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        shape = MaterialTheme.shapes.extraSmall,
                        border = if (!isPresent) CardDefaults.outlinedCardBorder() else null
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                formatShortDate(dateKey),
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 9.sp
                            )
                            Icon(
                                if (isPresent) Icons.Default.CheckCircle else Icons.Default.Cancel,
                                contentDescription = null,
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }
                }
            }

            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier.padding(top = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f))
                    if (userLogs.isEmpty()) {
                        Text(
                            "No attendance logged for this week.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        userLogs.sortedByDescending { it.lastActive }.forEach { log ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    formatDateKey(log.dateKey),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    "In: ${log.inTime.ifBlank { if (log.lastActive > 0) timeFormat.format(Date(log.lastActive)) else log.status }}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MonthlyAttendanceView(
    attendanceStaffMaster: List<AttendanceStaff>,
    attendanceList: List<AttendanceRecord>,
    selectedMonthKey: String,
    onMonthChange: (String) -> Unit
) {
    val normMonth = remember(selectedMonthKey) { selectedMonthKey.replace("-", "_") }
    val monthRecords = remember(attendanceList, normMonth) {
        attendanceList.filter { getMonthKey(it.dateKey) == normMonth }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Month Navigation Bar
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { onMonthChange(addMonthsToMonthKey(selectedMonthKey, -1)) }) {
                    Icon(Icons.Default.ChevronLeft, contentDescription = "Previous Month")
                }

                Text(
                    formatMonthKey(selectedMonthKey),
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleSmall
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { onMonthChange(getMonthKey(getTodayDateKey())) }) {
                        Text("Current Month")
                    }
                    IconButton(onClick = { onMonthChange(addMonthsToMonthKey(selectedMonthKey, 1)) }) {
                        Icon(Icons.Default.ChevronRight, contentDescription = "Next Month")
                    }
                }
            }
        }

        // Monthly Summary Metrics
        val activeStaffCount = remember(monthRecords) { monthRecords.map { if (it.staffId.isNotBlank()) it.staffId else it.staffName }.toSet().size }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Active Staff This Month", style = MaterialTheme.typography.labelSmall)
                    Text("$activeStaffCount / ${attendanceStaffMaster.size}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Total Attendance Logs", style = MaterialTheme.typography.labelSmall)
                    Text("${monthRecords.size}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Monthly Staff List
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(attendanceStaffMaster, key = { it.id }) { staff ->
                MonthlyUserAttendanceCard(
                    staff = staff,
                    monthRecords = monthRecords
                )
            }
        }
    }
}

@Composable
private fun MonthlyUserAttendanceCard(
    staff: AttendanceStaff,
    monthRecords: List<AttendanceRecord>
) {
    var expanded by remember { mutableStateOf(false) }
    val userMonthLogs = remember(monthRecords, staff) {
        monthRecords.filter {
            it.staffId == staff.id ||
            it.staffName.equals(staff.name, ignoreCase = true) ||
            (staff.email.isNotBlank() && it.staffEmail.equals(staff.email, ignoreCase = true))
        }.sortedBy { it.dateKey }
    }
    val presentDaysCount = remember(userMonthLogs) { userMonthLogs.map { it.dateKey }.toSet().size }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        staff.name,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleSmall
                    )
                    if (staff.email.isNotBlank()) {
                        Text(
                            staff.email,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Surface(
                    color = if (presentDaysCount > 0) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        "$presentDaysCount Days Present",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (presentDaysCount > 0) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(onClick = { expanded = !expanded }) {
                    Icon(
                        if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = "Expand details"
                    )
                }
            }

            // Attendance Progress Bar
            LinearProgressIndicator(
                progress = { (presentDaysCount / 30f).coerceAtMost(1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)
            )

            // Date Chips for Present Days
            if (userMonthLogs.isNotEmpty()) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(userMonthLogs) { log ->
                        val dayNum = log.dateKey.substringAfterLast('_').padStart(2, '0')
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = CircleShape
                        ) {
                            Text(
                                dayNum,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
            }

            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier.padding(top = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f))
                    if (userMonthLogs.isEmpty()) {
                        Text(
                            "No attendance recorded for this month.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        userMonthLogs.forEach { log ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    formatDateKey(log.dateKey),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    "In: ${log.inTime.ifBlank { log.status }}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun UserActivityCard(
    user: UserActivity,
    dateFormat: SimpleDateFormat,
    onToggleWhitelist: (String, Boolean) -> Unit,
    onToggleAdmin: (String, Boolean) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (user.whitelisted)
                MaterialTheme.colorScheme.surfaceVariant
            else
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
        )
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            user.name.ifBlank { "Unnamed user" },
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium
                        )
                        if (user.isAdmin) {
                            Surface(
                                color = MaterialTheme.colorScheme.primary,
                                shape = MaterialTheme.shapes.extraSmall,
                                modifier = Modifier.padding(start = 8.dp)
                            ) {
                                Text(
                                    "ADMIN",
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            }
                        }
                    }
                    Text(user.email, style = MaterialTheme.typography.bodySmall)
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            if (user.whitelisted) "Whitelisted" else "Restricted",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (user.whitelisted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                        )
                        Switch(
                            checked = user.whitelisted,
                            onCheckedChange = { onToggleWhitelist(user.id, it) },
                            modifier = Modifier.scale(0.8f)
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "Admin",
                            style = MaterialTheme.typography.labelSmall,
                        )
                        Switch(
                            checked = user.isAdmin,
                            onCheckedChange = { onToggleAdmin(user.id, it) },
                            modifier = Modifier.scale(0.8f)
                        )
                    }

                    IconButton(onClick = { expanded = !expanded }) {
                        Icon(
                            if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = "Expand details"
                        )
                    }
                }
            }

            Text(
                "Last Active: ${if (user.lastLogin > 0) dateFormat.format(Date(user.lastLogin)) else "Never"}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier.padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    HorizontalDivider(
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)
                    )

                    // Device Info
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Info, "", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Device: ${user.device} (Android ${user.androidVer}) | App: ${user.appVersion}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    if (user.dailyStats.isNotEmpty()) {
                        Text(
                            "Recent Usage Stats",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )

                        user.dailyStats.forEach { stats ->
                            DailyStatsItem(stats, dateFormat)
                        }
                    } else {
                        Text(
                            "No detailed usage logs found.",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DailyStatsItem(stats: DailyStats, dateFormat: SimpleDateFormat) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Column(Modifier.padding(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    stats.date.replace("_", "/"),
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    "Last activity: ${dateFormat.format(Date(stats.lastActive))}${if (stats.appVersion.isNotEmpty()) " (v${stats.appVersion})" else ""}",
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 10.sp
                )
            }

            Spacer(Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                StatChip(Icons.Default.Search, "${stats.searchCount} Searches")
                StatChip(Icons.Default.Mouse, "${stats.totalClicks} Clicks")
                if (stats.latitude != 0.0) {
                    StatChip(Icons.Default.LocationOn, "Location Logged")
                }
            }

            if (stats.buttonClicks.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "Clicks: " + stats.buttonClicks.entries.joinToString(", ") { "${it.key}: ${it.value}" },
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun StatChip(icon: ImageVector, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.secondary)
        Spacer(Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, fontSize = 11.sp)
    }
}

@Composable
private fun AdminBrochureCard(
    brochure: Brochure,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(brochure.name.ifBlank { "Untitled brochure" }, fontWeight = FontWeight.SemiBold)
            if (brochure.description.isNotBlank()) {
                Text(brochure.description, style = MaterialTheme.typography.bodyMedium)
            }
            if (brochure.brochureDate.isNotBlank()) {
                Text("Date: ${brochure.brochureDate}", style = MaterialTheme.typography.bodySmall)
            }
            Text(
                "Attachment: ${brochure.file.ifBlank { "Not set" }}",
                style = MaterialTheme.typography.bodySmall
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onEdit) {
                    Text("Edit")
                }
                TextButton(onClick = onDelete) {
                    Text("Delete")
                }
            }
        }
    }
}

private fun displayNameForUri(context: Context, uri: Uri): String {
    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (index >= 0 && cursor.moveToFirst()) {
            return cursor.getString(index)
        }
    }
    return uri.lastPathSegment?.substringAfterLast('/') ?: "brochure"
}

private fun safeStorageName(name: String): String =
    name.lowercase(Locale.US)
        .replace(Regex("[^a-z0-9._-]+"), "-")
        .trim('-')
        .ifBlank { "brochure" }
