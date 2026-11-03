package com.bhadrirajt.wifitrustfusion.wifi.explorer

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.SystemClock
import java.util.concurrent.ConcurrentHashMap

/** Reads the actual Wi-Fi connection. It does not scan, connect, persist or send probes. */
internal class WifiConnectionMonitor(context: Context) {
    data class Snapshot(
        val network: Network? = null,
        val ssid: String? = null,
        val bssid: String? = null,
        val rssiDbm: Int? = null,
        val frequencyMhz: Int? = null,
        val linkSpeedMbps: Int? = null,
        val security: String = "Unavailable",
        val securitySource: String = "No current security information",
        val validated: Boolean = false,
        val captivePortal: Boolean = false,
        val status: String = "Not connected to Wi-Fi",
        val securityFromCurrentConnection: Boolean = false,
        val securityAgeSeconds: Long? = null
    ) {
        val connected: Boolean get() = network != null
        val routeKey: String? get() = network?.let { "wifi:${it.networkHandle}" }
        fun sameConnection(other: Snapshot): Boolean = WifiConnectionPolicy.sameConnection(
            network?.networkHandle, other.network?.networkHandle, ssid, other.ssid, bssid, other.bssid)
        val key: String? get() = network?.let { "${it.networkHandle}|${ssid.orEmpty()}|${bssid.orEmpty()}" }
        val internetLabel: String get() = when {
            !connected -> "Not connected"
            captivePortal -> "Sign-in required"
            validated -> "Internet validated by Android"
            else -> "Internet not yet validated"
        }
    }

    private val app = context.applicationContext
    private val cm = app.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val wifi = app.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private val observed = ConcurrentHashMap<Network, NetworkCapabilities>()
    private var callback: ConnectivityManager.NetworkCallback? = null

