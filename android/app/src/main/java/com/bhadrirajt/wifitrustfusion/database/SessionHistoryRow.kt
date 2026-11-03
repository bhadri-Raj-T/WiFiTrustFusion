package com.bhadrirajt.wifitrustfusion.database

/** Aggregate Room-mirror history. No SSID, BSSID or raw scan result is queried. */
data class SessionHistoryRow(
    val sessionId: String,
    val startedUtcIso: String,
    val requestCount: Int,
    val observationCount: Int,
    val stopReason: String?,
    val cleanClose: Boolean?
)
