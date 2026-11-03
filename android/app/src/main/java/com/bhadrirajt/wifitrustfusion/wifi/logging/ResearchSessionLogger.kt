package com.bhadrirajt.wifitrustfusion.wifi.logging

import android.content.Context
import com.bhadrirajt.wifitrustfusion.wifi.model.CollectorEventRecord
import com.bhadrirajt.wifitrustfusion.wifi.model.CollectorSessionMetadata
import com.bhadrirajt.wifitrustfusion.wifi.model.ScanRequestRecord
import com.bhadrirajt.wifitrustfusion.wifi.model.WifiObservation
import java.io.BufferedWriter
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.time.Instant

class ResearchSessionLogger(
    context: Context
) {

    private val appContext =
        context.applicationContext

    private var sessionDirectory:
        File? = null

    private var metadataWriter:
        BufferedWriter? = null

    private var requestsWriter:
        BufferedWriter? = null

    private var observationsWriter:
        BufferedWriter? = null

    private var eventsWriter:
        BufferedWriter? = null

    private var activeSessionId:
        String? = null

    @Synchronized
    fun startSession(
        metadata: CollectorSessionMetadata
    ): File {

        close()

        val baseDirectory =
            appContext.getExternalFilesDir(
                "research_sessions"
            )
                ?: File(
                    appContext.filesDir,
                    "research_sessions"
                )

        if (
            !baseDirectory.exists() &&
            !baseDirectory.mkdirs()
        ) {
            throw IllegalStateException(
                "Unable to create research session base directory."
            )
        }

        val safeSessionId =
            sanitizeFileName(
                metadata.sessionId
            )

        val directory =
            File(
                baseDirectory,
                safeSessionId
            )

        if (
            directory.exists()
        ) {
            throw IllegalStateException(
                "Session directory already exists: $safeSessionId"
            )
        }

        if (
            !directory.mkdirs()
        ) {
            throw IllegalStateException(
                "Unable to create session directory."
            )
        }

        sessionDirectory =
            directory

        activeSessionId =
            metadata.sessionId

        metadataWriter =
            openWriter(
                File(
                    directory,
                    "metadata.csv"
                )
            )

        requestsWriter =
            openWriter(
                File(
                    directory,
                    "requests.csv"
                )
            )

        observationsWriter =
            openWriter(
                File(
                    directory,
                    "observations.csv"
                )
            )

        eventsWriter =
            openWriter(
                File(
                    directory,
                    "events.csv"
                )
            )

        writeMetadataHeader()
        writeRequestHeader()
        writeObservationHeader()
        writeEventHeader()

        writeMetadata(
            metadata
        )

        return directory
    }

    @Synchronized
    fun logRequest(
        request: ScanRequestRecord
    ) {

        requireActiveSession(
            request.sessionId
        )

        val writer =
            requestsWriter
                ?: error(
                    "Requests writer is not available."
                )

        writeCsvRow(
            writer,
            listOf(
                request.sessionId,
                request.requestId,
                request.requestUtcIso,
                request.requestElapsedUs,
                request.requestedIntervalMs,
                request.startScanReturned,
                request.broadcastReceived,
                request.resultsUpdated,
                request.resultCount,
                request.completionReason.name,
                request.completedUtcIso,
                request.completedElapsedUs,
                request.failureDetail
            )
        )
    }

    @Synchronized
    fun logObservations(
        observations: List<WifiObservation>
    ) {

        if (
            observations.isEmpty()
        ) {
            return
        }

        val sessionId =
            observations.first()
                .sessionId

        requireActiveSession(
            sessionId
        )

        if (
            observations.any {
                it.sessionId !=
                    sessionId
            }
        ) {
            throw IllegalArgumentException(
                "Observation batch contains multiple session IDs."
            )
        }

        val writer =
            observationsWriter
                ?: error(
                    "Observations writer is not available."
                )

        observations.forEach {
                observation ->

            writeCsvRow(
                writer,
                listOf(
                    observation.observationId,
                    observation.sessionId,
                    observation.requestId,
                    observation.observedUtcIso,
                    observation.observedElapsedUs,
                    observation.ssid,
                    observation.bssid,
                    observation.rssiDbm,
                    observation.frequencyMhz,
                    observation.channelNumber,
                    observation.channelWidth,
                    observation.capabilities,
                    observation.scanTimestampUs,
                    observation.scanAgeMs,
                    observation.timestampState.name,
                    observation.seenAfterRequest
                )
            )
        }
    }

    @Synchronized
    fun logEvent(
        event: CollectorEventRecord
    ) {

        requireActiveSession(
            event.sessionId
        )

        val writer =
            eventsWriter
                ?: error(
                    "Events writer is not available."
                )

        writeCsvRow(
            writer,
            listOf(
                event.eventId,
                event.sessionId,
                event.eventUtcIso,
                event.eventElapsedUs,
                event.eventType.name,
                event.requestId,
                event.detail
            )
        )
    }

    @Synchronized
    fun markSessionComplete(
        stopReason: String,
        completedRequestCount: Int,
        endedUtcIso: String = Instant.now().toString(),
        cleanClose: Boolean = stopReason == "USER_STOP"
    ) {

        val directory =
            sessionDirectory
                ?: error(
                    "No active session directory."
                )

        val sessionId =
            activeSessionId
                ?: error(
                    "No active session ID."
                )

        val completionFile =
            File(
                directory,
                "session_completion.csv"
            )

        // Never overwrite existing completion evidence.
        check(!completionFile.exists()) {
            "Completion record already exists."
        }

        // A caller cannot label a failure or unknown stop as clean.
        val verifiedCleanClose =
            cleanClose && stopReason == "USER_STOP"

        openWriter(
            completionFile
        ).use {
                writer ->

            writeCsvRow(
                writer,
                listOf(
                    "session_id",
                    "ended_utc_iso",
                    "stop_reason",
                    "completed_request_count",
                    "clean_close"
                )
            )

            writeCsvRow(
                writer,
                listOf(
                    sessionId,
                    endedUtcIso,
                    stopReason,
                    completedRequestCount,
                    verifiedCleanClose
                )
            )
        }
    }

    @Synchronized
    fun getSessionDirectory():
        File? {

        return sessionDirectory
    }

    @Synchronized
    fun isActive():
        Boolean {

        return activeSessionId != null
    }

    @Synchronized
    fun close() {

        try {
            metadataWriter?.flush()
        } catch (_: Exception) {
        }

        try {
            requestsWriter?.flush()
        } catch (_: Exception) {
        }

        try {
            observationsWriter?.flush()
        } catch (_: Exception) {
        }

        try {
            eventsWriter?.flush()
        } catch (_: Exception) {
        }

        try {
            metadataWriter?.close()
        } catch (_: Exception) {
        }

        try {
            requestsWriter?.close()
        } catch (_: Exception) {
        }

        try {
            observationsWriter?.close()
        } catch (_: Exception) {
        }

        try {
            eventsWriter?.close()
        } catch (_: Exception) {
        }

        metadataWriter =
            null

        requestsWriter =
            null

        observationsWriter =
            null

        eventsWriter =
            null

        activeSessionId =
            null

        sessionDirectory =
            null
    }

    private fun writeMetadata(
        metadata: CollectorSessionMetadata
    ) {

        val writer =
            metadataWriter
                ?: error(
                    "Metadata writer is not available."
                )

        writeCsvRow(
            writer,
            listOf(
                metadata.sessionId,
                metadata.collectorVersion,
                metadata.protocolVersion,
                metadata.observationSchemaVersion,
                metadata.appVersionName,
                metadata.appVersionCode,
                metadata.deviceAlias,
                metadata.manufacturer,
                metadata.model,
                metadata.deviceCodename,
                metadata.androidRelease,
                metadata.apiLevel,
                metadata.startedUtcIso,
                metadata.startedElapsedUs,
                metadata.requestedScanIntervalMs,
                metadata.runLabel
            )
        )
    }

    private fun writeMetadataHeader() {

        val writer =
            metadataWriter
                ?: error(
                    "Metadata writer is not available."
                )

        writeCsvRow(
            writer,
            listOf(
                "session_id",
                "collector_version",
                "protocol_version",
                "observation_schema_version",
                "app_version_name",
                "app_version_code",
                "device_alias",
                "manufacturer",
                "model",
                "device_codename",
                "android_release",
                "api_level",
                "started_utc_iso",
                "started_elapsed_us",
                "requested_scan_interval_ms",
                "run_label"
            )
        )
    }

    private fun writeRequestHeader() {

        val writer =
            requestsWriter
                ?: error(
                    "Requests writer is not available."
                )

        writeCsvRow(
            writer,
            listOf(
                "session_id",
                "request_id",
                "request_utc_iso",
                "request_elapsed_us",
                "requested_interval_ms",
                "start_scan_returned",
                "broadcast_received",
                "results_updated",
                "result_count",
                "completion_reason",
                "completed_utc_iso",
                "completed_elapsed_us",
                "failure_detail"
            )
        )
    }

    private fun writeObservationHeader() {

        val writer =
            observationsWriter
                ?: error(
                    "Observations writer is not available."
                )

        writeCsvRow(
            writer,
            listOf(
                "observation_id",
                "session_id",
                "request_id",
                "observed_utc_iso",
                "observed_elapsed_us",
                "ssid",
                "bssid",
                "rssi_dbm",
                "frequency_mhz",
                "channel_number",
                "channel_width",
                "capabilities",
                "scan_timestamp_us",
                "scan_age_ms",
                "timestamp_state",
                "seen_after_request"
            )
        )
    }

    private fun writeEventHeader() {

        val writer =
            eventsWriter
                ?: error(
                    "Events writer is not available."
                )

        writeCsvRow(
            writer,
            listOf(
                "event_id",
                "session_id",
                "event_utc_iso",
                "event_elapsed_us",
                "event_type",
                "request_id",
                "detail"
            )
        )
    }

    private fun openWriter(
        file: File
    ): BufferedWriter {

        return BufferedWriter(
            OutputStreamWriter(
                FileOutputStream(
                    file,
                    false
                ),
                Charsets.UTF_8
            )
        )
    }

    private fun writeCsvRow(
        writer: BufferedWriter,
        values: List<Any?>
    ) {

        val row =
            values.joinToString(
                separator = ","
            ) {
                    value ->

                csvEscape(
                    value
                )
            }

        writer.write(
            row
        )

        writer.newLine()

        /*
         * Flush every row.
         *
         * Collection volume is deliberately low.
         * Experimental recoverability is more important
         * than maximizing write throughput.
         */
        writer.flush()
    }

    private fun csvEscape(
        value: Any?
    ): String {

        if (
            value == null
        ) {
            return ""
        }

        val text =
            value.toString()

        val escaped =
            text.replace(
                "\"",
                "\"\""
            )

        return if (
            escaped.contains(",") ||
            escaped.contains("\"") ||
            escaped.contains("\n") ||
            escaped.contains("\r")
        ) {
            "\"$escaped\""
        } else {
            escaped
        }
    }

    private fun requireActiveSession(
        sessionId: String
    ) {

        val active =
            activeSessionId
                ?: throw IllegalStateException(
                    "No research session is active."
                )

        if (
            active !=
            sessionId
        ) {
            throw IllegalArgumentException(
                "Session mismatch. Active=$active, received=$sessionId"
            )
        }
    }

    private fun sanitizeFileName(
        value: String
    ): String {

        return value
            .replace(
                Regex(
                    "[^A-Za-z0-9._-]"
                ),
                "_"
            )
            .take(120)
    }
}
