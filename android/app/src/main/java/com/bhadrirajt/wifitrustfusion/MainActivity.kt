package com.bhadrirajt.wifitrustfusion

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.bhadrirajt.wifitrustfusion.core.ProjectVersions
import com.bhadrirajt.wifitrustfusion.database.RoomEvidenceMirror
import com.bhadrirajt.wifitrustfusion.database.SessionHistoryFormatter
import com.bhadrirajt.wifitrustfusion.ui.ReviewDashboardFormatter
import com.bhadrirajt.wifitrustfusion.wifi.assessment.NetworkIdentityEngine
import com.bhadrirajt.wifitrustfusion.wifi.assessment.EvidenceQualityEngine
import com.bhadrirajt.wifitrustfusion.wifi.assessment.AssessmentDemo
import com.bhadrirajt.wifitrustfusion.wifi.freshness.WifiFreshnessEngine
import com.bhadrirajt.wifitrustfusion.wifi.logging.ResearchSessionLogger
import com.bhadrirajt.wifitrustfusion.wifi.model.CollectorEventRecord
import com.bhadrirajt.wifitrustfusion.wifi.model.CollectorEventType
import com.bhadrirajt.wifitrustfusion.wifi.model.CollectorSessionMetadata
import com.bhadrirajt.wifitrustfusion.wifi.model.ScanCompletionReason
import com.bhadrirajt.wifitrustfusion.wifi.model.TimestampState
import com.bhadrirajt.wifitrustfusion.wifi.model.WifiObservation
import com.bhadrirajt.wifitrustfusion.wifi.permission.PermissionCoordinator
import com.bhadrirajt.wifitrustfusion.wifi.scanner.WifiScanBatch
import com.bhadrirajt.wifitrustfusion.wifi.scanner.WifiScanCollector
import com.bhadrirajt.wifitrustfusion.wifi.session.CollectorSessionController
import java.io.File
import java.time.Instant

