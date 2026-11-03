package com.bhadrirajt.wifitrustfusion.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/** Request IDs reset across sessions, so their primary key is composite. */
@Entity(
    tableName = "scan_requests",
    primaryKeys = ["session_id", "request_id"],
    foreignKeys = [ForeignKey(
        entity = CollectorSessionEntity::class,
        parentColumns = ["session_id"],
        childColumns = ["session_id"],
        onDelete = ForeignKey.RESTRICT
    )],
    indices = [Index(value = ["session_id"])]
)
data class ScanRequestEntity(
    @ColumnInfo(name = "session_id") val sessionId: String,
    @ColumnInfo(name = "request_id") val requestId: String,
    @ColumnInfo(name = "request_utc_iso") val requestUtcIso: String,
    @ColumnInfo(name = "request_elapsed_us") val requestElapsedUs: Long,
    @ColumnInfo(name = "requested_interval_ms") val requestedIntervalMs: Long,
    @ColumnInfo(name = "start_scan_returned") val startScanReturned: Boolean?,
    @ColumnInfo(name = "broadcast_received") val broadcastReceived: Boolean,
    @ColumnInfo(name = "results_updated") val resultsUpdated: Boolean?,
    @ColumnInfo(name = "result_count") val resultCount: Int,
    @ColumnInfo(name = "completion_reason") val completionReason: String,
    @ColumnInfo(name = "completed_utc_iso") val completedUtcIso: String?,
    @ColumnInfo(name = "completed_elapsed_us") val completedElapsedUs: Long?,
    @ColumnInfo(name = "failure_detail") val failureDetail: String?
)
