package com.bhadrirajt.wifitrustfusion.wifi.explorer

/** Invented inputs only. This model has no Android/network/radio APIs. */
internal data class FailureScenario(
    val id: String, val title: String, val category: String, val why: String, val advice: String,
    val checks: List<CheckItem>, val samples: List<Int>, val isolationEligible: Boolean = false
)
internal object FailureScenarios {
    private val strong = listOf(-48, -49, -47, -48, -46, -49, -48, -47)
    private fun item(id: String, title: String, value: String, state: CheckState = CheckState.PASS) =
        CheckItem(id, title, state, value, "Invented fixture for teaching. No live network check was performed.")
    private val baseline = listOf(
        item("route", "Wi-Fi connection", "Virtual Wi-Fi connected"),
        item("identity", "Network visibility", "Invented name: DEMO_Lab", CheckState.INFO),
        item("signal", "Current radio signal", "Illustrative -48 dBm", CheckState.INFO),
        item("variation", "Signal window", "8 invented readings / 14 seconds / 3 dB range", CheckState.INFO),
        item("security", "Wi-Fi configuration", "Invented WPA2 configuration", CheckState.INFO),
        item("portal", "Captive sign-in", "No portal in this fixture", CheckState.INFO),
        item("validation", "Android validation", "Invented validated state"),
        item("dns", "DNS lookup", "Invented successful lookup"),
        item("tcp", "TCP reachability", "Invented 3/3 connects / median 35 ms"),
        item("https", "HTTPS endpoint identity", "Invented successful verification"),
        item("http", "HTTP response", "Invented HTTP 200"),
        item("continuity", "Connection continuity", "Same virtual connection at checkpoints")
    )
    private fun c(id: String, title: String, group: String, why: String, advice: String,
        edits: Map<String, Pair<CheckState, String>> = emptyMap(), isolate: Boolean = false,
        samples: List<Int> = strong, blocked: Boolean = false): FailureScenario {
        val rows = baseline.map { row ->
            val base = when {
                blocked -> row.copy(state = CheckState.PENDING, value = "Not run: no virtual connection")
                row.id == "signal" && samples.isNotEmpty() -> row.copy(value = "Invented ${samples.last()} dBm")
                row.id == "variation" && samples.isNotEmpty() -> row.copy(value = "${samples.size} invented readings / ${(samples.size-1)*2}s / ${samples.max()-samples.min()} dB range")
                else -> row
            }
            edits[row.id]?.let { base.copy(state = it.first, value = it.second) } ?: base
        }
        return FailureScenario(id, title, group, why, advice, rows, samples, isolate)
    }
    private fun fail(value: String) = CheckState.FAILED to value
    private fun warn(value: String) = CheckState.WARNING to value
    private fun unknown(value: String) = CheckState.UNAVAILABLE to value
    private fun skip(value: String) = CheckState.PENDING to value
    private val skipAfterDns = mapOf("tcp" to skip("Skipped after DNS failure"), "https" to skip("Skipped after DNS failure"), "http" to skip("No HTTPS response"))
    private val discardProbes = listOf("dns", "tcp", "https", "http").associateWith { CheckState.CANCELLED to "Result discarded; do not reuse" }

