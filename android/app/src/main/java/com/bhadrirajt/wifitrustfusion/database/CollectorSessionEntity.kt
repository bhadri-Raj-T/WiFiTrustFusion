package com.bhadrirajt.wifitrustfusion.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/** One row per collector-generated session. Matches metadata.csv exactly. */
@Entity(tableName = "collector_sessions")
data class CollectorSessionEntity(
    @PrimaryKey @ColumnInfo(name = "session_id") val sessionId: String,
    @ColumnInfo(name = "collector_version") val collectorVersion: String,
    @ColumnInfo(name = "protocol_version") val protocolVersion: String,
    @ColumnInfo(name = "observation_schema_version") val observationSchemaVersion: String,
    @ColumnInfo(name = "app_version_name") val appVersionName: String,
    @ColumnInfo(name = "app_version_code") val appVersionCode: Long,
    @ColumnInfo(name = "device_alias") val deviceAlias: String,
    @ColumnInfo(name = "manufacturer") val manufacturer: String,
    @ColumnInfo(name = "model") val model: String,
    @ColumnInfo(name = "device_codename") val deviceCodename: String,
    @ColumnInfo(name = "android_release") val androidRelease: String,
    @ColumnInfo(name = "api_level") val apiLevel: Int,
    @ColumnInfo(name = "started_utc_iso") val startedUtcIso: String,
    @ColumnInfo(name = "started_elapsed_us") val startedElapsedUs: Long,
    @ColumnInfo(name = "requested_scan_interval_ms") val requestedScanIntervalMs: Long,
    @ColumnInfo(name = "run_label") val runLabel: String?
)
