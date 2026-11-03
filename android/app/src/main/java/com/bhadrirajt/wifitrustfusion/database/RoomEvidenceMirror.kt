package com.bhadrirajt.wifitrustfusion.database

import android.content.Context
import androidx.room.Room
import com.bhadrirajt.wifitrustfusion.wifi.model.CollectorEventRecord
import com.bhadrirajt.wifitrustfusion.wifi.model.CollectorSessionMetadata
import com.bhadrirajt.wifitrustfusion.wifi.model.ScanRequestRecord
import com.bhadrirajt.wifitrustfusion.wifi.model.WifiObservation
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.runBlocking

/**
 * Development-only, asynchronous Room mirror of already-written CSV evidence.
 * CSV is authoritative. All database work is queued on a single background thread.
 * A Room error never changes the collector's schedule or CSV completion path.
 *
 * This mirror is intentionally NOT a replacement for the frozen G1 collector.
 */
class RoomEvidenceMirror(
    private val database: EvidenceDatabase,
    private val onStatus: (MirrorStatus) -> Unit = {},
    private val ownsDatabase: Boolean = false
) {
    data class MirrorStatus(
        val sessionId: String,
        val state: String,
        val expectedRequests: Int,
        val storedRequests: Int,
        val expectedObservations: Int,
        val storedObservations: Int,
        val expectedEvents: Int,
        val storedEvents: Int,
        val errorCount: Int
    )

    private data class Tally(
        var requests: Int = 0,
        var observations: Int = 0,
        var events: Int = 0,
        var errors: Int = 0
    )

    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "M3-Room-Mirror").apply { isDaemon = false }
    }
    private val gateway = EvidenceWriteGateway(database.evidenceDao())
    private val closed = AtomicBoolean(false)
    private val totals = mutableMapOf<String, Tally>()

    private fun enqueue(action: () -> Unit) {
        check(!closed.get()) { "Room mirror has been closed" }
        executor.execute(action)
    }

    private fun recordFailure(sessionId: String) {
        val tally = totals.getOrPut(sessionId) { Tally() }
        tally.errors++
        // No raw SSID/BSSID, SQL exception messages or network identifiers in callbacks.
        notifyStatus(sessionId, "WRITE_ERROR", tally, -1, -1, -1)
    }

    private fun notifyStatus(
        id: String, state: String, tally: Tally,
        storedRequests: Int, storedObservations: Int, storedEvents: Int
    ) {
        try {
            onStatus(MirrorStatus(
                id, state, tally.requests, storedRequests,
                tally.observations, storedObservations,
                tally.events, storedEvents, tally.errors
            ))
        } catch (_: Exception) {
            // UI notification failure must never affect persistence or CSV acquisition.
        }
    }

    fun recordSession(metadata: CollectorSessionMetadata) {
        enqueue {
            totals.getOrPut(metadata.sessionId) { Tally() }
            try {
                runBlocking { gateway.saveSession(metadata) }
            } catch (_: Exception) {
                recordFailure(metadata.sessionId)
            }
        }
    }

    /** Queue only AFTER request and observations have been written to CSV. */
    fun recordRequest(record: ScanRequestRecord, observations: List<WifiObservation>) {
        // Immutable snapshot to avoid caller mutation between enqueue and write.
        val snapshot = observations.toList()
        enqueue {
            val tally = totals.getOrPut(record.sessionId) { Tally() }
            tally.requests++
            tally.observations += snapshot.size
            try {
                runBlocking { gateway.saveRequestBundle(record, snapshot) }
            } catch (_: Exception) {
                recordFailure(record.sessionId)
            }
        }
    }

    /** Queue only AFTER the matching event has been written to CSV. */
    fun recordEvent(record: CollectorEventRecord) {
        enqueue {
            val tally = totals.getOrPut(record.sessionId) { Tally() }
            tally.events++
            try {
                runBlocking { gateway.saveEvent(record) }
            } catch (_: Exception) {
                recordFailure(record.sessionId)
            }
        }
    }

    /** Queue only AFTER session_completion.csv was successfully written. */
    fun recordCompletion(
        sessionId: String,
        endedUtcIso: String,
        stopReason: String,
        completedRequestCount: Int,
        cleanClose: Boolean
    ) {
        enqueue {
            val tally = totals.getOrPut(sessionId) { Tally() }
            try {
                runBlocking {
                    gateway.saveCompletion(
                        sessionId, endedUtcIso, stopReason,
                        completedRequestCount, cleanClose
                    )
                }
            } catch (_: Exception) {
                recordFailure(sessionId)
            }

            try {
                val dao = database.evidenceDao()
                var storedRequests = -1
                var storedObservations = -1
                var storedEvents = -1
                var completionPresent = false
                runBlocking {
                    storedRequests = dao.requestCount(sessionId)
                    storedObservations = dao.observationCount(sessionId)
                    storedEvents = dao.eventsForSession(sessionId).size
                    completionPresent = dao.completionForSession(sessionId) != null
                }
                val consistent = tally.errors == 0 && cleanClose && completionPresent &&
                    storedRequests == tally.requests &&
                    storedObservations == tally.observations &&
                    storedEvents == tally.events &&
                    completedRequestCount == tally.requests
                notifyStatus(
                    sessionId, if (consistent) "MATCH" else "REVIEW_REQUIRED",
                    tally, storedRequests, storedObservations, storedEvents
                )
            } catch (_: Exception) {
                recordFailure(sessionId)
                notifyStatus(sessionId, "REVIEW_REQUIRED", tally, -1, -1, -1)
            }
        }
    }

    /**
     * Read after earlier queued mirror writes on the same executor.
     * Callback runs on worker thread; caller must marshal to UI thread.
     * This reports mirrored data, NOT a CSV evidence audit.
     */
    fun readRecentHistory(
        limit: Int = 10,
        onResult: (Result<List<SessionHistoryRow>>) -> Unit
    ) {
        require(limit in 1..25) { "History limit must be 1..25" }
        enqueue {
            val result = runCatching {
                runBlocking { database.evidenceDao().recentSessionSummaries(limit) }
            }
            try { onResult(result) } catch (_: Exception) {
                // UI callback must not affect queued evidence writes.
            }
        }
    }

    /** Drain previously queued work; do not cancel outstanding database writes. */
    fun shutdown() {
        if (closed.compareAndSet(false, true)) {
            executor.execute {
                if (ownsDatabase) database.close()
            }
            executor.shutdown()
        }
    }

    companion object {
        fun open(context: Context, onStatus: (MirrorStatus) -> Unit): RoomEvidenceMirror {
            val db = Room.databaseBuilder(
                context.applicationContext,
                EvidenceDatabase::class.java,
                EvidenceDatabase.DATABASE_NAME
            ).build()
            return RoomEvidenceMirror(db, onStatus, ownsDatabase = true)
        }
    }
}
