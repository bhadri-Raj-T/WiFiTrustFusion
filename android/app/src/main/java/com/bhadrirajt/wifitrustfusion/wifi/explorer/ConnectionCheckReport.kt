package com.bhadrirajt.wifitrustfusion.wifi.explorer

import java.util.Locale

internal enum class CheckState(val label: String) {
    PASS("PASS"), WARNING("ATTENTION"), FAILED("FAILED"), INFO("OBSERVED"),
    PENDING("NOT RUN"), RUNNING("RUNNING"), UNAVAILABLE("UNAVAILABLE"), CANCELLED("DISCARDED")
}
internal enum class ReportOrigin(val label: String) { LIVE("LIVE"), SIMULATED("SIMULATED") }
internal data class CheckItem(val id: String, val title: String, val state: CheckState, val value: String, val detail: String)
internal data class ConnectionCheckReport(val origin: ReportOrigin, val items: List<CheckItem>, val note: String) {
    val passed get() = items.count { it.state == CheckState.PASS }
    val attention get() = items.count { it.state == CheckState.WARNING || it.state == CheckState.FAILED }
    val unavailable get() = items.count { it.state in listOf(CheckState.PENDING, CheckState.RUNNING, CheckState.UNAVAILABLE, CheckState.CANCELLED) }
    val summary get() = "${origin.label} | ${items.size} items | $passed passed | $attention need attention | $unavailable incomplete/unavailable"
    fun asText() = "WiFiTrustFusion | ${origin.label}\n$summary\n\n" +
        items.joinToString("\n\n") { "${it.title}: ${it.state.label}\n${it.value}\n${it.detail}" } + "\n\n$note"
}