class MainActivity :
    AppCompatActivity(),
    WifiScanCollector.Listener,
    CollectorSessionController.Listener {

    companion object {

        private const val DEVICE_ALIAS = "J7_M3DEV"
    }

    private lateinit var permissionCoordinator:
        PermissionCoordinator

    private lateinit var scanCollector:
        WifiScanCollector

    private lateinit var sessionController:
        CollectorSessionController

    private lateinit var roomMirror: RoomEvidenceMirror

    private lateinit var researchLogger:
        ResearchSessionLogger

    private val freshnessEngine =
        WifiFreshnessEngine()

    // M4/M5 analysis is a read-only interpretation layer; G1 collector unchanged.
    private val identityEngine = NetworkIdentityEngine()
    private val qualityEngine = EvidenceQualityEngine()
    private lateinit var assessmentModeText: TextView
    private lateinit var assessmentQualityText: TextView
    private lateinit var assessmentIdentityText: TextView
    private lateinit var sessionHistoryText: TextView

    // M7/M8: display-only review state, never written to research evidence.
    private lateinit var reviewSummaryText: TextView
    private var reviewSource = ReviewDashboardFormatter.Source.NONE
    private var reviewQuality: String? = null
    private var reviewObservations = 0
    private var reviewSecurityChanges = 0
    private var reviewBandChanges = 0
    private var reviewRoomState: String? = null
    private var reviewHistoryLoaded = false

    // Presentation UI only: navigation never changes evidence or scanner logic.
    private val pageContainers = intArrayOf(
        R.id.pageOverview, R.id.pageScan, R.id.pageHistory, R.id.pageDetails
    )
    private val pageTabs = intArrayOf(
        R.id.tabOverview, R.id.tabScan, R.id.tabHistory, R.id.tabDetails
    )
    private var activeUiPage = 0

    private lateinit var diagnosticText:
        TextView

    private lateinit var sessionText:
        TextView

    private lateinit var scanStatusText:
        TextView

    private lateinit var scanResultsText:
        TextView

    private lateinit var startSessionButton:
        Button

    private lateinit var stopSessionButton:
        Button

    private lateinit var singleScanButton:
        Button

    private var latestRequestId:
        String? = null

    private var latestResultCount =
        0

    private var latestFirstSeen =
        0

    private var latestAdvanced =
        0

    private var latestRepeated =
        0

    private var latestRegressed =
        0

    private var latestUntrackable =
        0

    private var latestSeenAfterRequest =
        0

    private var eventCounter =
        0L

    private var sessionLoggingReady =
        false

    // Development branch: failure latch persists until the stop callback.
    private var sessionHadCsvError =
        false

    private var currentEvidenceDirectory:
        File? = null

    private val locationPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts
                .RequestMultiplePermissions()
        ) {
            renderDiagnostics()
        }

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {

        super.onCreate(
            savedInstanceState
        )

        setContentView(
            R.layout.activity_main
        )

        permissionCoordinator =
            PermissionCoordinator(
                this
            )

        scanCollector =
            WifiScanCollector(
                context =
                    this,
                permissionCoordinator =
                    permissionCoordinator,
                listener =
                    this
            )

        sessionController =
            CollectorSessionController(
                listener =
                    this
            )

        researchLogger =
            ResearchSessionLogger(
                this
            )

        roomMirror = RoomEvidenceMirror.open(this) { status ->
            runOnUiThread {
                if (!isDestroyed && ::scanStatusText.isInitialized) {
                    scanStatusText.text =
                        "ROOM MIRROR: ${status.state}\n" +
                        "Requests: ${status.storedRequests}/${status.expectedRequests}; " +
                        "Observations: ${status.storedObservations}/${status.expectedObservations}; " +
                        "Events: ${status.storedEvents}/${status.expectedEvents}; " +
                        "Errors: ${status.errorCount}\n" +
                        "CSV remains authoritative."
                    reviewRoomState = status.state
                    refreshReviewDashboard()
                }
            }
        }

        diagnosticText =
            findViewById(
                R.id.textDiagnostics
            )

        sessionText =
            findViewById(
                R.id.textSession
            )

        scanStatusText =
            findViewById(
                R.id.textScanStatus
            )

        scanResultsText =
            findViewById(
                R.id.textScanResults
            )

        assessmentModeText = findViewById(R.id.textAssessmentMode)
        assessmentQualityText = findViewById(R.id.textAssessmentQuality)
        assessmentIdentityText = findViewById(R.id.textAssessmentIdentity)
        sessionHistoryText = findViewById(R.id.textSessionHistory)
        reviewSummaryText = findViewById(R.id.textReviewDashboard)
        findViewById<Button>(R.id.buttonReviewRefresh).setOnClickListener {
            refreshReviewDashboard()
        }
        findViewById<Button>(R.id.buttonReviewGuidedSample).setOnClickListener {
            if (sessionController.isRunning() || scanCollector.isRequestInProgress()) {
                Toast.makeText(this, "Finish scanning before a synthetic demonstration.",
                    Toast.LENGTH_SHORT).show()
            } else {
                showSyntheticAssessment()
                findViewById<TextView>(R.id.textReviewGuide).text =
                    ReviewDashboardFormatter.reviewGuide()
            }
        }
        findViewById<Button>(R.id.buttonLoadSessionHistory).setOnClickListener {
            loadSessionHistory()
        }
        findViewById<Button>(R.id.buttonDemoAssessment).setOnClickListener {
            if (sessionController.isRunning()) {
                Toast.makeText(this, "Stop the active session before showing a sample.",
                    Toast.LENGTH_SHORT).show()
            } else {
                showSyntheticAssessment()
            }
        }

        startSessionButton =
            findViewById(
                R.id.buttonStartSession
            )

        stopSessionButton =
            findViewById(
                R.id.buttonStopSession
            )

        singleScanButton =
            findViewById(
                R.id.buttonRunSingleScan
            )

        findViewById<Button>(
            R.id.buttonRequestPermission
        ).setOnClickListener {

            requestCollectorPermissions()
        }

        findViewById<Button>(
            R.id.buttonLocationSettings
        ).setOnClickListener {

            openLocationSettings()
        }

        findViewById<Button>(
            R.id.buttonRefresh
        ).setOnClickListener {

            renderDiagnostics()
            renderSessionStatus()
        }

        singleScanButton
            .setOnClickListener {

                runDevelopmentSingleScan()
            }

        startSessionButton
            .setOnClickListener {

                startCollectorSession()
            }

        stopSessionButton
            .setOnClickListener {

                stopCollectorSession()
            }

        // Consumer UX: presentation-only launch. Frozen G1 collector is not changed.
        findViewById<Button>(R.id.buttonOpenWifiExplorer).setOnClickListener {
            startActivity(Intent(this, com.bhadrirajt.wifitrustfusion.wifi.explorer.WifiExplorerActivity::class.java))
        }
        setupUiNavigation(savedInstanceState?.getInt("wtf_ui_page", 0) ?: 0)
        renderDiagnostics()
        renderSessionStatus()
        updateButtons()
        refreshReviewDashboard()
    }

    override fun onStart() {

        super.onStart()

        scanCollector.register()
    }

    override fun onResume() {

        super.onResume()

        if (
            ::permissionCoordinator.isInitialized
        ) {
            renderDiagnostics()
            renderSessionStatus()
        }
    }

    override fun onStop() {

        /*
         * Finish any pending request first while the logger
         * and session are still active.
         */
        if (
            ::scanCollector.isInitialized
        ) {
            scanCollector.unregister()
        }

        if (
            ::sessionController.isInitialized &&
            sessionController.isRunning()
        ) {
            sessionController.stopSession(
                reason =
                    "ACTIVITY_LEFT_FOREGROUND"
            )
        }

        super.onStop()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt("wtf_ui_page", activeUiPage)
        super.onSaveInstanceState(outState)
    }

    private fun setupUiNavigation(restoredPage: Int) {
        pageTabs.forEachIndexed { index, id ->
            findViewById<TextView>(id).setOnClickListener { selectUiPage(index) }
        }
        findViewById<Button>(R.id.buttonOverviewGoScan).setOnClickListener {
            selectUiPage(1)
        }
        findViewById<Button>(R.id.buttonOverviewGoHistory).setOnClickListener {
            selectUiPage(2)
        }
        selectUiPage(restoredPage)
    }

    private fun selectUiPage(page: Int) {
        activeUiPage = page.coerceIn(0, pageContainers.size - 1)
        pageContainers.forEachIndexed { index, id ->
            findViewById<View>(id).visibility =
                if (index == activeUiPage) View.VISIBLE else View.GONE
            val tab = findViewById<TextView>(pageTabs[index])
            tab.setBackgroundResource(
                if (index == activeUiPage) R.drawable.wtf_tab_selected
                else R.drawable.wtf_tab_unselected
            )
            tab.alpha = if (index == activeUiPage) 1f else 0.7f
            tab.isSelected = index == activeUiPage
        }
        findViewById<View>(R.id.uiContentScroll).scrollTo(0, 0)
    }

    private fun updateOverviewCards() {
        if (!::sessionController.isInitialized) return
        val sourceLabel = when (reviewSource) {
            ReviewDashboardFormatter.Source.LIVE -> "LIVE OBSERVATION"
            ReviewDashboardFormatter.Source.SYNTHETIC -> "SYNTHETIC EXAMPLE - NOT MEASURED"
            ReviewDashboardFormatter.Source.NONE -> "NO ASSESSMENT"
        }
        findViewById<TextView>(R.id.textUiSource).text = sourceLabel
        findViewById<TextView>(R.id.textUiQuality).text =
            reviewQuality ?: "Not assessed"
        findViewById<TextView>(R.id.textUiObservations).text =
            if (reviewSource == ReviewDashboardFormatter.Source.NONE) "--"
            else reviewObservations.toString()
        findViewById<TextView>(R.id.textUiSession).text =
            if (sessionController.isRunning()) "Session running"
            else "Session not running"
        findViewById<TextView>(R.id.textUiChanges).text =
            if (reviewSource == ReviewDashboardFormatter.Source.NONE) "Not assessed"
            else "${reviewSecurityChanges} security | ${reviewBandChanges} band"
        findViewById<TextView>(R.id.textUiStorage).text =
            if (reviewSource == ReviewDashboardFormatter.Source.SYNTHETIC)
                "Demo only - no evidence stored"
            else "CSV authoritative | Room: ${reviewRoomState ?: "not verified"}"
        findViewById<TextView>(R.id.textUiExplanation).text =
            when (reviewSource) {
                ReviewDashboardFormatter.Source.SYNTHETIC ->
                    "Example calculation only. Never written to research evidence."
                ReviewDashboardFormatter.Source.LIVE ->
                    "Rule-based observation summary. Not a verified attack verdict."
                ReviewDashboardFormatter.Source.NONE ->
                    "Start a development session or open the synthetic walkthrough."
            }
    }

    override fun onDestroy() {

        if (::roomMirror.isInitialized) {
            roomMirror.shutdown()
        }

        if (
            ::researchLogger.isInitialized
        ) {
            researchLogger.close()
        }

        if (
            ::sessionController.isInitialized
        ) {
            sessionController.destroy()
        }

        super.onDestroy()
    }

    private fun requestCollectorPermissions() {

        val snapshot =
            permissionCoordinator.snapshot()

        if (
            snapshot.fineLocationGranted
        ) {

            Toast.makeText(
                this,
                "Required location permission is already granted.",
                Toast.LENGTH_SHORT
            ).show()

            renderDiagnostics()

            return
        }

        locationPermissionLauncher.launch(
            permissionCoordinator
                .requiredRuntimePermissions()
        )
    }

    private fun openLocationSettings() {

        try {

            startActivity(
                Intent(
                    Settings
                        .ACTION_LOCATION_SOURCE_SETTINGS
                )
            )

        } catch (_: Exception) {

            Toast.makeText(
                this,
                "Unable to open Location settings.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun prerequisitesReady():
        Boolean {

        val permission =
            permissionCoordinator.snapshot()

        return permission
            .readyForWifiCollection &&
            scanCollector.isWifiEnabled()
    }

    private fun runDevelopmentSingleScan() {

        if (
            sessionController.isRunning()
        ) {

            Toast.makeText(
                this,
                "Stop the running session before using manual single-scan mode.",
                Toast.LENGTH_SHORT
            ).show()

            return
        }

        if (
            !prerequisitesReady()
        ) {

            renderDiagnostics()

            Toast.makeText(
                this,
                "Collector prerequisites are not ready.",
                Toast.LENGTH_SHORT
            ).show()

            return
        }

        scanResultsText.text =
            "Waiting for manual scan..."

        scanCollector.requestSingleScan(
            sessionId =
                "M2_MANUAL",
            requestedIntervalMs =
                0L
        )

        updateButtons()
    }

    private fun startCollectorSession() {

        if (
            !prerequisitesReady()
        ) {

            renderDiagnostics()

            Toast.makeText(
                this,
                "Collector prerequisites are not ready.",
                Toast.LENGTH_SHORT
            ).show()

            return
        }

        freshnessEngine.reset()
        identityEngine.reset()
        reviewSource = ReviewDashboardFormatter.Source.NONE
        reviewQuality = null
        reviewObservations = 0
        reviewSecurityChanges = 0
        reviewBandChanges = 0
        assessmentModeText.text = "M4/M5: Waiting for live observations"
        assessmentQualityText.text = "Evidence quality: not assessed"
        assessmentIdentityText.text = "Network identity: session-local comparison not started"

        resetLatestSummary()

        eventCounter =
            0L

        sessionLoggingReady =
            false

        sessionHadCsvError =
            false

        currentEvidenceDirectory =
            null

        sessionController.startSession(
            deviceAlias =
                DEVICE_ALIAS,
            intervalMs =
                CollectorSessionController
                    .DEFAULT_DEV_INTERVAL_MS
        )

        renderSessionStatus()
        updateButtons()
    }

    private fun stopCollectorSession() {

        if (
            !sessionController.isRunning()
        ) {
            return
        }

        sessionController.stopSession(
            reason =
                "USER_STOP"
        )

        renderSessionStatus()
        updateButtons()
    }

    override fun onSessionStarted(
        sessionId: String,
        intervalMs: Long
    ) {

        runOnUiThread {

            val metadata =
                CollectorSessionMetadata(
                    sessionId =
                        sessionId,

                    collectorVersion =
                        ProjectVersions
                            .COLLECTOR_VERSION,

                    protocolVersion =
                        ProjectVersions
                            .PROTOCOL_VERSION,

                    observationSchemaVersion =
                        ProjectVersions
                            .OBS_SCHEMA_VERSION,

                    appVersionName =
                        getAppVersionName(),

                    appVersionCode =
                        getAppVersionCode(),

                    deviceAlias =
                        DEVICE_ALIAS,

                    manufacturer =
                        Build.MANUFACTURER,

                    model =
                        Build.MODEL,

                    deviceCodename =
                        Build.DEVICE,

                    androidRelease =
                        Build.VERSION.RELEASE,

                    apiLevel =
                        Build.VERSION.SDK_INT,

                    startedUtcIso =
                        Instant.now()
                            .toString(),

                    startedElapsedUs =
                        elapsedRealtimeUs(),

                    requestedScanIntervalMs =
                        intervalMs,

                    runLabel = "M3_3B_DEV_ROOM_MIRROR"
                )

            try {

                currentEvidenceDirectory =
                    researchLogger
                        .startSession(
                            metadata
                        )

                sessionLoggingReady =
                    true

                try {
                    roomMirror.recordSession(metadata)
                } catch (_: Exception) {
                    scanStatusText.text = "ROOM MIRROR ENQUEUE FAILED; CSV ACTIVE"
                }

                logEvent(
                    eventType =
                        CollectorEventType
                            .SESSION_STARTED,
                    requestId =
                        null,
                    detail =
                        "Persistent session evidence initialized."
                )

            } catch (
                exception: Exception
            ) {

                sessionLoggingReady =
                    false

                sessionHadCsvError =
                    true

                scanStatusText.text =
                    "LOGGER START ERROR:\n" +
                        (
                            exception.message
                                ?: exception
                                    .javaClass
                                    .simpleName
                        )
            }

            renderSessionStatus()
            updateButtons()
        }
    }

    override fun onScanDue(
        sessionId: String,
        intervalMs: Long
    ) {

        runOnUiThread {

            if (
                !sessionLoggingReady
            ) {

                if (
                    sessionController.isRunning()
                ) {
                    sessionController
                        .stopSession(
                            reason =
                                "LOGGER_START_FAILED"
                        )
                }

                return@runOnUiThread
            }

            if (
                scanCollector
                    .isRequestInProgress()
            ) {

                onSessionStatus(
                    "Scan due but another request is still in progress."
                )

                return@runOnUiThread
            }

            val eventWritten = logEventSafely(
                eventType =
                    CollectorEventType
                        .SCAN_REQUEST_CREATED,
                requestId =
                    null,
                detail =
                    "Scheduled scan became due."
            )

            if (!eventWritten) {
                if (sessionController.isRunning()) {
                    sessionController.stopSession(reason = "LOGGER_WRITE_FAILED")
                }
                return@runOnUiThread
            }

            scanCollector.requestSingleScan(
                sessionId =
                    sessionId,
                requestedIntervalMs =
                    intervalMs
            )

            updateButtons()
        }
    }

    override fun onSessionStopped(
        sessionId: String,
        completedRequestCount: Int,
        reason: String
    ) {
        runOnUiThread {
            // Completion is still attempted after a CSV write exception.
            // sessionLoggingReady is not cleared until AFTER this callback.
            if (researchLogger.isActive()) {
                var stopEventWritten = false
                try {
                    logEvent(
                        eventType = CollectorEventType.SESSION_STOPPED,
                        requestId = null,
                        detail = "reason=$reason; completedRequests=$completedRequestCount"
                    )
                    stopEventWritten = true
                } catch (exception: Exception) {
                    sessionHadCsvError = true
                    scanStatusText.text =
                        "LOGGER STOP EVENT ERROR: " + exception.javaClass.simpleName
                }

                // A clean stop requires a normal user stop AND every CSV
                // operation observed by this activity to have succeeded.
                // This does not prove power-loss durability or atomic bundles.
                val cleanClose = reason == "USER_STOP" &&
                    sessionLoggingReady && !sessionHadCsvError && stopEventWritten
                val endUtc = Instant.now().toString()
                try {
                    researchLogger.markSessionComplete(
                        stopReason = reason,
                        completedRequestCount = completedRequestCount,
                        endedUtcIso = endUtc,
                        cleanClose = cleanClose
                    )
                    try {
                        roomMirror.recordCompletion(
                            sessionId, endUtc, reason, completedRequestCount, cleanClose
                        )
                    } catch (_: Exception) {
                        scanStatusText.text =
                            "ROOM MIRROR ENQUEUE FAILED; CSV COMPLETION SAVED"
                    }
                } catch (exception: Exception) {
                    sessionHadCsvError = true
                    scanStatusText.text =
                        "LOGGER COMPLETION ERROR: " + exception.javaClass.simpleName
                }
            } else {
                sessionHadCsvError = true
                scanStatusText.text = "LOGGER UNAVAILABLE; COMPLETION NOT VERIFIED"
            }

            // Existing close() suppresses some flush/close exceptions. A
            // clean_close flag alone is not a crash/durability guarantee.
            researchLogger.close()
            sessionLoggingReady = false

            sessionText.text = buildString {
                appendLine("SESSION STOPPED")
                appendLine("Session: $sessionId")
                appendLine("Completed requests: $completedRequestCount")
                appendLine("Stop reason: $reason")
                appendLine("CSV logger error: $sessionHadCsvError")
                appendLine()
                appendLine("Evidence:")
                appendLine(currentEvidenceDirectory?.absolutePath ?: "unavailable")
            }
            updateButtons()
            refreshReviewDashboard()
        }
    }

    override fun onSessionStatus(
        message: String
    ) {

        runOnUiThread {

            scanStatusText.text =
                "Session status:\n$message"

            renderSessionStatus()

            updateButtons()
        }
    }

    override fun onScanCompleted(
        batch: WifiScanBatch
    ) {

        runOnUiThread {

            val analyzedObservations =
                freshnessEngine.analyzeBatch(
                    batch =
                        batch,

                    observedUtcIso =
                        Instant.now()
                            .toString(),

                    observedElapsedUs =
                        elapsedRealtimeUs()
                )

            renderBatch(
                batch =
                    batch,
                observations =
                    analyzedObservations
            )

            // Display-only. Never changes acquired observations, logger, or Room.
            showLiveAssessment(batch.request, analyzedObservations)

            val activeSessionId =
                sessionController
                    .getCurrentSessionId()

            val belongsToActiveSession =
                sessionController.isRunning() &&
                    activeSessionId != null &&
                    batch.request.sessionId ==
                        activeSessionId

            if (
                belongsToActiveSession &&
                sessionLoggingReady
            ) {

                try {

                    researchLogger
                        .logRequest(
                            batch.request
                        )

                    researchLogger
                        .logObservations(
                            analyzedObservations
                        )

                    try {
                        roomMirror.recordRequest(batch.request, analyzedObservations)
                    } catch (_: Exception) {
                        scanStatusText.text = "ROOM MIRROR ENQUEUE FAILED; CSV ACTIVE"
                    }

                    logCompletionEvent(
                        batch
                    )

                } catch (
                    exception: Exception
                ) {

                    scanStatusText.text =
                        "LOGGER WRITE ERROR:\n" +
                            (
                                exception.message
                                    ?: exception
                                        .javaClass
                                        .simpleName
                            )

                    // Preserve the active logger for a best-effort
                    // non-clean completion marker in onSessionStopped.
                    sessionHadCsvError = true

                    if (
                        sessionController
                            .isRunning()
                    ) {
                        sessionController
                            .stopSession(
                                reason =
                                    "LOGGER_WRITE_FAILED"
                            )
                    }

                    return@runOnUiThread
                }
            }

            if (
                belongsToActiveSession &&
                sessionController.isRunning()
            ) {

                sessionController
                    .notifyScanCompleted()
            }

            renderDiagnostics()
            renderSessionStatus()
            updateButtons()
        }
    }

    override fun onCollectorStatus(
        message: String
    ) {

        runOnUiThread {

            scanStatusText.text =
                "Collector status:\n$message"

            updateButtons()
        }
    }

    private fun logCompletionEvent(
        batch: WifiScanBatch
    ) {

        val request =
            batch.request

        val eventType =
            when (
                request.completionReason
            ) {

                ScanCompletionReason
                    .BROADCAST_RESULTS_UPDATED,

                ScanCompletionReason
                    .BROADCAST_RESULTS_NOT_UPDATED ->

                    CollectorEventType
                        .SCAN_BROADCAST_RECEIVED

                ScanCompletionReason
                    .SCAN_TIMEOUT ->

                    CollectorEventType
                        .SCAN_TIMEOUT

                ScanCompletionReason
                    .PERMISSION_MISSING,

                ScanCompletionReason
                    .WIFI_DISABLED,

                ScanCompletionReason
                    .LOCATION_DISABLED ->

                    CollectorEventType
                        .PREREQUISITE_BLOCKED

                ScanCompletionReason
                    .SECURITY_EXCEPTION,

                ScanCompletionReason
                    .COLLECTOR_ERROR ->

                    CollectorEventType
                        .COLLECTOR_ERROR

                else ->

                    CollectorEventType
                        .START_SCAN_CALLED
            }

        logEvent(
            eventType =
                eventType,
            requestId =
                request.requestId,
            detail =
                "completion=${request.completionReason}; " +
                    "resultCount=${request.resultCount}; " +
                    "resultsUpdated=${request.resultsUpdated}"
        )
    }

    private fun logEventSafely(
        eventType: CollectorEventType,
        requestId: String?,
        detail: String?
    ): Boolean {
        if (!sessionLoggingReady) return false
        return try {
            logEvent(eventType = eventType, requestId = requestId, detail = detail)
            true
        } catch (exception: Exception) {
            sessionHadCsvError = true
            scanStatusText.text =
                "LOGGER EVENT ERROR: " + exception.javaClass.simpleName
            false
        }
    }

    private fun logEvent(
        eventType: CollectorEventType,
        requestId: String?,
        detail: String?
    ) {
        val sessionId = sessionController.getCurrentSessionId() ?: return
        eventCounter += 1L
        val record = CollectorEventRecord(
            eventId = "EVT_%06d".format(eventCounter),
            sessionId = sessionId,
            eventUtcIso = Instant.now().toString(),
            eventElapsedUs = elapsedRealtimeUs(),
            eventType = eventType,
            requestId = requestId,
            detail = detail
        )
        researchLogger.logEvent(record)
        try {
            roomMirror.recordEvent(record)
        } catch (_: Exception) {
            scanStatusText.text = "ROOM MIRROR ENQUEUE FAILED; CSV ACTIVE"
        }
    }
    private fun loadSessionHistory() {
        sessionHistoryText.text = "Loading Room-mirror history..."
        try {
            roomMirror.readRecentHistory(limit = 10) { result ->
                runOnUiThread {
                    if (!isDestroyed && !isFinishing && ::sessionHistoryText.isInitialized) {
                        reviewHistoryLoaded = result.isSuccess
                        sessionHistoryText.text = result.fold(
                            onSuccess = { SessionHistoryFormatter.render(it) },
                            onFailure = {
                                "Room history could not be read. CSV evidence is unchanged."
                            }
                        )
                        refreshReviewDashboard()
                    }
                }
            }
        } catch (_: Exception) {
            sessionHistoryText.text =
                "Room history unavailable right now. CSV evidence is unchanged."
        }
    }

    private fun showLiveAssessment(
        request: com.bhadrirajt.wifitrustfusion.wifi.model.ScanRequestRecord,
        observations: List<WifiObservation>
    ) {
        val identity = identityEngine.evaluate(request, observations)
        val quality = qualityEngine.evaluate(request, observations)
        assessmentModeText.text = "LIVE M4/M5 · ${request.requestId}"
        updateReviewAssessment(ReviewDashboardFormatter.Source.LIVE, identity, quality)
        renderAssessment(identity, quality)
    }

    private fun showSyntheticAssessment() {
        val (identity, quality) = AssessmentDemo.sample()
        assessmentModeText.text = "SYNTHETIC DEMO ONLY — not a real Wi-Fi scan"
        updateReviewAssessment(ReviewDashboardFormatter.Source.SYNTHETIC, identity, quality)
        renderAssessment(identity, quality)
        Toast.makeText(this, "Synthetic assessment, no evidence recorded.",
            Toast.LENGTH_LONG).show()
    }

    private fun updateReviewAssessment(
        source: ReviewDashboardFormatter.Source,
        identity: NetworkIdentityEngine.Result,
        quality: EvidenceQualityEngine.Result
    ) {
        reviewSource = source
        reviewQuality = quality.level.name
        reviewObservations = quality.observed
        reviewSecurityChanges = identity.changedSecurity
        reviewBandChanges = identity.changedBand
        refreshReviewDashboard()
    }

    private fun refreshReviewDashboard() {
        if (!::reviewSummaryText.isInitialized || !::sessionController.isInitialized) return
        reviewSummaryText.text = ReviewDashboardFormatter.render(
            ReviewDashboardFormatter.Snapshot(
                source = reviewSource,
                sessionRunning = sessionController.isRunning(),
                requestsCompleted = if (reviewSource == ReviewDashboardFormatter.Source.SYNTHETIC)
                    0 else sessionController.getCompletedRequestCount(),
                observations = reviewObservations,
                evidenceQuality = reviewQuality,
                securityChanges = reviewSecurityChanges,
                bandChanges = reviewBandChanges,
                roomState = if (reviewSource == ReviewDashboardFormatter.Source.SYNTHETIC)
                    "NOT PART OF SYNTHETIC DEMO" else reviewRoomState,
                csvFailureSeen = sessionHadCsvError,
                historyLoaded = if (reviewSource == ReviewDashboardFormatter.Source.SYNTHETIC)
                    false else reviewHistoryLoaded
            )
        )
        updateOverviewCards()
    }

    private fun renderAssessment(
        identity: NetworkIdentityEngine.Result,
        quality: EvidenceQualityEngine.Result
    ) {
        assessmentQualityText.text = buildString {
            appendLine("M5 — EVIDENCE QUALITY: ${quality.level}")
            appendLine("Observations: ${quality.observed}")
            appendLine("Recent advanced: ${quality.advancedRecent}; first seen: ${quality.firstSeenRecent}")
            appendLine("Repeated/old: ${quality.repeatedOrRegressed}; aged: ${quality.aged}")
            appendLine("Untrackable: ${quality.untrackable}")
            appendLine("Why: ${quality.reasons.joinToString(" " )}")
            append("Rule-based assessment, not a calibrated confidence score.")
        }
        assessmentIdentityText.text = buildString {
            appendLine("M4 — NETWORK IDENTITY (SESSION-LOCAL)")
            appendLine("Qualified: ${identity.qualifiedObservations}; new in this runtime: ${identity.firstObservedHere}")
            appendLine("Seen before here: ${identity.previouslyObservedHere}; unknown: ${identity.unknownIdentity}")
            appendLine("Security changes: ${identity.changedSecurity}; band changes: ${identity.changedBand}")
            appendLine("Profiles in local memory: ${identity.knownProfilesTotal}")
            appendLine("Security categories: ${identity.securityBreakdown}")
            append(identity.explanation)
        }
    }

    private fun renderBatch(
        batch: WifiScanBatch,
        observations: List<WifiObservation>
    ) {

        val request =
            batch.request

        latestRequestId =
            request.requestId

        latestResultCount =
            observations.size

        latestFirstSeen =
            observations.count {
                it.timestampState ==
                    TimestampState.FIRST_SEEN
            }

        latestAdvanced =
            observations.count {
                it.timestampState ==
                    TimestampState.ADVANCED
            }

        latestRepeated =
            observations.count {
                it.timestampState ==
                    TimestampState.REPEATED
            }

        latestRegressed =
            observations.count {
                it.timestampState ==
                    TimestampState.REGRESSED
            }

        latestUntrackable =
            observations.count {
                it.timestampState ==
                    TimestampState.UNTRACKABLE
            }

        latestSeenAfterRequest =
            observations.count {
                it.seenAfterRequest == true
            }

        scanResultsText.text =
            buildString {

                appendLine(
                    "LATEST REQUEST"
                )

                appendLine(
                    "Session: ${request.sessionId}"
                )

                appendLine(
                    "Request: ${request.requestId}"
                )

                appendLine(
                    "startScan(): ${request.startScanReturned}"
                )

                appendLine(
                    "Broadcast: ${request.broadcastReceived}"
                )

                appendLine(
                    "Results updated: ${request.resultsUpdated}"
                )

                appendLine(
                    "Result count: ${request.resultCount}"
                )

                appendLine(
                    "Completion: ${request.completionReason}"
                )

                appendLine()

                appendLine(
                    "FRESHNESS SUMMARY"
                )

                appendLine(
                    "FIRST_SEEN: $latestFirstSeen"
                )

                appendLine(
                    "ADVANCED: $latestAdvanced"
                )

                appendLine(
                    "REPEATED: $latestRepeated"
                )

                appendLine(
                    "REGRESSED: $latestRegressed"
                )

                appendLine(
                    "UNTRACKABLE: $latestUntrackable"
                )

                appendLine(
                    "Seen after request: $latestSeenAfterRequest / ${observations.size}"
                )

                appendLine()

                appendLine(
                    "Privacy-safe display"
                )

                appendLine(
                    "Raw SSID/BSSID values are stored only in app-specific research evidence files."
                )
            }
    }

    private fun renderSessionStatus() {

        if (
            !::sessionController.isInitialized
        ) {
            return
        }

        if (
            sessionController.isRunning()
        ) {

            sessionText.text =
                buildString {

                    appendLine(
                        "SESSION RUNNING"
                    )

                    appendLine(
                        "Session: ${
                            sessionController
                                .getCurrentSessionId()
                        }"
                    )

                    appendLine(
                        "Device alias: $DEVICE_ALIAS"
                    )

                    appendLine(
                        "Development interval: ${
                            sessionController
                                .getIntervalMs() /
                                1_000L
                        } s"
                    )

                    appendLine(
                        "Completed requests: ${
                            sessionController
                                .getCompletedRequestCount()
                        }"
                    )

                    appendLine(
                        "Persistent logging: ${
                            if (
                                sessionLoggingReady
                            ) {
                                "ACTIVE"
                            } else {
                                "NOT READY"
                            }
                        }"
                    )

                    if (
                        currentEvidenceDirectory !=
                        null
                    ) {

                        appendLine()

                        appendLine(
                            "Evidence directory:"
                        )

                        appendLine(
                            currentEvidenceDirectory
                                ?.absolutePath
                        )
                    }

                    if (
                        latestRequestId != null
                    ) {

                        appendLine()

                        appendLine(
                            "Latest request: $latestRequestId"
                        )

                        appendLine(
                            "Latest result count: $latestResultCount"
                        )
                    }
                }

        } else if (
            sessionText.text
                .toString()
                .isBlank()
        ) {

            sessionText.text =
                "SESSION IDLE"
        }
    }

    private fun renderDiagnostics() {

        val permission =
            permissionCoordinator.snapshot()

        val wifiEnabled =
            if (
                ::scanCollector.isInitialized
            ) {
                scanCollector
                    .isWifiEnabled()
            } else {
                false
            }

        val ready =
            permission
                .readyForWifiCollection &&
                wifiEnabled

        diagnosticText.text =
            buildString {

                appendLine(
                    "PROJECT"
                )

                appendLine(
                    "WiFiTrustFusion"
                )

                appendLine()

                appendLine(
                    "IMPLEMENTATION STATUS"
                )

                appendLine(
                    "Milestone: ${ProjectVersions.MILESTONE}"
                )

                appendLine(
                    "Collector: ${ProjectVersions.COLLECTOR_VERSION}"
                )

                appendLine(
                    "Protocol: ${ProjectVersions.PROTOCOL_VERSION}"
                )

                appendLine(
                    "Observation schema: ${ProjectVersions.OBS_SCHEMA_VERSION}"
                )

                appendLine()

                appendLine(
                    "COLLECTOR PREREQUISITES"
                )

                appendLine(
                    "Fine location: ${permission.fineLocationGranted}"
                )

                appendLine(
                    "Coarse location: ${permission.coarseLocationGranted}"
                )

                appendLine(
                    "Location services: ${permission.locationServicesEnabled}"
                )

                appendLine(
                    "Wi-Fi enabled: $wifiEnabled"
                )

                appendLine(
                    "Ready: $ready"
                )

                appendLine()

                appendLine(
                    "M3.3B DEVELOPMENT BOUNDARY"
                )

                appendLine(
                    "Session lifecycle: IMPLEMENTED"
                )

                appendLine(
                    "Repeated acquisition: IMPLEMENTED"
                )

                appendLine(
                    "Freshness engine: IMPLEMENTED"
                )

                appendLine(
                    "Persistent evidence logging: IMPLEMENTED"
                )

                appendLine(
                    "Clean-close marker: IMPLEMENTED"
                )

                appendLine(
                    "Room database: DEVELOPMENT MIRROR (CSV IS AUTHORITATIVE)"
                )

                appendLine(
                    "WFAI: NOT IMPLEMENTED"
                )

                appendLine(
                    "Risk scoring: NOT IMPLEMENTED"
                )
            }

        updateButtons()
    }

    private fun resetLatestSummary() {

        latestRequestId =
            null

        latestResultCount =
            0

        latestFirstSeen =
            0

        latestAdvanced =
            0

        latestRepeated =
            0

        latestRegressed =
            0

        latestUntrackable =
            0

        latestSeenAfterRequest =
            0
    }

    private fun updateButtons() {

        if (
            !::startSessionButton.isInitialized
        ) {
            return
        }

        val sessionRunning =
            sessionController.isRunning()

        val scanRunning =
            scanCollector
                .isRequestInProgress()

        startSessionButton.isEnabled =
            !sessionRunning &&
                !scanRunning

        stopSessionButton.isEnabled =
            sessionRunning

        singleScanButton.isEnabled =
            !sessionRunning &&
                !scanRunning
    }


    private fun getAppVersionName():
        String {

        val packageInfo =
            packageManager.getPackageInfo(
                packageName,
                0
            )

        return packageInfo.versionName
            ?: "unknown"
    }

    private fun getAppVersionCode():
        Long {

        val packageInfo =
            packageManager.getPackageInfo(
                packageName,
                0
            )

        return if (
            Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.P
        ) {

            packageInfo.longVersionCode

        } else {

            @Suppress("DEPRECATION")
            packageInfo.versionCode
                .toLong()
        }
    }
    private fun elapsedRealtimeUs():
        Long {

        return SystemClock
            .elapsedRealtimeNanos() /
            1_000L
    }
}
