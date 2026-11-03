package com.bhadrirajt.wifitrustfusion.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey

/** Missing completion row must remain distinguishable from clean close. */
@Entity(
    tableName = "session_completions",
    primaryKeys = ["session_id"],
    foreignKeys = [ForeignKey(
        entity = CollectorSessionEntity::class,
        parentColumns = ["session_id"],
        childColumns = ["session_id"],
        onDelete = ForeignKey.RESTRICT
    )]
)
data class SessionCompletionEntity(
    @ColumnInfo(name = "session_id") val sessionId: String,
    @ColumnInfo(name = "ended_utc_iso") val endedUtcIso: String,
    @ColumnInfo(name = "stop_reason") val stopReason: String,
    @ColumnInfo(name = "completed_request_count") val completedRequestCount: Int,
    @ColumnInfo(name = "clean_close") val cleanClose: Boolean
)
