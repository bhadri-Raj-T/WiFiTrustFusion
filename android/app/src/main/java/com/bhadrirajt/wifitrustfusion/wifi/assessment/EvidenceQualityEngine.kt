package com.bhadrirajt.wifitrustfusion.wifi.assessment

import com.bhadrirajt.wifitrustfusion.wifi.model.TimestampState
import com.bhadrirajt.wifitrustfusion.wifi.model.WifiObservation
import com.bhadrirajt.wifitrustfusion.wifi.model.ScanRequestRecord

/** M5 descriptive heuristic v1; no empirically calibrated reliability probability. */
class EvidenceQualityEngine {
    enum class Level { NO_EVIDENCE, LOW, PROVISIONAL, USABLE }
    data class Result(
        val level: Level,
        val observed: Int,
        val advancedRecent: Int,
        val firstSeenRecent: Int,
        val repeatedOrRegressed: Int,
        val untrackable: Int,
        val aged: Int,
        val reasons: List<String>
    )

    companion object {
        const val MAX_HEURISTIC_AGE_MS = 15_000L
    }

    fun evaluate(request: ScanRequestRecord, observations: List<WifiObservation>): Result {
        val reasons = mutableListOf<String>()
        val countMatches = request.resultCount == observations.size
        if (!countMatches) reasons += "Request/observation count mismatch."
        if (request.startScanReturned != true) reasons += "startScan() did not confirm a new request."
        if (!request.broadcastReceived) reasons += "No scan-results broadcast confirmed."
        if (request.resultsUpdated != true) reasons += "Results were not marked updated."

        var advanced = 0
        var first = 0
        var old = 0
        var missing = 0
        var aged = 0
        for (o in observations) {
            if (o.scanAgeMs == null || o.scanAgeMs < 0L ||
                o.scanTimestampUs == null || o.scanTimestampUs <= 0L ||
                o.timestampState == TimestampState.UNTRACKABLE || o.seenAfterRequest == null) {
                missing++
            } else if (o.scanAgeMs > MAX_HEURISTIC_AGE_MS) {
                aged++
            } else if (o.seenAfterRequest != true) {
                old++
            } else when (o.timestampState) {
                TimestampState.ADVANCED -> advanced++
                TimestampState.FIRST_SEEN -> first++
                TimestampState.REPEATED, TimestampState.REGRESSED -> old++
                TimestampState.UNTRACKABLE -> missing++
            }
        }
        val validRequest = countMatches && request.startScanReturned == true &&
            request.broadcastReceived && request.resultsUpdated == true
        val level = when {
            observations.isEmpty() -> Level.NO_EVIDENCE
            !validRequest -> Level.LOW
            advanced > 0 && advanced * 2 >= observations.size -> Level.USABLE
            advanced > 0 || first > 0 -> Level.PROVISIONAL
            else -> Level.LOW
        }
        if (observations.isEmpty()) reasons += "No Wi-Fi observations returned."
        if (first > 0) reasons += "First-seen timestamps lack a prior comparison baseline."
        if (old > 0) reasons += "Some timestamps were repeated, regressed, or before the request."
        if (aged > 0) reasons += "Some observations exceeded the 15-second heuristic age limit."
        if (missing > 0) reasons += "Some observations lacked usable timestamp evidence."
        if (reasons.isEmpty()) reasons += "Updated scan and advancing recent timestamps observed."
        return Result(level, observations.size, advanced, first, old, missing, aged, reasons)
    }
}
