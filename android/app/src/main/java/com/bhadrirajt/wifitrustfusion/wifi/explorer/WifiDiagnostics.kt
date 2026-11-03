package com.bhadrirajt.wifitrustfusion.wifi.explorer

import android.os.SystemClock
import java.net.InetSocketAddress
import java.net.URL
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.net.ssl.HttpsURLConnection

/** User-triggered checks bound to one identified Wi-Fi Network, never the default route. */
internal object WifiDiagnostics {
    private const val HOST = "example.com"

    data class Result(
        val connectionKey: String,
        val completedAtMs: Long,
        val summary: String,
        val tcpTimesMs: List<Long>,
        val tcpAttempts: Int,
        val dnsOk: Boolean?,
        val httpsOk: Boolean?,
        val dnsStatus: String,
        val httpsStatus: String,
        val explanation: String
    ) {
        val medianConnectMs: Double? get() {
            val values = tcpTimesMs.sorted()
            if (values.isEmpty()) return null
            val m = values.size / 2
            return if (values.size % 2 == 1) values[m].toDouble()
                else (values[m - 1].toDouble() + values[m]) / 2.0
        }
        val tcpFailures: Int get() = tcpAttempts - tcpTimesMs.size
    }

    fun run(target: WifiConnectionMonitor.Snapshot, monitor: WifiConnectionMonitor): Result {
        val key = target.routeKey.orEmpty()
        fun stillSame(): Boolean {
            val now = monitor.snapshot()
            return target.sameConnection(now)
        }
        fun blocked(reason: String) = Result(key, System.currentTimeMillis(),
            "Checks were not completed", emptyList(), 0, null, null,
            "Not tested", "Not tested", reason)
        if (!stillSame()) return blocked("The selected Wi-Fi connection could not be confirmed. No probes sent.")
        val network = target.network ?: return blocked("Wi-Fi is disconnected. No probes sent.")
        val resolver = Executors.newSingleThreadExecutor { job ->
            Thread(job, "wifi-dns-check").apply { isDaemon = true }
        }
        val dnsTask = resolver.submit<java.net.InetAddress> { network.getByName(HOST) }
        val address = try { dnsTask.get(3, TimeUnit.SECONDS) }
            catch (_: Exception) { null }
            finally { dnsTask.cancel(true); resolver.shutdownNow() }
        if (!stillSame()) return blocked("Wi-Fi changed during the checks. Results were discarded; run again for the current connection.")
        val times = mutableListOf<Long>()
        var attempts = 0
        if (address != null) repeat(3) {
            if (!stillSame()) return blocked("Wi-Fi changed during the checks. Results were discarded.")
            attempts++
            try {
                network.socketFactory.createSocket().use { socket ->
                    val start = SystemClock.elapsedRealtime()
                    socket.connect(InetSocketAddress(address, 443), 1800)
                    times.add(SystemClock.elapsedRealtime() - start)
                }
            } catch (_: Exception) { /* Failed reachability is not an attack label. */ }
        }
        if (!stillSame()) return blocked("Wi-Fi changed during the checks. Results were discarded.")
        var httpsOk: Boolean? = null
        val https = if (address == null) "Skipped: hostname lookup did not complete" else try {
            val connection = network.openConnection(URL("https://$HOST/")) as HttpsURLConnection
            connection.connectTimeout = 2500
            connection.readTimeout = 2500
            connection.instanceFollowRedirects = false
            try {
                connection.requestMethod = "HEAD"
                val code = connection.responseCode
                httpsOk = true
                "TLS/hostname verified; HTTP $code"
            } finally { connection.disconnect() }
        } catch (e: Exception) {
            httpsOk = false
            "Not verified (${e.javaClass.simpleName})"
        }
        if (!stillSame()) return blocked("Wi-Fi changed during the checks. Results were discarded.")
        return Result(key, System.currentTimeMillis(), "Wi-Fi connection checks completed",
            times.toList(), attempts, address != null, httpsOk,
            if (address != null) "Resolved $HOST over this Wi-Fi" else "Lookup failed or exceeded 3 seconds",
            https,
            "The test contacted $HOST over this Wi-Fi only. TCP connect time includes connection establishment; it is not download speed or ICMP ping. HTTPS verifies the test endpoint, not the hotspot operator.")
    }
}
