package com.bhadrirajt.wifitrustfusion.wifi.logging

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bhadrirajt.wifitrustfusion.wifi.model.CollectorEventRecord
import com.bhadrirajt.wifitrustfusion.wifi.model.CollectorEventType
import com.bhadrirajt.wifitrustfusion.wifi.model.CollectorSessionMetadata
import com.bhadrirajt.wifitrustfusion.wifi.model.ScanCompletionReason
import com.bhadrirajt.wifitrustfusion.wifi.model.ScanRequestRecord
import com.bhadrirajt.wifitrustfusion.wifi.model.TimestampState
import com.bhadrirajt.wifitrustfusion.wifi.model.WifiObservation
import java.io.BufferedWriter
import java.io.File
import java.io.IOException
import java.util.UUID
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * M3.4D Stage D1: synthetic CSV logger fault-contract tests.
 * Run ONLY on a fresh emulator. No real Wi-Fi scan, SSID, BSSID or sensor data.
 * These tests characterize the *current* CSV logger; they do not exercise the
 * MainActivity stop state machine, power loss, or Room automatic replay.
 */
@RunWith(AndroidJUnit4::class)
class M3_4D_LoggerFaultContractsTest {
    private lateinit var logger: ResearchSessionLogger
    private lateinit var metadata: CollectorSessionMetadata
    private lateinit var sessionDir: File

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val id = "M34D_LOGGER_SYNTH_" + UUID.randomUUID().toString().replace("-", "")
        val root = context.getExternalFilesDir("research_sessions")
            ?: File(context.filesDir, "research_sessions")
        sessionDir = File(root, id)
        assertFalse("Synthetic session ID must be new", sessionDir.exists())
        metadata = CollectorSessionMetadata(
            sessionId = id,
            collectorVersion = "SYNTH_COLLECTOR",
            protocolVersion = "SYNTH_PROTOCOL",
            observationSchemaVersion = "SYNTH_OBS",
            appVersionName = "test",
            appVersionCode = 1L,
            deviceAlias = "EMULATOR_TEST",
            manufacturer = "SYNTHETIC",
            model = "SYNTHETIC",
            deviceCodename = "EMULATOR",
            androidRelease = "11",
            apiLevel = 30,
            startedUtcIso = "2026-11-01T00:00:00Z",
            startedElapsedUs = 100000L,
            requestedScanIntervalMs = 35000L,
            runLabel = "M3_4D_SYNTHETIC_LOGGER_TEST"
        )
        logger = ResearchSessionLogger(context)
        logger.startSession(metadata)
        assertTrue("Synthetic session folder created", sessionDir.isDirectory)
    }

    @After
    fun teardown() {
        if (::logger.isInitialized) logger.close()
        if (::sessionDir.isInitialized && sessionDir.name.startsWith("M34D_LOGGER_SYNTH_")) {
            assertTrue("Could not clean up own synthetic test directory", sessionDir.deleteRecursively())
        }
    }

    private fun csvLines(name: String): List<String> = File(sessionDir, name).readLines(Charsets.UTF_8)

    private fun request(sessionId: String = metadata.sessionId, count: Int = 0) = ScanRequestRecord(
        sessionId = sessionId,
        requestId = "REQ_000001",
        requestUtcIso = "2026-11-01T00:00:01Z",
        requestElapsedUs = 110000L,
        requestedIntervalMs = 35000L,
        startScanReturned = true,
        broadcastReceived = true,
        resultsUpdated = true,
        resultCount = count,
        completionReason = ScanCompletionReason.BROADCAST_RESULTS_UPDATED,
        completedUtcIso = "2026-11-01T00:00:02Z",
        completedElapsedUs = 120000L,
        failureDetail = null
    )

    private fun observation(id: String, sessionId: String = metadata.sessionId) = WifiObservation(
        observationId = id,
        sessionId = sessionId,
        requestId = "REQ_000001",
        observedUtcIso = "2026-11-01T00:00:02Z",
        observedElapsedUs = 120000L,
        ssid = null,
        bssid = null,
        rssiDbm = -60,
        frequencyMhz = 2412,
        channelNumber = 1,
        channelWidth = null,
        capabilities = "[SYNTHETIC]",
        scanTimestampUs = 115000L,
        scanAgeMs = 5L,
        timestampState = TimestampState.ADVANCED,
        seenAfterRequest = true
    )

    @Test fun wrongSessionRequestRejectedBeforeCsvWrite() {
        try {
            logger.logRequest(request(sessionId = "OTHER_SYNTHETIC_SESSION"))
            fail("Mismatched request must throw")
        } catch (_: IllegalArgumentException) { }
        assertEquals(1, csvLines("requests.csv").size)
        assertFalse(File(sessionDir, "session_completion.csv").exists())
    }

    @Test fun invalidMixedObservationBatchLeavesNoObservationRows() {
        logger.logRequest(request(count = 2))
        try {
            logger.logObservations(listOf(
                observation("OBS_SYNTH_1"),
                observation("OBS_SYNTH_2", sessionId = "OTHER_SYNTHETIC_SESSION")
            ))
            fail("Mixed-session observation batch must throw")
        } catch (_: IllegalArgumentException) { }
        // Characterization: the already-flushed request row remains; CSV
        // request + observation writes are NOT an atomic transaction.
        assertEquals(2, csvLines("requests.csv").size)
        assertEquals(1, csvLines("observations.csv").size)
        assertFalse(File(sessionDir, "session_completion.csv").exists())
    }

    @Test fun closedCsvWriterFailureIsObservableAndNotCompleted() {
        val field = ResearchSessionLogger::class.java.getDeclaredField("requestsWriter")
        field.isAccessible = true
        val writer = field.get(logger) as BufferedWriter
        writer.close() // Target ONLY this synthetic test's private writer.
        try {
            logger.logRequest(request())
            fail("Closed request writer must reject the write")
        } catch (_: IOException) { }
        assertEquals(1, csvLines("requests.csv").size)
        assertFalse(File(sessionDir, "session_completion.csv").exists())
    }

    @Test fun mismatchedEventRejectedWithoutAppend() {
        val event = CollectorEventRecord(
            eventId = "EVT_SYNTH_1", sessionId = "OTHER_SYNTHETIC_SESSION",
            eventUtcIso = "2026-11-01T00:00:03Z", eventElapsedUs = 130000L,
            eventType = CollectorEventType.SESSION_STOPPED,
            requestId = null, detail = "synthetic"
        )
        try {
            logger.logEvent(event)
            fail("Mismatched event must throw")
        } catch (_: IllegalArgumentException) { }
        assertEquals(1, csvLines("events.csv").size)
    }

    @Test fun duplicateSessionFolderCannotOverwriteOriginalEvidence() {
        logger.logRequest(request())
        logger.close()
        val priorMetadata = File(sessionDir, "metadata.csv").readBytes()
        val priorRequests = File(sessionDir, "requests.csv").readBytes()
        try {
            logger.startSession(metadata)
            fail("An existing synthetic folder must not be reused")
        } catch (_: IllegalStateException) { }
        assertArrayEquals(priorMetadata, File(sessionDir, "metadata.csv").readBytes())
        assertArrayEquals(priorRequests, File(sessionDir, "requests.csv").readBytes())
    }

    @Test fun normalUserStopWritesACompletionRecord() {
        logger.logRequest(request())
        logger.markSessionComplete("USER_STOP", 1, "2026-11-01T00:00:04Z")
        val lines = csvLines("session_completion.csv")
        assertEquals(2, lines.size)
        assertTrue(lines[0].contains("clean_close"))
        assertTrue(lines[1].contains("USER_STOP"))
        assertTrue(lines[1].endsWith(",true"))
    }
}
