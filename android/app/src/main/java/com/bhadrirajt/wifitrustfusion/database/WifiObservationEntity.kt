package com.bhadrirajt.wifitrustfusion.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/** Raw Wi-Fi identifiers are local sensitive evidence. Never upload this database. */
@Entity(
    tableName = "wifi_observations",
    primaryKeys = ["session_id", "observation_id"],
    foreignKeys = [ForeignKey(
        entity = ScanRequestEntity::class,
        parentColumns = ["session_id", "request_id"],
        childColumns = ["session_id", "request_id"],
        onDelete = ForeignKey.RESTRICT
    )],
    indices = [Index(value = ["session_id", "request_id"])]
)
data class WifiObservationEntity(
    @ColumnInfo(name = "observation_id") val observationId: String,
    @ColumnInfo(name = "session_id") val sessionId: String,
    @ColumnInfo(name = "request_id") val requestId: String,
    @ColumnInfo(name = "observed_utc_iso") val observedUtcIso: String,
    @ColumnInfo(name = "observed_elapsed_us") val observedElapsedUs: Long,
    @ColumnInfo(name = "ssid") val ssid: String?,
    @ColumnInfo(name = "bssid") val bssid: String?,
    @ColumnInfo(name = "rssi_dbm") val rssiDbm: Int,
    @ColumnInfo(name = "frequency_mhz") val frequencyMhz: Int,
    @ColumnInfo(name = "channel_number") val channelNumber: Int?,
    @ColumnInfo(name = "channel_width") val channelWidth: Int?,
    @ColumnInfo(name = "capabilities") val capabilities: String,
    @ColumnInfo(name = "scan_timestamp_us") val scanTimestampUs: Long?,
    @ColumnInfo(name = "scan_age_ms") val scanAgeMs: Long?,
    @ColumnInfo(name = "timestamp_state") val timestampState: String,
    @ColumnInfo(name = "seen_after_request") val seenAfterRequest: Boolean?
)