/** No Android dependencies: observations and completed probes remain distinct. */
internal object ConnectionCheckReports {
    data class Diagnostics(
        val dnsOk: Boolean?, val httpsOk: Boolean?, val tcpSuccesses: Int, val tcpAttempts: Int,
        val medianMs: Double?, val dnsDetail: String, val httpsDetail: String,
        val httpCode: Int?, val connectionUnchanged: Boolean
    )
    data class Inputs(
        val connected: Boolean = false, val identityVisible: Boolean = false, val rssiDbm: Int? = null,
        val sampleCount: Int = 0, val durationSeconds: Long = 0, val rangeDb: Int? = null,
        val security: String = "Unavailable", val securitySource: String = "No current observation",
        val portal: Boolean = false, val validated: Boolean = false,
        val diagnostics: Diagnostics? = null, val runningPhase: String? = null
    )
    fun live(input: Inputs): ConnectionCheckReport {
        val rows = mutableListOf<CheckItem>()
        fun row(id: String, title: String, state: CheckState, value: String, detail: String) {
            rows += CheckItem(id, title, state, value, detail)
        }
        val unavailable = CheckState.UNAVAILABLE
        row("route", "Wi-Fi connection", if (input.connected) CheckState.PASS else CheckState.FAILED,
            if (input.connected) "A Wi-Fi route is available" else "No Wi-Fi route identified",
            "Active probes use the selected Wi-Fi Network. A cellular success is not substituted.")
        row("identity", "Network visibility", when { !input.connected -> unavailable; input.identityVisible -> CheckState.INFO; else -> CheckState.WARNING },
            if (input.identityVisible) "Network name available" else "Network name unavailable",
            "Location permission/settings may hide identity. Hidden identity does not itself establish a security problem.")
        val validSignal = input.connected && ConnectionAnalytics.validRssi(input.rssiDbm)
        row("signal", "Current radio signal", when { !validSignal -> unavailable; input.rssiDbm!! < -75 -> CheckState.WARNING; else -> CheckState.INFO },
            if (validSignal) "${input.rssiDbm} dBm" else "No valid signal reading",
            "Closer to zero generally means stronger reception. Signal strength does not establish hotspot legitimacy.")
        val enoughHistory = input.connected && input.sampleCount >= 5 && input.durationSeconds >= 8
        row("variation", "Signal observation window", when { !enoughHistory -> CheckState.PENDING; (input.rangeDb ?: 0) >= 12 -> CheckState.WARNING; else -> CheckState.INFO },
            "${input.sampleCount} polls / ${input.durationSeconds}s" + (input.rangeDb?.let { " / ${it} dB range" } ?: ""),
            "Variation advice requires at least 5 polls over 8 seconds and a range of 12 dB. Cached values may repeat.")
        row("security", "Wi-Fi configuration", when {
            !input.connected || input.security == "Unavailable" -> unavailable
            input.security in listOf("Open Wi-Fi", "WEP (legacy)") -> CheckState.WARNING
            else -> CheckState.INFO
        }, input.security, "${input.securitySource}. A modern configuration is not proof of who operates the hotspot.")
        row("portal", "Captive sign-in", when { !input.connected -> unavailable; input.portal -> CheckState.WARNING; else -> CheckState.INFO },
            if (input.portal) "Android reports sign-in required" else "No portal flag reported",
            "Android's current portal flag is an observation; absence of the flag does not prove that no portal exists.")
        row("validation", "Android internet validation", when { !input.connected -> unavailable; input.validated -> CheckState.PASS; else -> CheckState.WARNING },
            if (input.validated) "Validated by Android" else "Internet access not yet confirmed",
            "This is Android's state, separate from the endpoint checks below.")
        val original = input.diagnostics
        val data = original?.takeIf { it.connectionUnchanged && input.connected }
        fun waiting(phase: String): CheckState = if (input.runningPhase == phase) CheckState.RUNNING else CheckState.PENDING
        row("dns", "DNS lookup", data?.dnsOk?.let { if (it) CheckState.PASS else CheckState.FAILED } ?: waiting("DNS"),
            data?.dnsDetail ?: if (input.runningPhase == "DNS") "Resolving example.com..." else "Not tested yet.",
            "Name lookup is bound to Wi-Fi, with a 3-second bound. A timeout does not prove DNS poisoning.")
        val tcpState = when {
            data == null || data.tcpAttempts == 0 -> waiting("TCP")
            data.tcpSuccesses == 0 -> CheckState.FAILED
            data.tcpSuccesses < data.tcpAttempts -> CheckState.WARNING
            else -> CheckState.PASS
        }
        val median = data?.medianMs?.let { String.format(Locale.US, " / median %.1f ms", it) }.orEmpty()
        row("tcp", "TCP reachability", tcpState,
            if (data != null && data.tcpAttempts > 0) "${data.tcpSuccesses}/${data.tcpAttempts} connected$median" else "Not completed",
            "Up to 3 TCP connects to example.com:443 after successful DNS. This is not ping or download speed.")
        row("https", "HTTPS and endpoint identity", data?.httpsOk?.let { if (it) CheckState.PASS else CheckState.FAILED } ?: waiting("HTTPS"),
            data?.httpsDetail ?: if (data?.dnsOk == false) "Skipped because DNS did not complete" else "Not tested yet.",
            "Uses normal certificate and hostname verification. Success verifies the test endpoint, not the hotspot owner.")
        val code = data?.httpCode
        row("http", "Endpoint HTTP response", when { code == null -> CheckState.PENDING; code != null && code in 200..399 -> CheckState.PASS; else -> CheckState.WARNING },
            code?.let { "HTTP $it" } ?: "No response status available",
            "This is the status returned by the HTTPS HEAD request, not a separate probe. An error may be endpoint-specific; 405 can mean HEAD is unsupported.")
        row("continuity", "Connection continuity", when { original == null -> CheckState.PENDING; original.connectionUnchanged && input.connected -> CheckState.PASS; else -> CheckState.CANCELLED },
            when { original == null -> "Checked during an active test run"; original.connectionUnchanged && input.connected -> "Wi-Fi matched at test checkpoints"; else -> "Incomplete or changed-connection results discarded" },
            "Results from another connection are not reused. No claim is made about hotspot ownership or packet inspection.")
        return ConnectionCheckReport(ReportOrigin.LIVE, rows,
            "12 checklist items combine passive observations and 3 kinds of active check (DNS, TCP, HTTPS). Unknown or not-run is not a pass. This is not a vulnerability scan or a security certification.")
    }
}
