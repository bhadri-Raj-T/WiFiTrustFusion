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
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** M3.3A tests use only synthetic in-memory Room records and no Wi-Fi scanning. */
@RunWith(AndroidJUnit4::class)
class EvidenceWriteGatewayTest {
    private lateinit var db: EvidenceDatabase
    private lateinit var dao: EvidenceDao
    private lateinit var writer: EvidenceWriteGateway

    @Before fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, EvidenceDatabase::class.java).build()
        dao = db.evidenceDao()
        writer = EvidenceWriteGateway(dao)
    }

    @After fun tearDown() { db.close() }

    private fun session(id: String = "SYNTH_A") = CollectorSessionMetadata(
        sessionId = id,
        collectorVersion = "COLLECTOR_SYNTHETIC",
        protocolVersion = "PROTOCOL_SYNTHETIC",
        observationSchemaVersion = "OBS_SCHEMA_SYNTHETIC",
        appVersionName = "synthetic-only",
        appVersionCode = 17L,
        deviceAlias = "SYNTH_DEVICE",
        manufacturer = "SYNTH",
        model = "SYNTH_MODEL",
        deviceCodename = "synthetic",
        androidRelease = "test",
        apiLevel = 27,
        startedUtcIso = "2026-10-29T00:00:00Z",
        startedElapsedUs = 101_000L,
        requestedScanIntervalMs = 35_000L,
        runLabel = null
    )

    private fun request(
        sessionId: String = "SYNTH_A",
        requestId: String = "REQ_000001",
        count: Int = 1
    ) = ScanRequestRecord(
        sessionId = sessionId,
        requestId = requestId,
        requestUtcIso = "2026-10-29T00:00:01Z",
        requestElapsedUs = 110_000L,
        requestedIntervalMs = 35_000L,
        startScanReturned = true,
        broadcastReceived = true,
        resultsUpdated = true,
        resultCount = count,
        completionReason = ScanCompletionReason.BROADCAST_RESULTS_UPDATED,
        completedUtcIso = "2026-10-29T00:00:02Z",
        completedElapsedUs = 120_000L,
        failureDetail = null
    )

    private fun observation(
        sessionId: String = "SYNTH_A",
        requestId: String = "REQ_000001",
        observationId: String = "OBS_000001"
    ) = WifiObservation(
        observationId = observationId,
        sessionId = sessionId,
        requestId = requestId,
        observedUtcIso = "2026-10-29T00:00:02Z",
        observedElapsedUs = 120_000L,
        ssid = null,
        bssid = null,
        rssiDbm = -58,
        frequencyMhz = 2412,
        channelNumber = 1,
        channelWidth = null,
        capabilities = "[SYNTHETIC_TEST_ONLY]",
        scanTimestampUs = 115_000L,
        scanAgeMs = 5L,
        timestampState = TimestampState.ADVANCED,
        seenAfterRequest = true
    )

    private fun event(id: String = "SYNTH_A") = CollectorEventRecord(
        eventId = "EVT_000001",
        sessionId = id,
        eventUtcIso = "2026-10-29T00:00:03Z",
        eventElapsedUs = 130_000L,
        eventType = CollectorEventType.SESSION_STOPPED,
        requestId = null,
        detail = null
    )

    @Test fun explicitMapperPreservesAllFieldsAndNulls() {
        val s = session()
        assertEquals(CollectorSessionEntity(
            s.sessionId, s.collectorVersion, s.protocolVersion, s.observationSchemaVersion,
            s.appVersionName, s.appVersionCode, s.deviceAlias, s.manufacturer, s.model,
            s.deviceCodename, s.androidRelease, s.apiLevel, s.startedUtcIso,
            s.startedElapsedUs, s.requestedScanIntervalMs, s.runLabel
        ), EvidenceRecordMapper.session(s))

        val r = request()
        assertEquals(ScanRequestEntity(
            r.sessionId, r.requestId, r.requestUtcIso, r.requestElapsedUs,
            r.requestedIntervalMs, r.startScanReturned, r.broadcastReceived,
            r.resultsUpdated, r.resultCount, r.completionReason.name,
            r.completedUtcIso, r.completedElapsedUs, r.failureDetail
        ), EvidenceRecordMapper.request(r))

        val o = observation()
        assertEquals(WifiObservationEntity(
            o.observationId, o.sessionId, o.requestId, o.observedUtcIso,
            o.observedElapsedUs, o.ssid, o.bssid, o.rssiDbm, o.frequencyMhz,
            o.channelNumber, o.channelWidth, o.capabilities, o.scanTimestampUs,
            o.scanAgeMs, o.timestampState.name, o.seenAfterRequest
        ), EvidenceRecordMapper.observation(o))

        val e = event()
        assertEquals(CollectorEventEntity(
            e.eventId, e.sessionId, e.eventUtcIso, e.eventElapsedUs,
            e.eventType.name, e.requestId, e.detail
        ), EvidenceRecordMapper.event(e))

        assertEquals(SessionCompletionEntity(
            "SYNTH_A", "2026-10-29T00:00:04Z", "USER_STOP", 1, true
        ), EvidenceRecordMapper.completion(
            "SYNTH_A", "2026-10-29T00:00:04Z", "USER_STOP", 1, true
        ))
    }

    @Test fun roundTripStoresSessionRequestObservationEventAndCompletion() = runBlocking {
        writer.saveSession(session())
        writer.saveRequestBundle(request(), listOf(observation()))
        writer.saveEvent(event())
        writer.saveCompletion("SYNTH_A", "2026-10-29T00:00:04Z", "USER_STOP", 1, true)
        assertEquals(EvidenceRecordMapper.session(session()), dao.findSession("SYNTH_A"))
        assertEquals(listOf(EvidenceRecordMapper.request(request())), dao.requestsForSession("SYNTH_A"))
        assertEquals(listOf(EvidenceRecordMapper.observation(observation())), dao.observationsForSession("SYNTH_A"))
        assertEquals(listOf(EvidenceRecordMapper.event(event())), dao.eventsForSession("SYNTH_A"))
        assertEquals(1, writer.requestCount("SYNTH_A"))
        assertEquals(1, writer.observationCount("SYNTH_A"))
        val completion = dao.completionForSession("SYNTH_A")!!
        assertEquals("USER_STOP", completion.stopReason)
        assertTrue(completion.cleanClose)
        assertNull(dao.observationsForSession("SYNTH_A").single().ssid)
    }

    @Test fun mismatchedResultCountCannotWritePartialRequest() = runBlocking {
        writer.saveSession(session())
        var rejected = false
        try {
            writer.saveRequestBundle(request(count = 2), listOf(observation()))
        } catch (expected: IllegalArgumentException) {
            rejected = true
        }
        assertTrue(rejected)
        assertEquals(0, writer.requestCount("SYNTH_A"))
        assertEquals(0, writer.observationCount("SYNTH_A"))
    }

    @Test fun observationFromWrongRequestCannotBeAttached() = runBlocking {
        writer.saveSession(session())
        var rejected = false
        try {
            writer.saveRequestBundle(request(), listOf(observation(requestId = "REQ_WRONG")))
        } catch (expected: IllegalArgumentException) {
            rejected = true
        }
        assertTrue(rejected)
        assertEquals(0, writer.requestCount("SYNTH_A"))
    }

    @Test fun duplicateObservationRollsBackWholeSecondRequest() = runBlocking {
        writer.saveSession(session())
        writer.saveRequestBundle(request(), listOf(observation()))
        var failed = false
        try {
            writer.saveRequestBundle(
                request(requestId = "REQ_000002"),
                listOf(observation(requestId = "REQ_000002", observationId = "OBS_000001"))
            )
        } catch (expected: Exception) {
            failed = true
        }
        assertTrue("Duplicate observation must fail", failed)
        // The new request must roll back too, not survive as a partial entry.
        assertEquals(1, writer.requestCount("SYNTH_A"))
        assertEquals(1, writer.observationCount("SYNTH_A"))
        assertEquals("REQ_000001", dao.requestsForSession("SYNTH_A").single().requestId)
    }

    @Test fun failedScanWithZeroObservationsIsPreserved() = runBlocking {
        writer.saveSession(session())
        val failedRequest = request(count = 0).copy(
            startScanReturned = false,
            broadcastReceived = false,
            resultsUpdated = null,
            completionReason = ScanCompletionReason.START_SCAN_RETURNED_FALSE,
            failureDetail = "SYNTHETIC_BLOCKED"
        )
        writer.saveRequestBundle(failedRequest, emptyList())
        assertEquals(1, writer.requestCount("SYNTH_A"))
        assertEquals(0, writer.observationCount("SYNTH_A"))
        val stored = dao.requestsForSession("SYNTH_A").single()
        assertEquals("START_SCAN_RETURNED_FALSE", stored.completionReason)
        assertEquals(false, stored.startScanReturned)
        assertNull(stored.resultsUpdated)
        assertEquals("SYNTHETIC_BLOCKED", stored.failureDetail)
        assertFalse(stored.broadcastReceived)
    }
}
