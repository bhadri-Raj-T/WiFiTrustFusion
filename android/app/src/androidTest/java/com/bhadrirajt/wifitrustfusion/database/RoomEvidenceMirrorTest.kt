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
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Synthetic, in-memory-only tests. Never reads real device Wi-Fi evidence. */
@RunWith(AndroidJUnit4::class)
class RoomEvidenceMirrorTest {
    private lateinit var db: EvidenceDatabase
    private val mirrors = mutableListOf<RoomEvidenceMirror>()

    @Before fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, EvidenceDatabase::class.java).build()
    }

    @After fun tearDown() {
        mirrors.forEach { it.shutdown() }
        // Each test awaits its terminal status before touching this database.
        db.close()
    }

    private fun session(id: String = "SYNTH_M3B") = CollectorSessionMetadata(
        id, "COLLECTOR_SYNTH", "PROTOCOL_SYNTH", "OBS_SYNTH", "test", 1L,
        "J7_SYNTH", "SYNTH", "SYNTH", "SYNTH", "test", 27,
        "2026-10-29T00:00:00Z", 100000L, 35000L, "SYNTHETIC_TEST"
    )

    private fun request(id: String = "REQ_000001", count: Int = 1, sessionId: String = "SYNTH_M3B") =
        ScanRequestRecord(
            sessionId, id, "2026-10-29T00:00:01Z", 110000L, 35000L,
            true, true, true, count, ScanCompletionReason.BROADCAST_RESULTS_UPDATED,
            "2026-10-29T00:00:02Z", 120000L, null
        )

    private fun observation(id: String = "OBS_000001", sessionId: String = "SYNTH_M3B") =
        WifiObservation(
            id, sessionId, "REQ_000001", "2026-10-29T00:00:02Z", 120000L,
            null, null, -61, 2412, 1, null, "[SYNTHETIC]", 115000L, 5L,
            TimestampState.ADVANCED, true
        )

    private fun event(id: String = "EVT_000001", sessionId: String = "SYNTH_M3B") =
        CollectorEventRecord(
            id, sessionId, "2026-10-29T00:00:03Z", 130000L,
            CollectorEventType.SESSION_STOPPED, null, "synthetic"
        )

    private fun newMirror(onStatus: (RoomEvidenceMirror.MirrorStatus)->Unit): RoomEvidenceMirror =
        RoomEvidenceMirror(db, onStatus).also { mirrors.add(it) }

    private fun awaitTerminal(
        latch: CountDownLatch,
        state: AtomicReference<RoomEvidenceMirror.MirrorStatus>
    ): RoomEvidenceMirror.MirrorStatus {
        assertTrue("Room mirror callback timed out", latch.await(20, TimeUnit.SECONDS))
        return state.get() ?: error("No terminal mirror status")
    }

    @Test fun orderedSessionRequestEventCompletionMatches() {
        val latch = CountDownLatch(1)
        val last = AtomicReference<RoomEvidenceMirror.MirrorStatus>()
        val mirror = newMirror { result ->
            if (result.state != "WRITE_ERROR") { last.set(result); latch.countDown() }
        }
        mirror.recordSession(session())
        mirror.recordRequest(request(), listOf(observation()))
        mirror.recordEvent(event())
        mirror.recordCompletion("SYNTH_M3B", "2026-10-29T00:00:04Z", "USER_STOP", 1, true)
        val result = awaitTerminal(latch, last)
        assertEquals("MATCH", result.state)
        assertEquals(1, result.storedRequests)
        assertEquals(1, result.storedObservations)
        assertEquals(1, result.storedEvents)
        runBlocking { assertEquals(true, db.evidenceDao().completionForSession("SYNTH_M3B")?.cleanClose) }
    }

    @Test fun zeroObservationRequestIsNotDropped() {
        val latch = CountDownLatch(1)
        val last = AtomicReference<RoomEvidenceMirror.MirrorStatus>()
        val mirror = newMirror { result ->
            if (result.state != "WRITE_ERROR") { last.set(result); latch.countDown() }
        }
        mirror.recordSession(session())
        mirror.recordRequest(request(count = 0), emptyList())
        mirror.recordCompletion("SYNTH_M3B", "2026-10-29T00:00:04Z", "USER_STOP", 1, true)
        val result = awaitTerminal(latch, last)
        assertEquals("MATCH", result.state)
        assertEquals(1, result.storedRequests)
        assertEquals(0, result.storedObservations)
    }

    @Test fun rejectedDuplicateRequestSignalsReviewNotSilentOverwrite() {
        val latch = CountDownLatch(1)
        val last = AtomicReference<RoomEvidenceMirror.MirrorStatus>()
        val mirror = newMirror { result ->
            if (result.state == "REVIEW_REQUIRED") { last.set(result); latch.countDown() }
        }
        mirror.recordSession(session())
        mirror.recordRequest(request(), listOf(observation()))
        mirror.recordRequest(request(), listOf(observation()))
        mirror.recordCompletion("SYNTH_M3B", "2026-10-29T00:00:04Z", "USER_STOP", 2, true)
        val result = awaitTerminal(latch, last)
        assertEquals("REVIEW_REQUIRED", result.state)
        assertTrue(result.errorCount >= 1)
        assertEquals(1, result.storedRequests)
        assertEquals(1, result.storedObservations)
    }

    @Test fun completionMismatchSignalsReview() {
        val latch = CountDownLatch(1)
        val last = AtomicReference<RoomEvidenceMirror.MirrorStatus>()
        val mirror = newMirror { result ->
            if (result.state != "WRITE_ERROR") { last.set(result); latch.countDown() }
        }
        mirror.recordSession(session())
        mirror.recordRequest(request(), listOf(observation()))
        mirror.recordCompletion("SYNTH_M3B", "2026-10-29T00:00:04Z", "USER_STOP", 0, true)
        val result = awaitTerminal(latch, last)
        assertEquals("REVIEW_REQUIRED", result.state)
        assertEquals(1, result.storedRequests)
    }
}
