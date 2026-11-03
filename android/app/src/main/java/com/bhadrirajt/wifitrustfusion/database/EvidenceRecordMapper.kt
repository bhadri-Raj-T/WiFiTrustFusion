package com.bhadrirajt.wifitrustfusion.database

import com.bhadrirajt.wifitrustfusion.wifi.model.CollectorEventRecord
import com.bhadrirajt.wifitrustfusion.wifi.model.CollectorSessionMetadata
import com.bhadrirajt.wifitrustfusion.wifi.model.ScanRequestRecord
import com.bhadrirajt.wifitrustfusion.wifi.model.WifiObservation

/** Lossless, explicit mapping from M2 collector records to M3 Room v1 entities.
 * No scoring, normalization, SSID/BSSID sanitization, or invented default values.
 * These conversions do NOT read the phone's Wi-Fi state.
 */
object EvidenceRecordMapper {
    fun session(record: CollectorSessionMetadata): CollectorSessionEntity =
        CollectorSessionEntity(
            sessionId = record.sessionId,
            collectorVersion = record.collectorVersion,
            protocolVersion = record.protocolVersion,
            observationSchemaVersion = record.observationSchemaVersion,
            appVersionName = record.appVersionName,
            appVersionCode = record.appVersionCode,
            deviceAlias = record.deviceAlias,
            manufacturer = record.manufacturer,
            model = record.model,
            deviceCodename = record.deviceCodename,
            androidRelease = record.androidRelease,
            apiLevel = record.apiLevel,
            startedUtcIso = record.startedUtcIso,
            startedElapsedUs = record.startedElapsedUs,
            requestedScanIntervalMs = record.requestedScanIntervalMs,
            runLabel = record.runLabel
        )

    fun request(record: ScanRequestRecord): ScanRequestEntity =
        ScanRequestEntity(
            sessionId = record.sessionId,
            requestId = record.requestId,
            requestUtcIso = record.requestUtcIso,
            requestElapsedUs = record.requestElapsedUs,
            requestedIntervalMs = record.requestedIntervalMs,
            startScanReturned = record.startScanReturned,
            broadcastReceived = record.broadcastReceived,
            resultsUpdated = record.resultsUpdated,
            resultCount = record.resultCount,
            completionReason = record.completionReason.name,
            completedUtcIso = record.completedUtcIso,
            completedElapsedUs = record.completedElapsedUs,
            failureDetail = record.failureDetail
        )

    fun observation(record: WifiObservation): WifiObservationEntity =
        WifiObservationEntity(
            observationId = record.observationId,
            sessionId = record.sessionId,
            requestId = record.requestId,
            observedUtcIso = record.observedUtcIso,
            observedElapsedUs = record.observedElapsedUs,
            ssid = record.ssid,
            bssid = record.bssid,
            rssiDbm = record.rssiDbm,
            frequencyMhz = record.frequencyMhz,
            channelNumber = record.channelNumber,
            channelWidth = record.channelWidth,
            capabilities = record.capabilities,
            scanTimestampUs = record.scanTimestampUs,
            scanAgeMs = record.scanAgeMs,
            timestampState = record.timestampState.name,
            seenAfterRequest = record.seenAfterRequest
        )

    fun event(record: CollectorEventRecord): CollectorEventEntity =
        CollectorEventEntity(
            eventId = record.eventId,
            sessionId = record.sessionId,
            eventUtcIso = record.eventUtcIso,
            eventElapsedUs = record.eventElapsedUs,
            eventType = record.eventType.name,
            requestId = record.requestId,
            detail = record.detail
        )

    fun completion(
        sessionId: String,
        endedUtcIso: String,
        stopReason: String,
        completedRequestCount: Int,
        cleanClose: Boolean
    ): SessionCompletionEntity = SessionCompletionEntity(
        sessionId = sessionId,
        endedUtcIso = endedUtcIso,
        stopReason = stopReason,
        completedRequestCount = completedRequestCount,
        cleanClose = cleanClose
    )
}
