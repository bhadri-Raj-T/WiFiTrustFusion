package com.bhadrirajt.wifitrustfusion.wifi.logging

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bhadrirajt.wifitrustfusion.wifi.model.CollectorSessionMetadata
import java.io.File
import java.util.UUID
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Development-only, synthetic CSV completion regression suite. */
@RunWith(AndroidJUnit4::class)
class M3_4D_D2C_CompletionRegressionTest {
    private lateinit var logger: ResearchSessionLogger
    private lateinit var directory: File

    @Before fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val sessionId = "M34D_D2C_SYNTH_" + UUID.randomUUID().toString().replace("-", "")
        val base = context.getExternalFilesDir("research_sessions")
            ?: File(context.filesDir, "research_sessions")
        directory = File(base, sessionId)
        assertFalse(directory.exists())
        logger = ResearchSessionLogger(context)
        logger.startSession(CollectorSessionMetadata(
            sessionId = sessionId,
            collectorVersion = "SYNTH_ONLY",
            protocolVersion = "SYNTH_ONLY",
            observationSchemaVersion = "SYNTH_ONLY",
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
            runLabel = "D2C_SYNTHETIC"
        ))
    }

    @After fun tearDown() {
        if (::logger.isInitialized) logger.close()
        if (::directory.isInitialized && directory.name.startsWith("M34D_D2C_SYNTH_")) {
            assertTrue(directory.deleteRecursively())
        }
    }

    private fun completion(): String =
        File(directory, "session_completion.csv").readLines(Charsets.UTF_8).last()

    @Test fun userStopWithNoReportedFailureIsClean() {
        logger.markSessionComplete("USER_STOP", 0)
        assertTrue(completion().endsWith(",true"))
    }

    @Test fun loggerWriteFailureIsNeverClean() {
        logger.markSessionComplete("LOGGER_WRITE_FAILED", 0)
        assertTrue(completion().endsWith(",false"))
    }

    @Test fun loggerStartFailureIsNeverClean() {
        logger.markSessionComplete("LOGGER_START_FAILED", 0)
        assertTrue(completion().endsWith(",false"))
    }

    @Test fun activityLeftForegroundIsNotCleanUserStop() {
        logger.markSessionComplete("ACTIVITY_LEFT_FOREGROUND", 0)
        assertTrue(completion().endsWith(",false"))
    }

    @Test fun unknownStopReasonFailsClosed() {
        logger.markSessionComplete("UNKNOWN_STOP", 0)
        assertTrue(completion().endsWith(",false"))
    }

    @Test fun failureCannotForceCleanTrue() {
        logger.markSessionComplete("LOGGER_WRITE_FAILED", 0, cleanClose = true)
        assertTrue(completion().endsWith(",false"))
    }

    @Test fun userStopCanForceNonCleanWhenPriorErrorOccurred() {
        logger.markSessionComplete("USER_STOP", 0, cleanClose = false)
        assertTrue(completion().endsWith(",false"))
    }

    @Test fun completionCannotOverwritePreviouslyStoredEvidence() {
        logger.markSessionComplete("USER_STOP", 0)
        val before = File(directory, "session_completion.csv").readBytes()
        try {
            logger.markSessionComplete("LOGGER_WRITE_FAILED", 0)
            fail("Completion overwrite should be blocked")
        } catch (_: IllegalStateException) { }
        assertArrayEquals(before, File(directory, "session_completion.csv").readBytes())
    }
}
