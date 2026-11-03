package com.bhadrirajt.wifitrustfusion.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/** Events may precede request persistence; request_id is not a foreign key. */
@Entity(
    tableName = "collector_events",
    primaryKeys = ["session_id", "event_id"],
    foreignKeys = [ForeignKey(
        entity = CollectorSessionEntity::class,
        parentColumns = ["session_id"],
        childColumns = ["session_id"],
        onDelete = ForeignKey.RESTRICT
    )],
    indices = [Index(value = ["session_id"])]
)
data class CollectorEventEntity(
    @ColumnInfo(name = "event_id") val eventId: String,
    @ColumnInfo(name = "session_id") val sessionId: String,
    @ColumnInfo(name = "event_utc_iso") val eventUtcIso: String,
    @ColumnInfo(name = "event_elapsed_us") val eventElapsedUs: Long,
    @ColumnInfo(name = "event_type") val eventType: String,
    @ColumnInfo(name = "request_id") val requestId: String?,
    @ColumnInfo(name = "detail") val detail: String?
)
