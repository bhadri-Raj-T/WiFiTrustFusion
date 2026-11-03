package com.bhadrirajt.wifitrustfusion.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class M6SessionHistoryTest {
    private lateinit var db: EvidenceDatabase

    @Before fun open() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, EvidenceDatabase::class.java)
            .allowMainThreadQueries().build()
    }

    @After fun close() { db.close() }

    private fun session(id: String, timestamp: String) = CollectorSessionEntity(
        sessionId = id, collectorVersion = "COLLECTOR_V1",
        protocolVersion = "PROTOCOL_V1", observationSchemaVersion = "OBS_SCHEMA_V1",
        appVersionName = "test", appVersionCode = 1L,
        deviceAlias = "SYNTHETIC", manufacturer = "Synthetic", model = "Synthetic",
        deviceCodename = "synthetic", androidRelease = "11", apiLevel = 30,
        startedUtcIso = timestamp, startedElapsedUs = 1000L,
        requestedScanIntervalMs = 35000L, runLabel = "SYNTHETIC_TEST"
    )

    private fun request(id: String) = ScanRequestEntity(
        sessionId = id, requestId = "R1", requestUtcIso = "2026-01-01T00:00:01Z",
        requestElapsedUs = 1100L, requestedIntervalMs = 35000L,
        startScanReturned = true, broadcastReceived = true,
        resultsUpdated = true, resultCount = 0,
        completionReason = "SYNTHETIC", completedUtcIso = "2026-01-01T00:00:02Z",
        completedElapsedUs = 1200L, failureDetail = null
    )

    @Test fun emptyHistoryDoesNotInventSessions() = runBlocking {
        assertTrue(db.evidenceDao().recentSessionSummaries(10).isEmpty())
    }

    @Test fun sessionWithoutCompletionIsIncomplete() = runBlocking {
        val dao = db.evidenceDao()
        dao.insertSession(session("SYNTH_A", "2026-01-01T00:00:00Z"))
        val rows = dao.recentSessionSummaries(10)
        assertEquals(1, rows.size)
        assertEquals(null, rows[0].cleanClose)
        assertTrue(SessionHistoryFormatter.render(rows).contains("NO COMPLETION RECORD"))
    }

    @Test fun cleanAndNoncleanAreDifferent() = runBlocking {
        val dao = db.evidenceDao()
        dao.insertSession(session("SYNTH_A", "2026-01-01T00:00:00Z"))
        dao.insertSession(session("SYNTH_B", "2026-01-02T00:00:00Z"))
        dao.insertCompletion(SessionCompletionEntity("SYNTH_A", "2026-01-01T00:01:00Z", "USER_STOP", 0, true))
        dao.insertCompletion(SessionCompletionEntity("SYNTH_B", "2026-01-02T00:01:00Z", "LOGGER_WRITE_FAILED", 0, false))
        val rows = dao.recentSessionSummaries(10)
        assertEquals(false, rows[0].cleanClose)
        assertEquals(true, rows[1].cleanClose)
        assertTrue(SessionHistoryFormatter.render(rows).contains("NON-CLEAN STOP"))
        assertTrue(SessionHistoryFormatter.render(rows).contains("CLEAN STOP"))
    }

    @Test fun newestFirstAndLimitAreEnforced() = runBlocking {
        val dao = db.evidenceDao()
        dao.insertSession(session("SYNTH_OLD", "2026-01-01T00:00:00Z"))
        dao.insertSession(session("SYNTH_NEW", "2026-01-02T00:00:00Z"))
        val rows = dao.recentSessionSummaries(1)
        assertEquals(1, rows.size)
        assertEquals("SYNTH_NEW", rows[0].sessionId)
    }

    @Test fun requestCountIsFromRoomNotEstimated() = runBlocking {
        val dao = db.evidenceDao()
        dao.insertSession(session("SYNTH_A", "2026-01-01T00:00:00Z"))
        dao.insertRequest(request("SYNTH_A"))
        val row = dao.recentSessionSummaries(10).single()
        assertEquals(1, row.requestCount)
        assertEquals(0, row.observationCount)
    }

    @Test fun formatterDoesNotExposeEntireSessionIdOrInventQuality() {
        val rows = listOf(SessionHistoryRow(
            sessionId = "PRIVATE_DEVELOPMENT_SESSION_12345678",
            startedUtcIso = "2026-01-01T00:00:00Z",
            requestCount = 2, observationCount = 3,
            stopReason = null, cleanClose = null
        ))
        val out = SessionHistoryFormatter.render(rows)
        assertFalse(out.contains("PRIVATE_DEVELOPMENT_SESSION_12345678"))
        assertTrue(out.contains("12345678"))
        assertTrue(out.contains("NOT persisted"))
    }
}
