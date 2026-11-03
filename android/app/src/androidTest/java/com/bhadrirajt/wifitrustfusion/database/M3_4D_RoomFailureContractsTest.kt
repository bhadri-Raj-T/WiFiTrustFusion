package com.bhadrirajt.wifitrustfusion.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bhadrirajt.wifitrustfusion.wifi.model.CollectorEventRecord
import com.bhadrirajt.wifitrustfusion.wifi.model.CollectorEventType
import com.bhadrirajt.wifitrustfusion.wifi.model.CollectorSessionMetadata
import com.bhadrirajt.wifitrustfusion.wifi.model.ScanCompletionReason
import com.bhadrirajt.wifitrustfusion.wifi.model.ScanRequestRecord
import com.bhadrirajt.wifitrustfusion.wifi.model.TimestampState
import com.bhadrirajt.wifitrustfusion.wifi.model.WifiObservation
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * M3.4D Stage C: ONLY synthetic records and a Room in-memory database.
 * No Wi-Fi scan, physical SSID/BSSID, CSV, filesystem DB, or collector changes.
 * Tests observable failure states, not artificial destructive SQLite faults.
 */
@RunWith(AndroidJUnit4::class)
class M3_4D_RoomFailureContractsTest {
    private val id = "SYNTH_M34D"
    private lateinit var db: EvidenceDatabase
    private lateinit var dao: EvidenceDao
    private val mirrors = mutableListOf<RoomEvidenceMirror>()

