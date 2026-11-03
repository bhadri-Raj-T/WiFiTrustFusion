package com.bhadrirajt.wifitrustfusion.ui

/** Presentation-only summary. Never classifies a network as malicious. */
object ReviewDashboardFormatter {
    enum class Source { NONE, LIVE, SYNTHETIC }

    data class Snapshot(
        val source: Source,
        val sessionRunning: Boolean,
        val requestsCompleted: Int,
        val observations: Int,
        val evidenceQuality: String?,
        val securityChanges: Int,
        val bandChanges: Int,
        val roomState: String?,
        val csvFailureSeen: Boolean,
        val historyLoaded: Boolean
    )

    fun render(snapshot: Snapshot): String = buildString {
        appendLine("WIFITRUSTFUSION | REVIEW DASHBOARD")
        when (snapshot.source) {
            Source.NONE -> appendLine("SOURCE: NO ASSESSMENT YET")
            Source.LIVE -> appendLine("SOURCE: LIVE ANDROID SCAN (on-device)")
            Source.SYNTHETIC -> appendLine("SOURCE: SYNTHETIC DEMONSTRATION - NOT MEASURED")
        }
        appendLine("Session: ${if (snapshot.sessionRunning) "RUNNING" else "NOT RUNNING"}")
        appendLine("Live session completed requests: ${snapshot.requestsCompleted.coerceAtLeast(0)}")
        if (snapshot.source == Source.NONE) {
            appendLine("Evidence quality: NOT ASSESSED")
            appendLine("Network changes: NOT ASSESSED")
        } else {
            appendLine("Observations in displayed assessment: ${snapshot.observations.coerceAtLeast(0)}")
            appendLine("Evidence quality: ${snapshot.evidenceQuality ?: "UNKNOWN"} (heuristic)")
            appendLine("Security-category changes: ${snapshot.securityChanges.coerceAtLeast(0)}")
            appendLine("Frequency-band changes: ${snapshot.bandChanges.coerceAtLeast(0)}")
        }
        appendLine("Room mirror: ${snapshot.roomState ?: "NOT YET VERIFIED"}")
        appendLine("CSV write error seen: ${if (snapshot.csvFailureSeen) "YES" else "NO"}")
        appendLine("Session history: ${if (snapshot.historyLoaded) "REQUESTED" else "NOT LOADED"}")
        appendLine("CSV remains authoritative; Room may be incomplete.")
        append("No validated WFAI, attack verdict, or crowd reputation in this prototype.")
    }

    fun reviewGuide(): String = """
        REVIEW WALKTHROUGH (3-5 MIN)
        1. Verify the source label: LIVE or SYNTHETIC.
        2. Show M4 session-local identity and M5 evidence-quality reasons.
        3. Use the saved-session summary to inspect Room mirror counts.
        4. Explain that CSV is authoritative and persistence is not crash-proof.
        5. Show the Jupyter charts as synthetic examples or actual test evidence.
        6. Do not describe this prototype as validated attack detection.
    """.trimIndent()
}
