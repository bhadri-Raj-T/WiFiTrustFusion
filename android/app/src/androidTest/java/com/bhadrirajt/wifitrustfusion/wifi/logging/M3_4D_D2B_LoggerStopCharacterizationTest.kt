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
 * Synthetic, emulator-only CHARACTERIZATION of current M3 logger stop behavior.
 * Intentional characterization of existing weaknesses is NOT evidence of a fix.
 * Never run against DEV01 or a real acquisition session.
 */
@RunWith(AndroidJUnit4::class)
class M3_4D_D2B_LoggerStopCharacterizationTest {
    private lateinit var logger: ResearchSessionLogger
    private lateinit var metadata: CollectorSessionMetadata
    private lateinit var sessionDir: File

    @Before fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val id = "M34D_D2B_SYNTH_" + UUID.randomUUID().toString().replace("-", "")
        val root = ctx.getExternalFilesDir("research_sessions")
            ?: File(ctx.filesDir, "research_sessions")
        sessionDir = File(root, id)
        assertFalse("Synthetic session directory must not exist", sessionDir.exists())
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
            runLabel = "M3_4D_D2B_SYNTHETIC"
        )
        logger = ResearchSessionLogger(ctx)
        logger.startSession(metadata)
        assertTrue(sessionDir.isDirectory)
    }

    @After fun tearDown() {
        if (::logger.isInitialized) logger.close()
        if (::sessionDir.isInitialized && sessionDir.name.startsWith("M34D_D2B_SYNTH_")) {
            assertTrue("Synthetic directory cleanup failed", sessionDir.deleteRecursively())
        }
    }

    private fun lines(file: String) = File(sessionDir, file).readLines(Charsets.UTF_8)

    private fun request() = ScanRequestRecord(
        sessionId = metadata.sessionId,
        requestId = "REQ_SYNTH_1",
        requestUtcIso = "2026-11-01T00:00:01Z",
        requestElapsedUs = 110000L,
        requestedIntervalMs = 35000L,
        startScanReturned = true,
        broadcastReceived = true,
        resultsUpdated = true,
        resultCount = 1,
        completionReason = ScanCompletionReason.BROADCAST_RESULTS_UPDATED,
        completedUtcIso = "2026-11-01T00:00:02Z",
        completedElapsedUs = 120000L,
        failureDetail = null
    )

    private fun observation() = WifiObservation(
        observationId = "OBS_SYNTH_1",
        sessionId = metadata.sessionId,
        requestId = "REQ_SYNTH_1",
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

    @Test fun observationWriteFailureLeavesPriorRequestRowWithoutCompletion() {
        logger.logRequest(request())
        // Inject an I/O failure ONLY into this test's private observation writer.
        val field = ResearchSessionLogger::class.java.getDeclaredField("observationsWriter")
        field.isAccessible = true
        (field.get(logger) as BufferedWriter).close()
        try {
            logger.logObservations(listOf(observation()))
            fail("Closed observation writer should throw")
        } catch (_: IOException) { }
        assertEquals(2, lines("requests.csv").size)
        assertEquals(1, lines("observations.csv").size)
        assertFalse(File(sessionDir, "session_completion.csv").exists())
    }

    @Test fun loggerWriteFailedStopRecordsNonCleanCompletion() {
        // Regression: do not report logger failure as clean completion.
        logger.markSessionComplete("LOGGER_WRITE_FAILED", 0, "2026-11-01T00:00:03Z")
        val csv = lines("session_completion.csv")
        assertEquals(2, csv.size)
        assertTrue(csv.last().contains("LOGGER_WRITE_FAILED"))
        assertTrue("Logger failure must have clean_close=false", csv.last().endsWith(",false"))
    }

    @Test fun ordinaryUserStopCurrentlyWritesCleanCloseTrue() {
        logger.markSessionComplete("USER_STOP", 0, "2026-11-01T00:00:03Z")
        assertTrue(lines("session_completion.csv").last().endsWith(",true"))
    }

    @Test fun closingWithoutCompletionLeavesNoCompletionMarker() {
        logger.logRequest(request())
        logger.close()
        assertFalse(logger.isActive())
        assertEquals(2, lines("requests.csv").size)
        assertFalse(File(sessionDir, "session_completion.csv").exists())
    }

    @Test fun closedSessionCannotBeRetroactivelyMarkedComplete() {
        logger.close()
        try {
            logger.markSessionComplete("LOGGER_WRITE_FAILED", 0)
            fail("Closed session cannot be marked complete")
        } catch (_: IllegalStateException) { }
        assertFalse(File(sessionDir, "session_completion.csv").exists())
    }
}
