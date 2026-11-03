package com.bhadrirajt.wifitrustfusion.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
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
 * M3.2 instrumentation tests. ALL records are synthetic and stored in RAM only.
 * No real scan, real SSID/BSSID, persistent database or CSV is read or written.
 */
@RunWith(AndroidJUnit4::class)
class EvidenceDatabaseIntegrityTest {
    private lateinit var database: EvidenceDatabase
    private lateinit var dao: EvidenceDao

    @Before
    fun openDatabase() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, EvidenceDatabase::class.java)
            .build()
        dao = database.evidenceDao()
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    private fun session(id: String) = CollectorSessionEntity(
        sessionId = id,
        collectorVersion = "COLLECTOR_TEST_ONLY",
        protocolVersion = "PROTOCOL_TEST_ONLY",
        observationSchemaVersion = "OBS_SCHEMA_TEST_ONLY",
        appVersionName = "synthetic-test",
        appVersionCode = 1L,
        deviceAlias = "SYNTHETIC_DEVICE",
        manufacturer = "TEST",
        model = "TEST_MODEL",
        deviceCodename = "test_device",
        androidRelease = "test",
        apiLevel = 27,
        startedUtcIso = "2026-10-28T00:00:00Z",
        startedElapsedUs = 1_000_000L,
        requestedScanIntervalMs = 35_000L,
        runLabel = null
    )

    private fun request(sessionId: String, requestId: String = "REQ_000001") = ScanRequestEntity(
        sessionId = sessionId,
        requestId = requestId,
        requestUtcIso = "2026-10-28T00:00:01Z",
        requestElapsedUs = 2_000_000L,
        requestedIntervalMs = 35_000L,
        startScanReturned = true,
        broadcastReceived = true,
        resultsUpdated = true,
        resultCount = 1,
        completionReason = "BROADCAST_RESULTS_UPDATED",
        completedUtcIso = "2026-10-28T00:00:02Z",
        completedElapsedUs = 3_000_000L,
        failureDetail = null
    )

    private fun observation(
        sessionId: String,
        requestId: String = "REQ_000001",
        observationId: String = "OBS_000001"
    ) = WifiObservationEntity(
        observationId = observationId,
        sessionId = sessionId,
        requestId = requestId,
        observedUtcIso = "2026-10-28T00:00:02Z",
        observedElapsedUs = 3_000_000L,
        ssid = null,
        bssid = null,
        rssiDbm = -57,
        frequencyMhz = 2412,
        channelNumber = 1,
        channelWidth = null,
        capabilities = "[TEST_ONLY]",
        scanTimestampUs = 2_750_000L,
        scanAgeMs = 250L,
        timestampState = "FIRST_SEEN",
        seenAfterRequest = true
    )

    private fun event(sessionId: String, eventId: String = "EVT_000001") =
        CollectorEventEntity(
            eventId = eventId,
            sessionId = sessionId,
            eventUtcIso = "2026-10-28T00:00:00Z",
            eventElapsedUs = 1_000_000L,
            eventType = "SESSION_STARTED",
            requestId = null,
            detail = null
        )

    private fun completion(sessionId: String) = SessionCompletionEntity(
        sessionId = sessionId,
        endedUtcIso = "2026-10-28T00:00:03Z",
        stopReason = "USER_STOP",
        completedRequestCount = 1,
        cleanClose = true
    )

    /** Avoid accepting arbitrary exceptions as evidence of SQLite integrity. */
    private suspend fun expectConstraintViolation(action: suspend () -> Unit) {
        var caught: Exception? = null
        try {
            action()
        } catch (error: Exception) {
            caught = error
        }
        assertNotNull("Expected a database constraint violation", caught)
        val message = buildString {
            var cause: Throwable? = caught
            while (cause != null) {
                append(cause.javaClass.name).append(' ').append(cause.message).append(' ')
                cause = cause.cause
            }
        }
        assertTrue(
            "Expected constraint-related exception, got: $message",
            message.contains("constraint", ignoreCase = true) ||
                message.contains("foreign key", ignoreCase = true) ||
                message.contains("unique", ignoreCase = true) ||
                message.contains("primary key", ignoreCase = true)
        )
    }

    @Test
    fun storesAndRetrievesCompleteSyntheticSession() = runBlocking {
        val session = session("SYNTH_A")
        val request = request("SYNTH_A")
        val observation = observation("SYNTH_A")
        val event = event("SYNTH_A")
        val completion = completion("SYNTH_A")

        dao.insertSession(session)
        dao.insertRequest(request)
        dao.insertObservations(listOf(observation))
        dao.insertEvent(event)
        dao.insertCompletion(completion)

        assertEquals(session, dao.findSession("SYNTH_A"))
        assertEquals(listOf(request), dao.requestsForSession("SYNTH_A"))
        assertEquals(listOf(observation), dao.observationsForSession("SYNTH_A"))
        assertEquals(listOf(event), dao.eventsForSession("SYNTH_A"))
        assertEquals(completion, dao.completionForSession("SYNTH_A"))
        assertEquals(1, dao.requestCount("SYNTH_A"))
        assertEquals(1, dao.observationCount("SYNTH_A"))
        assertNull(dao.findSession("MISSING"))
        assertNull(dao.observationsForSession("SYNTH_A").single().ssid)
        assertNull(dao.observationsForSession("SYNTH_A").single().bssid)
    }

    @Test
    fun preservesCompositeKeysAcrossDifferentSessions() = runBlocking {
        dao.insertSession(session("SYNTH_A"))
        dao.insertSession(session("SYNTH_B"))
        dao.insertRequest(request("SYNTH_A", "REQ_000001"))
        dao.insertRequest(request("SYNTH_B", "REQ_000001"))
        dao.insertObservations(listOf(observation("SYNTH_A")))
        dao.insertObservations(listOf(observation("SYNTH_B")))

        assertEquals(1, dao.requestCount("SYNTH_A"))
        assertEquals(1, dao.requestCount("SYNTH_B"))
        assertEquals(1, dao.observationCount("SYNTH_A"))
        assertEquals(1, dao.observationCount("SYNTH_B"))
        assertEquals("SYNTH_B", dao.observationsForSession("SYNTH_B").single().sessionId)
    }

    @Test
    fun duplicateInsertsAbortAndDoNotOverwriteEvidence() = runBlocking {
        val originalSession = session("SYNTH_A")
        val originalRequest = request("SYNTH_A")
        val originalObservation = observation("SYNTH_A")
        val originalEvent = event("SYNTH_A")
        val originalCompletion = completion("SYNTH_A")

        dao.insertSession(originalSession)
        dao.insertRequest(originalRequest)
        dao.insertObservations(listOf(originalObservation))
        dao.insertEvent(originalEvent)
        dao.insertCompletion(originalCompletion)

        expectConstraintViolation {
            dao.insertSession(originalSession.copy(deviceAlias = "INVALID_REPLACEMENT"))
        }
        expectConstraintViolation {
            dao.insertRequest(originalRequest.copy(resultCount = 999))
        }
        expectConstraintViolation {
            dao.insertObservations(listOf(originalObservation.copy(rssiDbm = -1)))
        }
        expectConstraintViolation {
            dao.insertEvent(originalEvent.copy(eventType = "INVALID_REPLACEMENT"))
        }
        expectConstraintViolation {
            dao.insertCompletion(originalCompletion.copy(cleanClose = false))
        }

        assertEquals(originalSession, dao.findSession("SYNTH_A"))
        assertEquals(originalRequest, dao.requestsForSession("SYNTH_A").single())
        assertEquals(originalObservation, dao.observationsForSession("SYNTH_A").single())
        assertEquals(originalEvent, dao.eventsForSession("SYNTH_A").single())
        assertEquals(originalCompletion, dao.completionForSession("SYNTH_A"))
    }

    @Test
    fun foreignKeysRejectOrphanRequestsObservationsEventsAndCompletion() = runBlocking {
        expectConstraintViolation { dao.insertRequest(request("MISSING_SESSION")) }
        expectConstraintViolation { dao.insertEvent(event("MISSING_SESSION")) }
        expectConstraintViolation { dao.insertCompletion(completion("MISSING_SESSION")) }

        dao.insertSession(session("SYNTH_A"))
        expectConstraintViolation {
            dao.insertObservations(listOf(observation("SYNTH_A", "MISSING_REQUEST")))
        }

        // Composite FK must not be satisfied by another session's same request ID.
        dao.insertSession(session("SYNTH_B"))
        dao.insertRequest(request("SYNTH_B"))
        expectConstraintViolation {
            dao.insertObservations(listOf(observation("SYNTH_A", "REQ_000001")))
        }
        assertEquals(0, dao.requestCount("SYNTH_A"))
        assertEquals(0, dao.observationCount("SYNTH_A"))
    }

    @Test
    fun duplicateObservationIdInSameSessionIsRejectedAcrossRequests() = runBlocking {
        dao.insertSession(session("SYNTH_A"))
        dao.insertRequest(request("SYNTH_A", "REQ_000001"))
        dao.insertRequest(request("SYNTH_A", "REQ_000002").copy(requestElapsedUs = 4_000_000L))
        val original = observation("SYNTH_A", "REQ_000001", "OBS_000001")
        dao.insertObservations(listOf(original))
        expectConstraintViolation {
            dao.insertObservations(listOf(observation("SYNTH_A", "REQ_000002", "OBS_000001")))
        }
        assertEquals(1, dao.observationCount("SYNTH_A"))
        assertEquals(original, dao.observationsForSession("SYNTH_A").single())
    }

    @Test
    fun observationBatchRejectsDuplicatesAtomically() = runBlocking {
        dao.insertSession(session("SYNTH_A"))
        dao.insertRequest(request("SYNTH_A"))
        val existing = observation("SYNTH_A", observationId = "OBS_000001")
        dao.insertObservations(listOf(existing))

        expectConstraintViolation {
            dao.insertObservations(
                listOf(
                    observation("SYNTH_A", observationId = "OBS_000002"),
                    existing.copy(rssiDbm = -1)
                )
            )
        }
        // @Insert(List) is transactional; first row must not be half-committed.
        assertEquals(listOf(existing), dao.observationsForSession("SYNTH_A"))
    }

    @Test
    fun unavailableScanAndMissingCompletionAreNotRecordedAsCleanSuccess() = runBlocking {
        dao.insertSession(session("SYNTH_A"))
        assertNull(dao.completionForSession("SYNTH_A"))
        val blocked = request("SYNTH_A").copy(
            startScanReturned = null,
            broadcastReceived = false,
            resultsUpdated = null,
            resultCount = 0,
            completionReason = "WIFI_DISABLED",
            completedUtcIso = null,
            completedElapsedUs = null,
            failureDetail = "SYNTHETIC_WIFI_DISABLED"
        )
        dao.insertRequest(blocked)
        assertEquals(blocked, dao.requestsForSession("SYNTH_A").single())
        assertEquals(0, dao.observationCount("SYNTH_A"))
        assertNull(dao.requestsForSession("SYNTH_A").single().resultsUpdated)
        assertFalse(dao.requestsForSession("SYNTH_A").single().broadcastReceived)

        val notClean = completion("SYNTH_A").copy(
            stopReason = "COLLECTOR_ERROR", cleanClose = false
        )
        dao.insertCompletion(notClean)
        assertEquals(notClean, dao.completionForSession("SYNTH_A"))
        assertFalse(dao.completionForSession("SYNTH_A")!!.cleanClose)
    }

    @Test
    fun nullAndUntrackableFieldsSurviveRoundTrip() = runBlocking {
        dao.insertSession(session("SYNTH_A"))
        dao.insertRequest(request("SYNTH_A"))
        val unavailable = observation("SYNTH_A").copy(
            ssid = null,
            bssid = null,
            channelNumber = null,
            channelWidth = null,
            scanTimestampUs = null,
            scanAgeMs = null,
            timestampState = "UNTRACKABLE",
            seenAfterRequest = null
        )
        dao.insertObservations(listOf(unavailable))
        assertEquals(unavailable, dao.observationsForSession("SYNTH_A").single())
    }
}