    val all: List<FailureScenario> = listOf(
        c("healthy", "Healthy connection baseline", "Baseline", "A healthy fixture helps compare failure reports.", "Compare individual results; success does not certify the hotspot owner."),
        c("wifi_off", "Wi-Fi radio off", "Connection", "The virtual Wi-Fi radio is off, so network probes cannot run.", "Enable Wi-Fi through Android and reconnect.", mapOf("route" to fail("Virtual Wi-Fi radio off")), blocked = true, samples = emptyList()),
        c("disconnected", "Wi-Fi on, no connection", "Connection", "An enabled radio does not guarantee an associated network.", "Choose an authorised network and confirm the actual connection.", mapOf("route" to fail("Virtual radio enabled; no Wi-Fi route")), blocked = true, samples = emptyList()),
        c("identity_hidden", "Permission or Location hides identity", "Permissions", "A hidden name is missing evidence, not a detected vulnerability.", "Allow visibility and enable Location where needed. Route-bound checks can still be possible.", mapOf("identity" to unknown("Android did not provide a network name"))),
        c("weak_signal", "Low-signal pause rule", "Signal", "This fixture crosses a deliberately configured demonstration threshold. Signal is not hacker evidence.", "Move closer or reconnect; the simulator can demonstrate pausing the virtual session.", mapOf("signal" to fail("3 invented polls below the selected demo threshold")), isolate = true, samples = listOf(-48,-54,-62,-71,-78,-81,-84,-85)),
        c("variation", "Signal changes during observation", "Signal", "Movement, walls and interference can explain variation.", "Compare position and observe more readings; variation alone is not a reason to identify an attacker.", mapOf("variation" to warn("Invented range: 26 dB across 8 readings")), samples = listOf(-45,-60,-47,-71,-49,-66,-51,-45)),
        c("open", "Open Wi-Fi configuration", "Configuration", "The fixture has no Wi-Fi link encryption. It does not prove that a hacker is present.", "Use a trusted protected network. An explicitly armed configuration rule can pause this phone's Wi-Fi.", mapOf("security" to fail("Invented Open Wi-Fi configuration")), isolate = true),
        c("wep", "Legacy WEP configuration", "Configuration", "WEP is a legacy link-protection configuration in this invented example.", "Use an authorised network with modern protection.", mapOf("security" to fail("Invented WEP configuration")), isolate = true),
        c("security_unknown", "Security metadata unavailable", "Permissions", "The app lacks current or recent matching security information.", "Do not invent a secure/unsafe result. Refresh visibility or verify the configuration with the operator.", mapOf("security" to unknown("No usable security observation"))),
        c("lookalike", "Look-alike hotspot name", "Identity", "Invented names DEMO_Lab and DEMO_Lab_Free illustrate a naming concern. Name similarity does not authenticate an operator.", "Confirm the exact network through a trusted source before entering credentials.", mapOf("identity" to warn("Invented similar network names; no attacker detected"))),
        c("ap_change", "Same name, different access point", "Identity", "Roaming and router replacement can change the AP identifier legitimately.", "Verify the change. Do not label it an evil twin from SSID/BSSID alone.", discardProbes + mapOf("continuity" to (CheckState.CANCELLED to "Invented AP change; previous results discarded"))),
        c("portal", "Captive portal needs sign-in", "Internet", "A portal can restrict access until sign-in and is not itself evidence of phishing.", "Use Android's sign-in controls and verify the operator/address.", mapOf("portal" to warn("Invented portal flag"), "validation" to warn("Not yet validated"), "dns" to skip("Complete sign-in first"), "tcp" to skip("Complete sign-in first"), "https" to skip("Complete sign-in first"), "http" to skip("No endpoint status yet"))),
        c("dns", "DNS lookup times out", "Internet", "Timeout, filtering, a portal or endpoint access problems may explain this. It does not prove DNS poisoning.", "Retry a known connection; optional simulator policy pauses only the virtual session.", skipAfterDns + mapOf("dns" to fail("Invented DNS timeout after 3 seconds")), isolate = true),
        c("tcp_blocked", "All TCP connection attempts fail", "Internet", "A firewall, route issue or endpoint problem can block connections.", "Inspect network access and retry; a TCP failure is not an attack verdict.", mapOf("tcp" to fail("Invented 0/3 TCP connects"), "https" to fail("Invented HTTPS connect timeout"), "http" to skip("No response received")), isolate = true),
        c("tcp_partial", "Some TCP probes fail", "Internet", "Partial reachability is different from total failure; HTTPS may still succeed on another connection.", "Repeat later and compare results without assuming an attacker.", mapOf("tcp" to warn("Invented 1/3 successful / median 95 ms"))),
        c("slow", "Slow TCP connection establishment", "Internet", "The simulator uses an illustrative timing threshold. This is not a throughput measurement or a validated risk score.", "Compare positions and networks. A slow connect is not proof of interception.", mapOf("tcp" to warn("Invented 3/3 successful / median 850 ms"))),
        c("tls", "HTTPS identity check rejected", "Internet", "This invented fixture represents a certificate/hostname problem. A live generic HTTPS failure does not establish its exact cause.", "Do not bypass certificate warnings. Use a known connection; the simulator can pause its virtual session.", mapOf("https" to fail("Invented certificate/hostname verification rejection"), "http" to skip("No verified HTTPS response")), isolate = true),
        c("http", "Endpoint returns HTTP 503", "Internet", "A verified HTTPS endpoint can still report an application/service error.", "Retry the endpoint. This does not mean the Wi-Fi encryption or TLS verification failed.", mapOf("http" to warn("Invented HTTP 503; TLS verification passed"))),
        c("changed", "Connection changes during checks", "Connection", "A report must not carry results from the previous connection into the new one.", "Discard the incomplete report and run again on the actual connected network.", discardProbes + mapOf("continuity" to (CheckState.CANCELLED to "Virtual network changed during the run"))),
        c("cancelled", "User cancels the test session", "Connection", "An unfinished test is not a successful result.", "Show cancellation and allow a new run; do not invent missing outcomes.", discardProbes + mapOf("continuity" to (CheckState.CANCELLED to "Virtual test run cancelled by the user")))
    )

    data class Outcome(val report: ConnectionCheckReport, val virtualPaused: Boolean, val response: String)
    fun outcome(scenario: FailureScenario, simulatePause: Boolean): Outcome {
        val pause = simulatePause && scenario.isolationEligible && scenario.checks.any { it.state == CheckState.FAILED }
        val response = if (pause) "SIMULATED AUTO RESPONSE: virtual Wi-Fi paused. The phone's real Wi-Fi was not changed."
            else "SIMULATED RESPONSE: advice or missing-evidence handling only. The phone's real Wi-Fi was not changed."
        return Outcome(ConnectionCheckReport(ReportOrigin.SIMULATED, scenario.checks,
            "SIMULATED fixture, not measurements from your connected Wi-Fi. ${scenario.why}\n${scenario.advice}\n$response"), pause, response)
    }
}