    fun start() {
        if (callback != null) return
        val cb = if (Build.VERSION.SDK_INT >= 31) {
            object : ConnectivityManager.NetworkCallback(ConnectivityManager.NetworkCallback.FLAG_INCLUDE_LOCATION_INFO) {
                override fun onCapabilitiesChanged(n: Network, caps: NetworkCapabilities) { observed[n] = caps }
                override fun onLost(n: Network) { observed.remove(n) }
            }
        } else {
            object : ConnectivityManager.NetworkCallback() {
                override fun onCapabilitiesChanged(n: Network, caps: NetworkCapabilities) { observed[n] = caps }
                override fun onLost(n: Network) { observed.remove(n) }
            }
        }
        try {
            cm.registerNetworkCallback(NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI).build(), cb)
            callback = cb
        } catch (_: Exception) { /* Polling remains available if registration is refused. */ }
    }

    fun stop() {
        callback?.let { try { cm.unregisterNetworkCallback(it) } catch (_: Exception) {} }
        callback = null
        observed.clear()
    }

    @Suppress("DEPRECATION")
    fun snapshot(): Snapshot {
        val networks = try {
            cm.allNetworks.mapNotNull { n ->
                cm.getNetworkCapabilities(n)?.takeIf {
                    it.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) &&
                        !it.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
                }?.let { n to it }
            }
        } catch (_: Exception) { return Snapshot(status = "Network information is unavailable") }
        if (networks.isEmpty()) return Snapshot()
        val defaultNetwork = cm.activeNetwork
        val chosen = networks.firstOrNull { it.first == defaultNetwork }
            ?: networks.singleOrNull()
            ?: return Snapshot(status = "Several Wi-Fi connections are present; the active connection could not be identified")
        val (network, caps) = chosen
        val trackedInfo = if (Build.VERSION.SDK_INT >= 29)
            observed[network]?.transportInfo as? WifiInfo else null
        val directInfo = if (Build.VERSION.SDK_INT >= 29) caps.transportInfo as? WifiInfo else null
        // Older Samsung phones may expose several Wi-Fi Network records. The default
        // Wi-Fi is still the current radio connection described by getConnectionInfo().
        val useLegacy = WifiConnectionPolicy.allowLegacyInfo(Build.VERSION.SDK_INT, networks.size, network == defaultNetwork)
        val legacyInfo = if (useLegacy) try { wifi.connectionInfo } catch (_: Exception) { null } else null
        val candidates = listOfNotNull(directInfo, trackedInfo, legacyInfo)
        val identityInfo = candidates.firstOrNull { ConnectionAnalytics.cleanSsid(it.ssid) != null }
        val ssid = ConnectionAnalytics.cleanSsid(identityInfo?.ssid)
        val identityBssid = ConnectionAnalytics.cleanBssid(identityInfo?.bssid)
        val compatible = candidates.filter { candidate ->
            val name = ConnectionAnalytics.cleanSsid(candidate.ssid)
            val ap = ConnectionAnalytics.cleanBssid(candidate.bssid)
            (name == null || ssid == null || name == ssid) &&
                (ap == null || identityBssid == null || ap.equals(identityBssid, true))
        }
        val info = identityInfo ?: compatible.firstOrNull()
        val bssid = identityBssid ?: compatible.firstNotNullOfOrNull { ConnectionAnalytics.cleanBssid(it.bssid) }
        // SSID can be redacted while radio measurements remain available.
        val rssi = compatible.firstNotNullOfOrNull { it.rssi.takeIf { value -> ConnectionAnalytics.validRssi(value) } }
        val frequency = compatible.firstNotNullOfOrNull { it.frequency.takeIf { value -> value > 0 } }
        val speed = compatible.firstNotNullOfOrNull { it.linkSpeed.takeIf { value -> value > 0 } }
        var security = "Unavailable"
        var currentSecurity = false
        var securityAgeSeconds: Long? = null
        var source = "Security information is unavailable for this connection"
        if (Build.VERSION.SDK_INT >= 31 && info != null) {
            security = when (info.currentSecurityType) {
                WifiInfo.SECURITY_TYPE_OPEN -> "Open Wi-Fi"
                WifiInfo.SECURITY_TYPE_WEP -> "WEP (legacy)"
                WifiInfo.SECURITY_TYPE_PSK -> "WPA/WPA2-Personal"
                WifiInfo.SECURITY_TYPE_EAP -> "Enterprise authentication"
                WifiInfo.SECURITY_TYPE_SAE -> "WPA3-Personal"
                WifiInfo.SECURITY_TYPE_OWE -> "Enhanced Open (OWE)"
                WifiInfo.SECURITY_TYPE_EAP_WPA3_ENTERPRISE,
                WifiInfo.SECURITY_TYPE_EAP_WPA3_ENTERPRISE_192_BIT -> "WPA3-Enterprise"
                WifiInfo.SECURITY_TYPE_WAPI_PSK,
                WifiInfo.SECURITY_TYPE_WAPI_CERT -> "WAPI"
                WifiInfo.SECURITY_TYPE_PASSPOINT_R1_R2,
                WifiInfo.SECURITY_TYPE_PASSPOINT_R3 -> "Passpoint"
                else -> "Unavailable"
            }
            if (security != "Unavailable") { source = "Current connection reported by Android"; currentSecurity = true }
        }
        if (security == "Unavailable" && bssid != null) {
            val scan = try { wifi.scanResults.firstOrNull { it.BSSID.equals(bssid, true) } }
                catch (_: Exception) { null }
            val ageMicros = scan?.let { SystemClock.elapsedRealtimeNanos() / 1000 - it.timestamp }
            if (scan != null && scan.timestamp > 0 && ageMicros != null && ageMicros in 0..120_000_000L) {
                security = WifiSecurityRules.classify(scan.capabilities.orEmpty()).label
                securityAgeSeconds = (ageMicros + 999_999L) / 1_000_000
                source = "Advertisement from this AP, ${ageMicros / 1_000_000}s ago; negotiated security unavailable"
            }
        }
        return Snapshot(network, ssid, bssid, rssi, frequency, speed, security, source,
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED),
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL),
            if (ssid == null) "Wi-Fi connected; Android has not provided its name. Internet checks are available." else "Connected to $ssid",
            securityFromCurrentConnection = currentSecurity, securityAgeSeconds = securityAgeSeconds)
    }
}
