package com.example.pricelist

import com.example.pricelist.data.DerivedSyncState
import com.example.pricelist.data.SyncStatusParser
import com.google.firebase.Timestamp
import java.util.Date

class SyncStatusParserTest {

    private fun assertEquals(expected: Any?, actual: Any?) {
        check(expected == actual) { "Expected <$expected> but was <$actual>" }
    }

    private fun assertNotNull(actual: Any?) {
        check(actual != null) { "Expected non-null value" }
    }

    private fun assertNull(actual: Any?) {
        check(actual == null) { "Expected null value but was <$actual>" }
    }

    fun testLegacyRootOnlyDocumentParsing() {
        val legacyData = mapOf<String, Any?>(
            "schemaVersion" to 1,
            "runId" to "20261125T063000Z-legacy",
            "state" to "running",
            "trigger" to "auto",
            "stage" to "Uploading items",
            "stageIndex" to 3,
            "stageTotal" to 10,
            "startedAt" to Timestamp(Date(1700000000000L)),
            "host" to "DESKTOP-WIN",
            "database" to "SalesDB",
            "appVersion" to "1.4.0"
        )

        val latestMap = SyncStatusParser.parseLatestStatusDoc(legacyData)
        assertNotNull(latestMap)
        assertEquals(1, latestMap.size)

        val run = latestMap["price_list"]
        assertNotNull(run)
        assertEquals("price_list", run!!.kind)
        assertEquals("Price List", run.getEffectiveKindLabel())
        assertEquals("20261125T063000Z-legacy", run.runId)
        assertEquals("running", run.state)
        assertEquals("Scheduled", run.getFormattedTrigger())
        assertEquals("Uploading items", run.stage)
    }

    fun testMissingLatestFallback() {
        val dataWithoutLatest = mapOf<String, Any?>(
            "runId" to "test-run-123",
            "state" to "success",
            "trigger" to "manual",
            "counts" to mapOf("items" to 100, "added" to 5)
        )

        val latestMap = SyncStatusParser.parseLatestStatusDoc(dataWithoutLatest)
        assertNotNull(latestMap["price_list"])
        val run = latestMap["price_list"]!!
        assertEquals("success", run.state)
        assertEquals("Manual", run.getFormattedTrigger())
        assertEquals(100, run.counts["items"])
        assertEquals(5, run.counts["added"])
    }

    fun testUnknownKindAndCustomCountLabels() {
        val data = mapOf<String, Any?>(
            "latest" to mapOf(
                "custom_sync" to mapOf<String, Any?>(
                    "kind" to "custom_sync",
                    "kindLabel" to "Custom Sync Task",
                    "state" to "success",
                    "counts" to mapOf("files" to 42, "unknown_key" to 10),
                    "countLabels" to mapOf("files" to "Processed Files")
                )
            )
        )

        val latestMap = SyncStatusParser.parseLatestStatusDoc(data)
        assertNotNull(latestMap["custom_sync"])
        val run = latestMap["custom_sync"]!!
        assertEquals("Custom Sync Task", run.getEffectiveKindLabel())
        assertEquals("Processed Files", run.getCounterLabel("files"))
        assertEquals("Unknown Key", run.getCounterLabel("unknown_key"))
    }

    fun testExtraCountersOrdering() {
        val data = mapOf<String, Any?>(
            "kind" to "sql_backup",
            "counts" to mapOf(
                "failed" to 1,
                "extra_b" to 20,
                "databases" to 5,
                "extra_a" to 10,
                "uploaded" to 4
            )
        )

        val run = SyncStatusParser.parseRunFromMap(data)
        val pairs = run.getOrderedCounterPairs()

        // Known order for sql_backup: databases, verified, uploaded, failed
        // followed by extra keys sorted alphabetically: extra_a, extra_b
        val keys = pairs.map { it.first }
        assertEquals(listOf("databases", "uploaded", "failed", "extra_a", "extra_b"), keys)
    }

    fun testStaleHeartbeatToInterrupted() {
        val now = 1700000000000L
        val staleHb = Timestamp(Date(now - (6 * 60 * 1000L))) // 6 mins ago

        val run = SyncStatusParser.parseRunFromMap(
            mapOf(
                "state" to "running",
                "heartbeatAt" to staleHb
            )
        )

        val derivedState = run.getDerivedState(nowMillis = now)
        assertEquals(DerivedSyncState.INTERRUPTED, derivedState)
    }

    fun testNullProgressHandling() {
        val run = SyncStatusParser.parseRunFromMap(
            mapOf(
                "state" to "running",
                "progress" to null
            )
        )

        assertNull(run.progress)
    }
}
