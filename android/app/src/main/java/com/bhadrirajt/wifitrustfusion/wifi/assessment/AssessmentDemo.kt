package com.bhadrirajt.wifitrustfusion.wifi.assessment

import com.bhadrirajt.wifitrustfusion.wifi.model.ScanCompletionReason
import com.bhadrirajt.wifitrustfusion.wifi.model.ScanRequestRecord
import com.bhadrirajt.wifitrustfusion.wifi.model.TimestampState
import com.bhadrirajt.wifitrustfusion.wifi.model.WifiObservation

/** Synthetic UI example ONLY. Not passed to collector, logger, or Room. */
object AssessmentDemo {
    data class Sample(val request: ScanRequestRecord, val observations: List<WifiObservation>)

    fun sample(): Pair<NetworkIdentityEngine.Result, EvidenceQualityEngine.Result> {
        val identity = NetworkIdentityEngine()
        val baseline = sampleRequest("DEMO_BASELINE")
        identity.evaluate(baseline, listOf(sampleObservation("DEMO_BASELINE", 1L,
            TimestampState.FIRST_SEEN, "[WPA2-PSK-CCMP][ESS]")))
        val current = sampleRequest("DEMO_CURRENT")
        val observations = listOf(
            sampleObservation("DEMO_CURRENT", 2L, TimestampState.ADVANCED, "[ESS]")
        )
        return identity.evaluate(current, observations) to
            EvidenceQualityEngine().evaluate(current, observations)
    }

    private fun sampleRequest(id: String) = ScanRequestRecord(
        sessionId = "SYNTHETIC_DEMO_ONLY", requestId = id,
        requestUtcIso = "2026-01-01T00:00:00Z", requestElapsedUs = 1_000_000L,
        requestedIntervalMs = 35_000L,
        startScanReturned = true, broadcastReceived = true, resultsUpdated = true,
        resultCount = 1, completionReason = ScanCompletionReason.BROADCAST_RESULTS_UPDATED,
        completedUtcIso = "2026-01-01T00:00:01Z", completedElapsedUs = 1_200_000L
    )

    private fun sampleObservation(id: String, timestampMultiplier: Long,
        state: TimestampState, security: String) = WifiObservation(
        observationId = "SYNTHETIC_OBS_$timestampMultiplier",
        sessionId = "SYNTHETIC_DEMO_ONLY", requestId = id,
        observedUtcIso = "2026-01-01T00:00:01Z", observedElapsedUs = 1_300_000L,
        ssid = null, bssid = "02:00:00:00:00:01", rssiDbm = -50,
        frequencyMhz = 2412, channelNumber = 1, channelWidth = null,
        capabilities = security, scanTimestampUs = 1_000_000L + timestampMultiplier * 100L,
        scanAgeMs = 300L, timestampState = state, seenAfterRequest = true
    )
}
