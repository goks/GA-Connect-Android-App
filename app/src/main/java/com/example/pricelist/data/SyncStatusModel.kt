package com.example.pricelist.data

import com.google.firebase.Timestamp
import java.util.Locale

enum class DerivedSyncState(val label: String, val colorHex: String) {
    RUNNING("Running", "#31525B"),
    INTERRUPTED("Interrupted", "#A23B2A"),
    SUCCESS("Success", "#2E6F40"),
    FAILED("Failed", "#A23B2A"),
    CANCELLED("Cancelled", "#8A6A1F"),
    UNKNOWN("Unknown", "#5F6B6D")
}

data class SyncProgress(
    val done: Int = 0,
    val total: Int = 0
)

data class SyncRun(
    val schemaVersion: Int = 2,
    val runId: String = "",
    val kind: String = "price_list",
    val kindLabel: String = "",
    val state: String = "",
    val trigger: String = "",
    val stage: String? = null,
    val stageIndex: Int? = null,
    val stageTotal: Int? = null,
    val progress: SyncProgress? = null,
    val counts: Map<String, Int> = emptyMap(),
    val countLabels: Map<String, String> = emptyMap(),
    val startedAt: Timestamp? = null,
    val finishedAt: Timestamp? = null,
    val heartbeatAt: Timestamp? = null,
    val durationSeconds: Double? = null,
    val error: String? = null,
    val host: String = "",
    val database: String = "",
    val appVersion: String = ""
) {
    fun getEffectiveKindLabel(): String {
        if (kindLabel.isNotBlank()) return kindLabel
        return when (kind) {
            "price_list" -> "Price List"
            "sql_backup" -> "SQL Backups"
            "attendance" -> "Attendance"
            else -> kind.replace("_", " ").replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }
        }
    }

    fun getDerivedState(nowMillis: Long = System.currentTimeMillis()): DerivedSyncState {
        val st = state.lowercase(Locale.ROOT)
        return when (st) {
            "running" -> {
                val hbTime = heartbeatAt?.toDate()?.time ?: startedAt?.toDate()?.time ?: 0L
                if (hbTime > 0 && (nowMillis - hbTime) > 5 * 60 * 1000L) {
                    DerivedSyncState.INTERRUPTED
                } else {
                    DerivedSyncState.RUNNING
                }
            }
            "success" -> DerivedSyncState.SUCCESS
            "failed" -> DerivedSyncState.FAILED
            "cancelled" -> DerivedSyncState.CANCELLED
            else -> DerivedSyncState.UNKNOWN
        }
    }

    fun getFormattedTrigger(): String {
        return when (trigger.lowercase(Locale.ROOT)) {
            "manual" -> "Manual"
            "scheduled", "auto" -> "Scheduled"
            "full_rebuild" -> "Full rebuild"
            else -> if (trigger.isNotBlank()) trigger.replaceFirstChar { it.titlecase(Locale.ROOT) } else "Scheduled"
        }
    }

    fun getFormattedDuration(nowMillis: Long = System.currentTimeMillis()): String {
        val seconds = if (durationSeconds != null && durationSeconds > 0) {
            durationSeconds.toLong()
        } else if (startedAt != null) {
            val startMs = startedAt.toDate().time
            val endMs = finishedAt?.toDate()?.time ?: nowMillis
            maxOf(0L, (endMs - startMs) / 1000L)
        } else {
            0L
        }

        val mins = seconds / 60
        val secs = seconds % 60
        return if (mins > 0) "${mins}m ${secs}s" else "${secs}s"
    }

    fun getOrderedCounterPairs(): List<Pair<String, Int>> {
        val knownOrder = when (kind) {
            "price_list" -> listOf("items", "images", "deleted", "added")
            "sql_backup" -> listOf("databases", "verified", "uploaded", "failed")
            "attendance" -> listOf("employees", "attendance")
            else -> emptyList()
        }

        val result = mutableListOf<Pair<String, Int>>()
        for (key in knownOrder) {
            if (counts.containsKey(key)) {
                result.add(key to (counts[key] ?: 0))
            }
        }
        val extraKeys = counts.keys.filter { it !in knownOrder }.sorted()
        for (key in extraKeys) {
            result.add(key to (counts[key] ?: 0))
        }
        return result
    }

    fun getCounterLabel(key: String): String {
        if (countLabels.containsKey(key) && !countLabels[key].isNullOrBlank()) {
            return countLabels[key]!!
        }
        return when (key) {
            "items" -> "Items"
            "images" -> "Images"
            "deleted" -> "Deleted"
            "added" -> "Added"
            "databases" -> "Databases"
            "verified" -> "Verified"
            "uploaded" -> "Uploaded"
            "failed" -> "Failed"
            "employees" -> "Employees"
            "attendance" -> "Attendance Logs"
            else -> key.replace("_", " ").replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }
        }
    }
}

