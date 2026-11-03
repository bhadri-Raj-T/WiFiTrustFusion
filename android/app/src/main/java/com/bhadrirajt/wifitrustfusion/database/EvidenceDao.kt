package com.bhadrirajt.wifitrustfusion.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

/** Strict ABORT inserts: never silently replace research evidence. */
@Dao
interface EvidenceDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertSession(session: CollectorSessionEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertRequest(request: ScanRequestEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertObservations(observations: List<WifiObservationEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertEvent(event: CollectorEventEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertCompletion(completion: SessionCompletionEntity)

    /** One request and its observations are one atomic write unit. */
    @Transaction
    suspend fun insertRequestBundle(
        request: ScanRequestEntity,
        observations: List<WifiObservationEntity>
    ) {
        require(request.resultCount == observations.size && request.resultCount >= 0) {
            "Request result count does not match observation rows"
        }
        require(observations.all {
            it.sessionId == request.sessionId && it.requestId == request.requestId
        }) { "Observation belongs to different request" }
        insertRequest(request)
        if (observations.isNotEmpty()) {
            insertObservations(observations)
        }
    }

    @Query("SELECT * FROM collector_sessions WHERE session_id = :sessionId LIMIT 1")
    suspend fun findSession(sessionId: String): CollectorSessionEntity?

    @Query("SELECT * FROM scan_requests WHERE session_id = :sessionId ORDER BY request_elapsed_us")
    suspend fun requestsForSession(sessionId: String): List<ScanRequestEntity>

    @Query("SELECT * FROM wifi_observations WHERE session_id = :sessionId ORDER BY observed_elapsed_us")
    suspend fun observationsForSession(sessionId: String): List<WifiObservationEntity>

    @Query("SELECT * FROM collector_events WHERE session_id = :sessionId ORDER BY event_elapsed_us")
    suspend fun eventsForSession(sessionId: String): List<CollectorEventEntity>

    @Query("SELECT * FROM session_completions WHERE session_id = :sessionId LIMIT 1")
    suspend fun completionForSession(sessionId: String): SessionCompletionEntity?

    /** Read-only, privacy-minimizing session summary, newest first. */
    @Query("""
        SELECT s.session_id AS sessionId,
               s.started_utc_iso AS startedUtcIso,
               (SELECT COUNT(*) FROM scan_requests r
                WHERE r.session_id = s.session_id) AS requestCount,
               (SELECT COUNT(*) FROM wifi_observations o
                WHERE o.session_id = s.session_id) AS observationCount,
               c.stop_reason AS stopReason,
               c.clean_close AS cleanClose
        FROM collector_sessions s
        LEFT JOIN session_completions c ON c.session_id = s.session_id
        ORDER BY s.started_utc_iso DESC, s.session_id DESC
        LIMIT :limit
    """)
    suspend fun recentSessionSummaries(limit: Int): List<SessionHistoryRow>

    @Query("SELECT COUNT(*) FROM scan_requests WHERE session_id = :sessionId")
    suspend fun requestCount(sessionId: String): Int

    @Query("SELECT COUNT(*) FROM wifi_observations WHERE session_id = :sessionId")
    suspend fun observationCount(sessionId: String): Int
}