    @Before fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, EvidenceDatabase::class.java).build()
        dao = db.evidenceDao()
    }

    @After fun tearDown() {
        mirrors.forEach { it.shutdown() }
        db.close()
    }

    private class Probe {
        val errorLatch = CountDownLatch(1)
        val reviewLatch = CountDownLatch(1)
        val matchLatch = CountDownLatch(1)
        val error = AtomicReference<RoomEvidenceMirror.MirrorStatus>()
        val review = AtomicReference<RoomEvidenceMirror.MirrorStatus>()
        val match = AtomicReference<RoomEvidenceMirror.MirrorStatus>()

        fun accept(status: RoomEvidenceMirror.MirrorStatus) {
            when (status.state) {
                "WRITE_ERROR" -> { error.set(status); errorLatch.countDown() }
                "REVIEW_REQUIRED" -> { review.set(status); reviewLatch.countDown() }
                "MATCH" -> { match.set(status); matchLatch.countDown() }
            }
        }

        fun awaitError(): RoomEvidenceMirror.MirrorStatus {
            assertTrue("Expected WRITE_ERROR callback within 20 seconds", errorLatch.await(20, TimeUnit.SECONDS))
            return error.get() ?: kotlin.error("WRITE_ERROR callback missing status")
        }

        fun awaitReview(): RoomEvidenceMirror.MirrorStatus {
            assertTrue("Expected REVIEW_REQUIRED callback within 20 seconds", reviewLatch.await(20, TimeUnit.SECONDS))
            return review.get() ?: kotlin.error("REVIEW_REQUIRED callback missing status")
        }

        fun awaitMatch(): RoomEvidenceMirror.MirrorStatus {
            assertTrue("Expected MATCH callback within 20 seconds", matchLatch.await(20, TimeUnit.SECONDS))
            return match.get() ?: kotlin.error("MATCH callback missing status")
        }
    }

    private fun mirror(callback: (RoomEvidenceMirror.MirrorStatus) -> Unit): RoomEvidenceMirror =
        RoomEvidenceMirror(db, callback).also { mirrors.add(it) }

    private fun session() = CollectorSessionMetadata(
        id, "COLLECTOR_SYNTHETIC", "PROTOCOL_SYNTHETIC", "OBS_SYNTHETIC", "unit-test", 1L,
        "SYNTHETIC_DEVICE", "SYNTHETIC", "SYNTHETIC", "SYNTHETIC", "test", 27,
        "2026-10-31T00:00:00Z", 100000L, 35000L, "M3_4D_SYNTHETIC_ONLY"
    )

    private fun request(count: Int = 1) = ScanRequestRecord(
        id, "REQ_000001", "2026-10-31T00:00:01Z", 110000L, 35000L,
        true, true, true, count, ScanCompletionReason.BROADCAST_RESULTS_UPDATED,
        "2026-10-31T00:00:02Z", 120000L, null
    )

    private fun observation(requestId: String = "REQ_000001") = WifiObservation(
        "OBS_000001", id, requestId, "2026-10-31T00:00:02Z", 120000L,
        null, null, -58, 2412, 1, null, "[SYNTHETIC]", 115000L, 5L,
        TimestampState.ADVANCED, true
    )

    private fun event() = CollectorEventRecord(
        "EVT_000001", id, "2026-10-31T00:00:03Z", 130000L,
        CollectorEventType.SESSION_STOPPED, null, "SYNTHETIC_ONLY"
    )

    private fun complete(m: RoomEvidenceMirror, count: Int, reason: String = "USER_STOP", clean: Boolean = true) {
        m.recordCompletion(id, "2026-10-31T00:00:04Z", reason, count, clean)
    }

    @Test fun invalidResultCountReportsWriteErrorAndNoPartialBundle() = runBlocking {
        val probe = Probe()
        val m = mirror(probe::accept)
        m.recordSession(session())
        m.recordRequest(request(count = 2), listOf(observation()))
        complete(m, count = 1)
        assertTrue(probe.awaitError().errorCount >= 1)
        val outcome = probe.awaitReview()
        assertEquals("REVIEW_REQUIRED", outcome.state)
        assertEquals(1, outcome.expectedRequests)
        assertEquals(0, outcome.storedRequests)
        assertEquals(0, outcome.storedObservations)
        assertEquals(0, dao.requestCount(id))
        assertEquals(0, dao.observationCount(id))
        // A completion row's cleanClose flag is NOT proof of Room parity.
        assertNotNull(dao.completionForSession(id))
    }

    @Test fun wrongObservationRequestReportsWriteErrorWithoutPartialRequest() = runBlocking {
        val probe = Probe()
        val m = mirror(probe::accept)
        m.recordSession(session())
        m.recordRequest(request(), listOf(observation(requestId = "REQ_WRONG")))
        complete(m, count = 1)
        probe.awaitError()
        assertEquals("REVIEW_REQUIRED", probe.awaitReview().state)
        assertEquals(0, dao.requestCount(id))
        assertEquals(0, dao.observationCount(id))
    }

    @Test fun missingParentSessionDoesNotProduceOrphanRowsOrMatch() = runBlocking {
        val probe = Probe()
        val m = mirror(probe::accept)
        // No recordSession(): foreign key for request and completion must reject writes.
        m.recordRequest(request(), listOf(observation()))
        complete(m, count = 1)
        probe.awaitError()
        val final = probe.awaitReview()
        assertTrue(final.errorCount >= 1)
        assertEquals(0, final.storedRequests)
        assertEquals(0, dao.requestCount(id))
        assertNull(dao.completionForSession(id))
    }

    @Test fun duplicateEventIsNotOverwrittenAndFinalStatusRequiresReview() = runBlocking {
        val probe = Probe()
        val m = mirror(probe::accept)
        m.recordSession(session())
        m.recordEvent(event())
        m.recordEvent(event())
        complete(m, count = 0)
        probe.awaitError()
        val final = probe.awaitReview()
        assertEquals(2, final.expectedEvents)
        assertEquals(1, final.storedEvents)
        assertEquals(1, dao.eventsForSession(id).size)
        assertEquals("SYNTHETIC_ONLY", dao.eventsForSession(id).single().detail)
    }

    @Test fun repeatedCompletionDoesNotOverwriteFirstRowAndRequiresReview() = runBlocking {
        val probe = Probe()
        val m = mirror(probe::accept)
        m.recordSession(session())
        complete(m, count = 0, reason = "USER_STOP", clean = true)
        // A second completion for the same session must ABORT rather than replace.
        complete(m, count = 0, reason = "LOGGER_WRITE_FAILED", clean = false)
        probe.awaitMatch() // First completion can MATCH before the second fails.
        probe.awaitError()
        assertTrue(probe.awaitReview().errorCount >= 1)
        val persisted = dao.completionForSession(id)
        assertNotNull(persisted)
        assertEquals("USER_STOP", persisted?.stopReason)
        assertTrue(persisted?.cleanClose == true)
    }

    @Test fun statusCallbackExceptionCannotCancelCompletedDatabaseWrite() = runBlocking {
        val probe = Probe()
        val m = mirror { status ->
            probe.accept(status)
            throw IllegalStateException("Synthetic UI callback error")
        }
        m.recordSession(session())
        m.recordRequest(request(count = 0), emptyList())
        complete(m, count = 1)
        val result = probe.awaitMatch()
        assertEquals(0, result.errorCount)
        assertEquals(1, dao.requestCount(id))
        assertEquals(0, dao.observationCount(id))
        assertTrue(dao.completionForSession(id)?.cleanClose == true)
    }

    @Test fun closedMirrorRejectsNewEnqueueBeforeAnyWrite() = runBlocking {
        val m = mirror { }
        m.shutdown()
        var rejected = false
        try {
            m.recordSession(session())
        } catch (expected: IllegalStateException) {
            rejected = true
        }
        assertTrue("Closed mirror must refuse new queue work", rejected)
        assertNull(dao.findSession(id))
        // Current contract: caller catches this enqueue-time exception.
        // It does NOT emit a WRITE_ERROR callback, unlike queued write failures.
    }
}