object SyncStatusParser {

    @Suppress("UNCHECKED_CAST")
    fun parseRunFromMap(map: Map<String, Any?>, fallbackRunId: String = ""): SyncRun {
        val schemaVer = (map["schemaVersion"] as? Number)?.toInt() ?: 2
        val runId = (map["runId"] as? String)?.ifBlank { null } ?: fallbackRunId
        val kind = (map["kind"] as? String)?.ifBlank { null } ?: "price_list"
        val kindLabel = (map["kindLabel"] as? String) ?: ""
        val state = (map["state"] as? String) ?: ""
        val trigger = (map["trigger"] as? String) ?: ""
        val stage = map["stage"] as? String
        val stageIndex = (map["stageIndex"] as? Number)?.toInt()
        val stageTotal = (map["stageTotal"] as? Number)?.toInt()

        val progressMap = map["progress"] as? Map<String, Any?>
        val progress = if (progressMap != null) {
            val done = (progressMap["done"] as? Number)?.toInt() ?: 0
            val total = (progressMap["total"] as? Number)?.toInt() ?: 0
            SyncProgress(done = done, total = total)
        } else null

        val countsRaw = map["counts"] as? Map<String, Any?>
        val counts = countsRaw?.mapValues { (_, v) -> (v as? Number)?.toInt() ?: 0 } ?: emptyMap()

        val countLabelsRaw = map["countLabels"] as? Map<String, Any?>
        val countLabels = countLabelsRaw?.mapValues { (_, v) -> v?.toString() ?: "" } ?: emptyMap()

        val startedAt = map["startedAt"] as? Timestamp
        val finishedAt = map["finishedAt"] as? Timestamp
        val heartbeatAt = map["heartbeatAt"] as? Timestamp

        val durationSeconds = (map["durationSeconds"] as? Number)?.toDouble()
        val error = map["error"] as? String
        val host = (map["host"] as? String) ?: ""
        val database = (map["database"] as? String) ?: ""
        val appVersion = (map["appVersion"] as? String) ?: ""

        return SyncRun(
            schemaVersion = schemaVer,
            runId = runId,
            kind = kind,
            kindLabel = kindLabel,
            state = state,
            trigger = trigger,
            stage = stage,
            stageIndex = stageIndex,
            stageTotal = stageTotal,
            progress = progress,
            counts = counts,
            countLabels = countLabels,
            startedAt = startedAt,
            finishedAt = finishedAt,
            heartbeatAt = heartbeatAt,
            durationSeconds = durationSeconds,
            error = error,
            host = host,
            database = database,
            appVersion = appVersion
        )
    }

    @Suppress("UNCHECKED_CAST")
    fun parseLatestStatusDoc(data: Map<String, Any?>?): Map<String, SyncRun> {
        if (data == null) return emptyMap()

        val resultMap = mutableMapOf<String, SyncRun>()
        val latestMap = data["latest"] as? Map<String, Map<String, Any?>>

        if (latestMap != null && latestMap.isNotEmpty()) {
            for ((kind, runMap) in latestMap) {
                if (runMap is Map<*, *>) {
                    val run = parseRunFromMap(runMap as Map<String, Any?>, fallbackRunId = kind)
                    val effectiveKind = run.kind.ifBlank { kind }
                    resultMap[effectiveKind] = run.copy(kind = effectiveKind)
                }
            }
        }

        // Fallback / Legacy compatibility: root fields mirror price_list
        if (!resultMap.containsKey("price_list")) {
            val rootRun = parseRunFromMap(data, fallbackRunId = "root_price_list")
            if (rootRun.state.isNotBlank() || rootRun.startedAt != null || rootRun.runId.isNotBlank()) {
                resultMap["price_list"] = rootRun.copy(kind = "price_list")
            }
        }

        return resultMap
    }
}
