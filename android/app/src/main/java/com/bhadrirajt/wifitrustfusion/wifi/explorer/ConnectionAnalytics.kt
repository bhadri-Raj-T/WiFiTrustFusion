package com.bhadrirajt.wifitrustfusion.wifi.explorer

import kotlin.math.sqrt

/** In-memory consumer analytics. Never writes collector CSVs or the Room mirror. */
internal object ConnectionAnalytics {
    data class Sample(val elapsedMs: Long, val rssiDbm: Int)

    data class SignalSummary(
        val count: Int,
        val durationSeconds: Long,
        val medianDbm: Double?,
        val minimumDbm: Int?,
        val maximumDbm: Int?,
        val standardDeviationDb: Double?
    ) {
        val rangeDb: Int? get() = if (minimumDbm == null || maximumDbm == null) null
            else maximumDbm - minimumDbm
    }

    data class Advice(val title: String, val detail: String)

    fun cleanSsid(raw: String?): String? = raw?.removeSurrounding("\"")?.takeIf {
        it.isNotBlank() && !it.equals("<unknown ssid>", ignoreCase = true)
    }

    fun cleanBssid(raw: String?): String? = raw?.lowercase()?.takeIf {
        it.matches(Regex("(?:[0-9a-f]{2}:){5}[0-9a-f]{2}")) &&
            it !in setOf("02:00:00:00:00:00", "00:00:00:00:00:00", "ff:ff:ff:ff:ff:ff")
    }

    // -127 is Android's unavailable RSSI sentinel; zero and positive values are invalid here.
    fun validRssi(value: Int?): Boolean = value != null && value in -126..-1

    fun summarise(samples: List<Sample>): SignalSummary {
        val valid = samples.filter { validRssi(it.rssiDbm) }.sortedBy { it.elapsedMs }
        if (valid.isEmpty()) return SignalSummary(0, 0, null, null, null, null)
        val values = valid.map { it.rssiDbm }.sorted()
        val middle = values.size / 2
        val median = if (values.size % 2 == 1) values[middle].toDouble()
            else (values[middle - 1].toDouble() + values[middle]) / 2.0
        val mean = values.average()
        // Descriptive population SD of these polled readings, not independent scan samples.
        val sd = sqrt(values.map { (it - mean) * (it - mean) }.average())
        return SignalSummary(valid.size, (valid.last().elapsedMs - valid.first().elapsedMs) / 1000,
            median, values.first(), values.last(), sd)
    }

    class SignalWindow(private val capacity: Int = 60) {
        private val readings = mutableListOf<Sample>()
        var connectionKey: String? = null
            private set

        fun clear() { readings.clear(); connectionKey = null }

        fun record(key: String?, elapsedMs: Long, rssi: Int?) {
            if (key == null) { clear(); return }
            val previous = readings.lastOrNull()
            // Never join different APs, connections, or a long background gap on one chart.
            if (key != connectionKey || (previous != null &&
                    (elapsedMs < previous.elapsedMs || elapsedMs - previous.elapsedMs > 6000))) {
                readings.clear()
                connectionKey = key
            }
            if (!validRssi(rssi)) return
            if (readings.lastOrNull()?.let { elapsedMs - it.elapsedMs < 1500 } == true) return
            readings.add(Sample(elapsedMs, rssi!!))
            while (readings.size > capacity.coerceAtLeast(1)) readings.removeAt(0)
        }

        fun samples(): List<Sample> = readings.toList()
    }

    fun recommendations(
        connected: Boolean,
        identityKnown: Boolean,
        rssi: Int?,
        summary: SignalSummary,
        portal: Boolean,
        validated: Boolean,
        security: String,
        dnsOk: Boolean?,
        httpsOk: Boolean?
    ): List<Advice> = buildList {
        if (!connected) {
            add(Advice("Connect to Wi-Fi", "Choose a network using Android, then return here for its live report."))
            return@buildList
        }
        if (!identityKnown) add(Advice("Network name is hidden", "Allow Location access and enable the phone's Location setting to display its name. Internet checks can still use the connected Wi-Fi."))
        if (portal) add(Advice("Complete the network sign-in", "Android detected a login portal. Use the system sign-in screen, then run the checks again."))
        if (validRssi(rssi) && rssi!! < -75) add(Advice("Try a closer position", "The latest signal is $rssi dBm. Move closer to the access point and compare the chart."))
        if (summary.count >= 5 && summary.durationSeconds >= 8 && (summary.rangeDb ?: 0) >= 12) {
            add(Advice("Signal changed during this observation", "The displayed readings span ${summary.rangeDb} dB over ${summary.durationSeconds}s. Position and the surrounding environment can affect signal."))
        }
        if (security == "Open Wi-Fi" || security == "WEP (legacy)") {
            add(Advice("Review the Wi-Fi security setting", "This report indicates ${security.lowercase()}. Prefer a trusted network with modern link protection for sensitive activity."))
        }
        if (dnsOk == false) add(Advice("Name lookup did not complete", "The test hostname could not be resolved over this Wi-Fi. Check sign-in and network settings, then retry."))
        if (httpsOk == false) add(Advice("HTTPS check did not complete", "The test endpoint was not verified. A timeout, filtering or an endpoint problem can cause this result."))
        if (!validated && !portal) add(Advice("Internet access is not yet confirmed", "Android has not validated this Wi-Fi connection. Review the DNS and HTTPS results."))
        if (summary.count < 5) add(Advice("Building signal history", "Keep this screen open for several readings. The chart describes only this short observation window."))
        if (isEmpty()) add(Advice("Connection observations look consistent", "Review the measured signal and test results below. Connectivity checks do not establish who operates the hotspot."))
    }
}
