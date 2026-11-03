package com.bhadrirajt.wifitrustfusion.wifi.explorer

import android.Manifest
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ImageView
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.bhadrirajt.wifitrustfusion.MainActivity
import com.bhadrirajt.wifitrustfusion.R
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.Future

/** Consumer M-stage UI. Collector, research CSVs and the Room mirror remain independent. */
class WifiExplorerActivity : AppCompatActivity() {
    private data class AccessPoint(
        val ssid: String, val bssid: String, val level: Int, val frequency: Int,
        val capabilities: String, val apCount: Int, val ageSeconds: Long?, val demo: Boolean = false
    )
    private data class AnalysisViews(
        val name: TextView, val status: TextView, val signal: TextView,
        val internet: TextView, val security: TextView, val securitySource: TextView,
        val link: TextView, val chart: SignalChartView, val statistics: TextView,
        val advice: TextView, val tests: TextView, val run: Button
    )
    private val bg = Color.rgb(13, 18, 31)
    private val panel = Color.rgb(23, 32, 49)
    private val panelSoft = Color.rgb(33, 45, 65)
    private val violet = Color.rgb(183, 166, 255)
    private val teal = Color.rgb(117, 234, 213)
    private val white = Color.rgb(246, 248, 255)
    private val muted = Color.rgb(175, 189, 209)
    private val amber = Color.rgb(250, 194, 100)
    private lateinit var body: LinearLayout
    private lateinit var root: LinearLayout
    private lateinit var scroll: ScrollView
    private lateinit var navigation: LinearLayout
    private lateinit var monitor: WifiConnectionMonitor
    private val handler = Handler(Looper.getMainLooper())
    private val workers = Executors.newSingleThreadExecutor { job ->
        Thread(job, "wifi-connection-check").apply { isDaemon = true }
    }
    private var pendingDiagnostic: Future<*>? = null
    private val history = ConnectionAnalytics.SignalWindow()
    private var current = WifiConnectionMonitor.Snapshot()
    private var page = 0
    private var demo = false
    private var selected: AccessPoint? = null
    private var cachedNetworks: List<AccessPoint> = emptyList()
    private var lastDiagnostics: WifiDiagnostics.Result? = null
    private var lastRunMs: Long? = null
    private var busy = false
    private var runId = 0
    private var foreground = false
    private var awaitingSsid: String? = null
    private var analyseOnReturn = false
    private var connectionNotice = ""
    private var checkNotice = ""
    private var analysisViews: AnalysisViews? = null
    private var connectionName: TextView? = null
    private var connectionState: TextView? = null
    private var selectedState: TextView? = null
    private val poll = object : Runnable {
        override fun run() {
            if (!foreground) return
            refreshConnection()
            handler.postDelayed(this, 2000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        monitor = WifiConnectionMonitor(applicationContext)
        demo = savedInstanceState?.getBoolean("sample_preview") ?: false
        awaitingSsid = savedInstanceState?.getString("awaiting_ssid")
        analyseOnReturn = savedInstanceState?.getBoolean("analyse_on_return") ?: false
        connectionNotice = savedInstanceState?.getString("connection_notice").orEmpty()
        savedInstanceState?.getString("selected_ssid")?.let { name ->
            selected = AccessPoint(name, savedInstanceState.getString("selected_bssid").orEmpty(),
                savedInstanceState.getInt("selected_level", -127), savedInstanceState.getInt("selected_frequency"),
                savedInstanceState.getString("selected_capabilities").orEmpty(),
                savedInstanceState.getInt("selected_count", 1),
                savedInstanceState.getLong("selected_age", -1).takeIf { it >= 0 }, demo)
        }
        if (!demo) selected = null
        buildShell()
        showPage(savedInstanceState?.getInt("page", 0) ?: 0)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    page != 0 -> showPage(0)
                    selected != null -> { selected = null; showPage(0) }
                    demo -> exitDemo()
                    else -> { isEnabled = false; onBackPressedDispatcher.onBackPressed() }
                }
            }
        })
    }

    override fun onStart() { super.onStart(); monitor.start() }
    override fun onResume() {
        super.onResume()
        foreground = true
        refreshConnection()
        if (!demo && analyseOnReturn) {
            analyseOnReturn = false
            selected = null
            if (current.connected) {
                showPage(1)
            } else {
                connectionNotice = "No Wi-Fi connection yet. Choose a network on this phone to continue."
                showPage(0)
            }
        }
        if (page == 0 && selected == null) showPage(0)
        handler.removeCallbacks(poll)
        handler.postDelayed(poll, 2000)
    }
    override fun onPause() {
        foreground = false
        handler.removeCallbacks(poll)
        super.onPause()
    }
    override fun onStop() { monitor.stop(); super.onStop() }
    override fun onDestroy() {
        runId++
        pendingDiagnostic?.cancel(true)
        workers.shutdownNow()
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt("page", page)
        outState.putBoolean("sample_preview", demo)
        outState.putString("awaiting_ssid", awaitingSsid)
        outState.putBoolean("analyse_on_return", analyseOnReturn)
        outState.putString("connection_notice", connectionNotice)
        selected?.let {
            outState.putString("selected_ssid", it.ssid)
            outState.putString("selected_bssid", it.bssid)
            outState.putInt("selected_level", it.level)
            outState.putInt("selected_frequency", it.frequency)
            outState.putString("selected_capabilities", it.capabilities)
            outState.putInt("selected_count", it.apCount)
            outState.putLong("selected_age", it.ageSeconds ?: -1)
        }
        super.onSaveInstanceState(outState)
    }

    private fun dp(value: Int): Int = (resources.displayMetrics.density * value + 0.5f).toInt()
    private fun bgDrawable(color: Int, radius: Int = 18) = GradientDrawable().apply {
        setColor(color); cornerRadius = dp(radius).toFloat()
    }
    private fun vertical() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        layoutParams = LinearLayout.LayoutParams(-1, -2)
    }
    private fun line(text: String, size: Float = 14f, color: Int = white, bold: Boolean = false) =
        TextView(this).apply {
            this.text = text; textSize = size; setTextColor(color)
            if (bold) setTypeface(typeface, Typeface.BOLD)
            setLineSpacing(dp(3).toFloat(), 1f)
        }
    private fun add(parent: LinearLayout, child: View, top: Int = 0) {
        parent.addView(child, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(top) })
    }
    private fun card(parent: LinearLayout, top: Int = 12, color: Int = panel, content: LinearLayout.() -> Unit) {
        val c = vertical().apply {
            background = bgDrawable(color)
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }
        c.content(); add(parent, c, top)
    }
    private fun button(label: String, prominent: Boolean = false, action: () -> Unit) = Button(this).apply {
        text = label; isAllCaps = false; textSize = 13f
        setTextColor(if (prominent) bg else white)
        background = RippleDrawable(
            ColorStateList.valueOf(if (prominent) Color.argb(45, 13, 18, 31) else Color.argb(45, 117, 234, 213)),
            bgDrawable(if (prominent) teal else panelSoft, 13), null)
        stateListAnimator = null
        minHeight = dp(49)
        setPadding(dp(9), dp(8), dp(9), dp(8))
        setOnClickListener { action() }
    }
    private fun separator(parent: LinearLayout) {
        parent.addView(View(this).apply { setBackgroundColor(panelSoft) },
            LinearLayout.LayoutParams(-1, dp(1)).apply { topMargin = dp(14); bottomMargin = dp(14) })
    }
    private fun heading(label: String) { add(body, line(label, 24f, white, true), 20) }
    private fun requestVisibility() {
        if (Build.VERSION.SDK_INT >= 23) requestPermissions(arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), 300)
    }
    private fun permissionAvailable() = Build.VERSION.SDK_INT < 23 ||
        checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun buildShell() {
        root = vertical().apply {
            layoutParams = LinearLayout.LayoutParams(-1, -1)
            setBackgroundColor(bg)
        }
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(root)
        WindowInsetsControllerCompat(window, root).apply {
            isAppearanceLightStatusBars = false; isAppearanceLightNavigationBars = false
        }
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            val keyboard = insets.getInsets(WindowInsetsCompat.Type.ime())
            view.setPadding(bars.left, bars.top, bars.right, maxOf(bars.bottom, keyboard.bottom))
            insets
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(18), dp(12), dp(18), dp(10))
        }
        val logo = ImageView(this).apply {
            setImageResource(R.drawable.wtf_brand_mark)
            contentDescription = "WiFiTrustFusion logo"
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        header.addView(logo, LinearLayout.LayoutParams(dp(42), dp(42)))
        val title = vertical()
        add(title, line("WiFiTrustFusion", 18.5f, white, true).apply {
            maxLines = 1; ellipsize = TextUtils.TruncateAt.END
        })
        add(title, line("Connection insights", 11f, teal).apply {
            maxLines = 1; ellipsize = TextUtils.TruncateAt.END
        }, 2)
        header.addView(title, LinearLayout.LayoutParams(0, -2, 1f).apply {
            leftMargin = dp(10); rightMargin = dp(8)
        })
        header.addView(button("Safety") {
            startActivity(Intent(this, SafetyDemoActivity::class.java))
        }.apply { contentDescription = "Open live Wi-Fi pause demo and simulated security examples" },
            LinearLayout.LayoutParams(dp(56), dp(48)).apply { rightMargin = dp(6) })
        header.addView(button("Tools") {
            startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
        }.apply { contentDescription = "Open project tools and session history" },
            LinearLayout.LayoutParams(dp(56), dp(48)))
        root.addView(header)
        navigation = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(18), 0, dp(18), dp(8))
        }
        root.addView(navigation, LinearLayout.LayoutParams(-1, -2))
        scroll = ScrollView(this).apply { setFillViewport(true); clipToPadding = false }
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        body = vertical().apply { setPadding(dp(18), dp(5), dp(18), dp(28)) }
        scroll.addView(body)
    }

    private fun showPage(index: Int) {
        val previousY = if (page == index) scroll.scrollY else 0
        page = index.coerceIn(0, 2)
        analysisViews = null; connectionName = null; connectionState = null; selectedState = null
        body.removeAllViews()
        val nav = navigation.apply { removeAllViews() }
        listOf("My Wi-Fi", "Analysis", "Reports").forEachIndexed { i, text ->
            nav.addView(button(text, page == i) {
                if (i == 0) selected = null
                showPage(i)
            }.apply {
                isSelected = page == i
                contentDescription = "$text tab"
            }, LinearLayout.LayoutParams(0, dp(48), 1f).apply { if (i > 0) leftMargin = dp(7) })
        }
        when (page) {
            0 -> selected?.let { renderSelectedNetwork(it) } ?: renderNetworkList()
            1 -> if (demo) renderSampleAnalysis() else renderAnalysis()
            else -> renderCommunity()
        }
        scroll.post { scroll.scrollTo(0, previousY) }
    }

    private fun refreshConnection() {
        val next = monitor.snapshot()
        if (!current.sameConnection(next)) {
            lastDiagnostics = null
            checkNotice = if (current.connected) "Connection changed. Run checks for the current Wi-Fi." else ""
        }
        current = next
        if (!demo) history.record(next.key, SystemClock.elapsedRealtime(), next.rssiDbm)
        val target = awaitingSsid
        if (!demo && target != null) {
            if (next.connected && next.ssid == target) {
                awaitingSsid = null
                connectionNotice = "Connected to $target. This report uses the actual connection."
                showPage(1)
            } else {
                connectionNotice = when {
                    !next.connected -> "Connection to $target is not confirmed. Complete the connection in Android."
                    next.ssid == null -> "Wi-Fi is connected, but Android has hidden the name. Grant precise location to confirm $target."
                    else -> "Still connected to ${next.ssid}; $target has not been confirmed. Analysis shows the actual connection."
                }
            }
        }
        updateLiveViews()
    }

    private fun updateLiveViews() {
        connectionName?.text = current.ssid ?: if (current.connected) "Wi-Fi name hidden" else "Connect your Wi-Fi"
        connectionState?.text = current.internetLabel + if (connectionNotice.isNotBlank()) "\n$connectionNotice" else ""
        selectedState?.text = connectionNotice.ifBlank {
            if (current.ssid == selected?.ssid && current.connected) "This network is currently connected."
            else "Android will handle your connection and password."
        }
        val views = analysisViews ?: return
        views.name.text = current.ssid ?: if (current.connected) "Wi-Fi name hidden" else "No Wi-Fi connection"
        views.status.text = current.status + if (connectionNotice.isNotBlank()) "\n$connectionNotice" else ""
        views.signal.text = current.rssiDbm?.let { "$it dBm  ·  ${WifiSecurityRules.signalLabel(it)}" } ?: "Signal unavailable"
        views.internet.text = current.internetLabel
        views.security.text = current.security
        views.securitySource.text = current.securitySource
        views.link.text = listOfNotNull(
            current.frequencyMhz?.let { "${WifiSecurityRules.band(it)} · $it MHz" },
            current.linkSpeedMbps?.let { "Wi-Fi link rate: $it Mbps (not download speed)" },
            current.bssid?.let { "Access point: ${WifiSecurityRules.maskedBssid(it)}" }
        ).joinToString("\n").ifBlank { "Connection details unavailable" }
        val samples = history.samples()
        views.chart.submit(samples)
        val summary = ConnectionAnalytics.summarise(samples)
        views.statistics.text = statisticsText(summary)
        val checks = lastDiagnostics?.takeIf { it.connectionKey == current.routeKey }
        views.advice.text = ConnectionAnalytics.recommendations(current.connected, current.ssid != null,
            current.rssiDbm, summary, current.captivePortal, current.validated, current.security,
            checks?.dnsOk, checks?.httpsOk).joinToString("\n\n") { "${it.title}\n${it.detail}" }
        views.tests.text = checks?.let { result ->
            val median = result.medianConnectMs?.let { String.format(Locale.US, "%.1f ms", it) } ?: "Unavailable"
            "${result.summary}\n${DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(result.completedAtMs))}\n\n" +
                "DNS: ${result.dnsStatus}\nHTTPS: ${result.httpsStatus}\n" +
                "Median TCP connect time: $median\n" +
                "TCP probes: ${result.tcpTimesMs.size}/${result.tcpAttempts} completed\n\n${result.explanation}"
        } ?: if (busy) "Checking DNS, TCP reachability and HTTPS over this Wi-Fi..." else
            checkNotice.ifBlank { "Not tested yet. Tap Check internet connection to test this Wi-Fi." }
        val retrySeconds = lastRunMs?.let {
            ((30_000L - (SystemClock.elapsedRealtime() - it)).coerceAtLeast(0L) + 999L) / 1000L
        } ?: 0L
        views.run.text = when {
            busy -> "Checking connection..."
            !current.connected -> "Connect to Wi-Fi first"
            retrySeconds > 0 -> "Check again in ${retrySeconds}s"
            else -> "Check internet connection"
        }
        views.run.isEnabled = !busy && current.connected && retrySeconds == 0L
        views.run.alpha = if (views.run.isEnabled) 1f else 0.5f
    }

    private fun statisticsText(s: ConnectionAnalytics.SignalSummary): String {
        if (s.count == 0) return "No valid signal readings yet. Enable Wi-Fi and network visibility."
        fun value(v: Double?) = v?.let { String.format(Locale.US, "%.1f", it) } ?: "—"
        val variation = if (s.count < 2) "Waiting for another reading" else "${value(s.standardDeviationDb)} dB SD"
        return "${s.count} readings · ${s.durationSeconds}s observed\n" +
            "Median ${value(s.medianDbm)} dBm · Range ${s.minimumDbm} to ${s.maximumDbm} dBm\n" +
            "Variation: $variation\n" +
            "Polled connection readings every 2s; values can repeat. History resets after a connection change or a long pause."
    }

    @Suppress("DEPRECATION")
    private fun loadFromCache(): Pair<List<AccessPoint>, String> {
        if (!permissionAvailable()) return emptyList<AccessPoint>() to "Allow precise location to view nearby Wi-Fi."
        val wifi = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        if (!wifi.isWifiEnabled) return emptyList<AccessPoint>() to "Wi-Fi is off. Open Android Wi-Fi to enable it."
        val scans: List<ScanResult> = try { wifi.scanResults ?: emptyList() }
            catch (_: Exception) { return emptyList<AccessPoint>() to "Enable Location and Wi-Fi, then refresh the list." }
        val nowMicros = SystemClock.elapsedRealtimeNanos() / 1000
        val list = scans.filter { it.SSID.isNotBlank() }.groupBy { it.SSID }.map { (name, aps) ->
            val best = aps.maxByOrNull { it.level }!!
            val age = if (best.timestamp > 0 && nowMicros >= best.timestamp) (nowMicros - best.timestamp) / 1_000_000 else null
            AccessPoint(name, best.BSSID.orEmpty(), best.level, best.frequency,
                best.capabilities.orEmpty(), aps.size, age)
        }.sortedByDescending { it.level }
        return list to "Android scan cache · ages measured at list refresh"
    }
    private fun samples() = listOf(
        AccessPoint("DEMO_Campus_Secure", "02:11:22:33:44:55", -59, 5180, "[WPA2-PSK-CCMP][ESS]", 2, 3, true),
        AccessPoint("DEMO_Public_Guest", "02:11:22:33:44:66", -74, 2412, "[ESS]", 1, 7, true),
        AccessPoint("DEMO_Library_WPA3", "02:11:22:33:44:77", -67, 5955, "[RSN-SAE-CCMP][ESS]", 1, 9, true)
    )
    private fun exitDemo() {
        demo = false; selected = null; history.clear(); lastDiagnostics = null
        refreshConnection(); showPage(0)
    }

    private fun analyseConnectedWifi() {
        demo = false
        selected = null
        awaitingSsid = null
        connectionNotice = ""
        refreshConnection()
        if (current.connected) showPage(1) else openSystemWifi()
    }

    private fun choosePhoneNetwork() {
        if (demo) { exitDemo(); return }
        refreshConnection()
        val (networks, _) = loadFromCache()
        val connectedName = current.ssid
        val hadConnection = current.connected
        val others = networks.filter { it.ssid != connectedName }
        val labels = mutableListOf<String>()
        if (hadConnection) labels.add("Use connected Wi-Fi · ${connectedName ?: "name hidden"}")
        labels.addAll(others.map { "${it.ssid} · ${WifiSecurityRules.signalLabel(it.level)}" })
        labels.add("Open the phone's Wi-Fi list…")
        AlertDialog.Builder(this)
            .setTitle("Select Wi-Fi from this phone")
            .setItems(labels.toTypedArray()) { _, which ->
                if (hadConnection && which == 0) {
                    analyseConnectedWifi()
                } else {
                    val index = which - if (hadConnection) 1 else 0
                    val network = others.getOrNull(index)
                    openSystemWifi(network?.ssid)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun renderNetworkList() {
        heading("Your Wi-Fi. One tap away.")
        add(body, line("Use the Wi-Fi connected on this phone, or tap a network below.", 14f, muted), 5)
        if (!demo) card(body, 18) {
            add(this, line("CONNECTED ON THIS PHONE", 10f, teal, true))
            connectionName = line("", 23f, white, true).also { add(this, it, 9) }
            connectionState = line("", 12f, muted).also { add(this, it, 7) }
            add(this, button("Analyse connected Wi-Fi", true) { analyseConnectedWifi() }, 14)
            add(this, line("Already connected? Open your report directly. No network name to enter.", 12f, muted), 9)
        } else card(body, 18) {
            add(this, line("SAMPLE PREVIEW · FICTIONAL NETWORKS", 12f, amber, true))
            add(this, line("These examples demonstrate the report. They do not connect or run internet checks.", 12f, muted), 7)
        }
        if (!demo) add(body, button("Choose Wi-Fi") { choosePhoneNetwork() }, 13)
        else add(body, button("Exit sample") { exitDemo() }, 13)
        val (networks, status) = if (demo) samples() to "SAMPLE DATA — NOT LIVE" else loadFromCache()
        cachedNetworks = networks
        if (!demo && !permissionAvailable()) card(body) {
            add(this, line("Let Android show Wi-Fi names", 16f, white, true))
            add(this, line("Allow Location permission and turn on the phone's Location setting to see Wi-Fi names.", 13f, muted), 8)
            add(this, button("Allow Wi-Fi visibility", true) { requestVisibility() }, 12)
            add(this, button("Open Location settings") {
                startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
            }, 8)
        }
        add(body, line(if (demo) "Sample networks" else "Networks seen by this phone", 17f, white, true), 20)
        add(body, line(if (demo) status else "Tap to select. Android handles the connection and password.", 12f, muted), 6)
        if (networks.isEmpty()) card(body) {
            add(this, line("Let the phone find your Wi-Fi", 16f, white, true))
            add(this, line("Open the phone's Wi-Fi list, choose your network and return. Your connected Wi-Fi report will open automatically.", 13f, muted), 8)
            add(this, button("Open phone Wi-Fi list", true) { openSystemWifi() }, 12)
        }
        networks.forEach { n -> card(body, 11) {
            val sec = WifiSecurityRules.classify(n.capabilities)
            add(this, line(n.ssid, 18f, white, true))
            val strength = if (ConnectionAnalytics.validRssi(n.level)) "${WifiSecurityRules.signalLabel(n.level)} · ${n.level} dBm" else "Signal unavailable"
            add(this, line("$strength · ${WifiSecurityRules.band(n.frequency)}", 12f, muted), 7)
            add(this, line(sec.label, 12f, muted), 6)
            add(this, line(n.ageSeconds?.let { "Seen ${it}s before this list refreshed" } ?: "Observation age unavailable", 10f, muted), 6)
            add(this, button(if (n.demo) "View sample report" else "Select this Wi-Fi") {
                when {
                    n.demo -> { selected = n; showPage(1) }
                    current.connected && current.ssid == n.ssid -> analyseConnectedWifi()
                    else -> openSystemWifi(n.ssid)
                }
            }, 11)
        } }
        if (!demo) {
            add(body, button("Refresh networks from phone") { refreshConnection(); showPage(0) }, 14)
            add(body, button("Preview with labelled sample data") {
                demo = true; selected = null; awaitingSsid = null; history.clear(); showPage(0)
            }, 10)
        }
        updateLiveViews()
    }

    private fun renderSelectedNetwork(n: AccessPoint) {
        add(body, button("← Nearby networks") { selected = null; awaitingSsid = null; showPage(0) }, 15)
        heading(n.ssid)
        add(body, line(if (n.demo) "SAMPLE NETWORK · NOT LIVE" else "Cached observation · age at list refresh: ${n.ageSeconds?.let { "${it}s" } ?: "unknown"}", 12f, if (n.demo) amber else muted), 6)
        val sec = WifiSecurityRules.classify(n.capabilities)
        card(body, 16) {
            add(this, line("ADVERTISED CONFIGURATION", 10f, violet, true))
            add(this, line(sec.label, 20f, white, true), 9)
            add(this, line(sec.description, 13f, muted), 8)
            add(this, line(sec.caution, 12f, amber), 10)
            separator(this)
            add(this, line("Signal ${n.level} dBm · ${WifiSecurityRules.band(n.frequency)}\n${n.apCount} AP(s) share this name\nBest observed AP ${WifiSecurityRules.maskedBssid(n.bssid)}", 13f, muted))
        }
        if (n.demo) {
            add(body, button("View sample analysis", true) { showPage(1) }, 15)
            add(body, line("Connection and active diagnostics are disabled for fictional networks.", 12f, amber), 10)
        } else card(body) {
            add(this, line("CONNECT & ANALYSE", 10f, teal, true))
            selectedState = line("", 13f, muted).also { add(this, it, 9) }
            add(this, button("Connect using Android", true) { openSystemWifi(n.ssid) }, 13)
            add(this, line("Choose this network and enter its password in Android. Return here to confirm the connection.", 12f, muted), 9)
            add(this, button("Analyse current Wi-Fi") { showPage(1) }, 11)
        }
        updateLiveViews()
    }

    private fun openSystemWifi(targetSsid: String? = null) {
        if (demo) return
        awaitingSsid = targetSsid
        analyseOnReturn = true
        connectionNotice = if (targetSsid != null) "Waiting for Android to connect to $targetSsid." else ""
        val action = if (Build.VERSION.SDK_INT >= 29) Settings.Panel.ACTION_WIFI else Settings.ACTION_WIFI_SETTINGS
        try { startActivity(Intent(action)) }
        catch (_: Exception) {
            try { startActivity(Intent(Settings.ACTION_WIFI_SETTINGS)) }
            catch (_: Exception) { Toast.makeText(this, "Open Wi-Fi in Android Settings.", Toast.LENGTH_LONG).show() }
        }
    }

    private fun renderAnalysis() {
        heading("How is your Wi-Fi?")
        add(body, line("Your connected network, signal history and internet checks in one place.", 13f, muted), 6)
        lateinit var name: TextView; lateinit var status: TextView
        lateinit var signal: TextView; lateinit var internet: TextView
        lateinit var security: TextView; lateinit var securitySource: TextView; lateinit var link: TextView
        lateinit var chart: SignalChartView; lateinit var stats: TextView
        lateinit var advice: TextView; lateinit var tests: TextView; lateinit var run: Button
        card(body, 17) {
            add(this, line("CONNECTED NETWORK", 10f, teal, true))
            name = line("", 22f, white, true); add(this, name, 9)
            status = line("", 12f, muted); add(this, status, 7)
            internet = line("", 14f, teal, true); add(this, internet, 12)
            add(this, button("Choose or change Wi-Fi") { openSystemWifi() }, 13)
            if (current.ssid == null || current.rssiDbm == null) {
                add(this, line("To show Wi-Fi names and signal details, allow Location access and enable the phone's Location setting. You can still check internet access on the connected Wi-Fi.", 12f, muted), 10)
                if (!permissionAvailable()) add(this, button("Allow Wi-Fi visibility") { requestVisibility() }, 9)
                add(this, button("Open phone Location settings") {
                    startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                }, 9)
            }
        }
        card(body) {
            add(this, line("SIGNAL HISTORY", 10f, violet, true))
            signal = line("", 24f, white, true); add(this, signal, 10)
            chart = SignalChartView(this@WifiExplorerActivity)
            addView(chart, LinearLayout.LayoutParams(-1, dp(190)).apply { topMargin = dp(12) })
            stats = line("", 12f, muted); add(this, stats, 10)
        }
        card(body) {
            add(this, line("WI-FI CONFIGURATION", 10f, violet, true))
            security = line("", 19f, white, true); add(this, security, 10)
            securitySource = line("", 12f, muted); add(this, securitySource, 7)
            link = line("", 13f, muted); add(this, link, 10)
        }
        card(body, color = Color.rgb(23, 45, 49)) {
            add(this, line("WHAT THE OBSERVATIONS MEAN", 10f, teal, true))
            advice = line("", 13f, white); add(this, advice, 11)
        }
        card(body) {
            add(this, line("CONNECTION CHECKS", 10f, violet, true))
            add(this, line("Tap below to check internet access on this Wi-Fi. Small test requests are sent to example.com.", 12f, muted), 10)
            run = button("Check internet connection", true) { launchDiagnostic() }; add(this, run, 13)
            tests = line("", 12f, muted); add(this, tests, 12)
        }
        add(body, line("Connection quality and security configuration are separate observations. This app does not certify hotspot ownership or detect attacks.", 12f, muted), 16)
        analysisViews = AnalysisViews(name, status, signal, internet, security, securitySource,
            link, chart, stats, advice, tests, run)
        updateLiveViews()
    }

    private fun launchDiagnostic() {
        refreshConnection()
        val target = current
        if (demo || busy || !target.connected) return
        val last = lastRunMs
        if (last != null && SystemClock.elapsedRealtime() - last < 30_000) {
            Toast.makeText(this, "Wait 30 seconds between checks.", Toast.LENGTH_SHORT).show(); return
        }
        busy = true; lastRunMs = SystemClock.elapsedRealtime(); lastDiagnostics = null; checkNotice = ""
        val id = ++runId
        updateLiveViews()
        pendingDiagnostic = workers.submit {
            val result = try { WifiDiagnostics.run(target, monitor) } catch (e: Exception) {
                WifiDiagnostics.Result(target.routeKey.orEmpty(), System.currentTimeMillis(), "Checks could not complete",
                    emptyList(), 0, null, null, "Not tested", "Not tested", e.javaClass.simpleName)
            }
            handler.post {
                if (id != runId || isFinishing || isDestroyed) return@post
                busy = false
                refreshConnection()
                if (!demo && result.connectionKey == current.routeKey) lastDiagnostics = result
                else checkNotice = "Wi-Fi changed. Run checks again for the current connection."
                updateLiveViews()
            }
        }
        handler.postDelayed({
            if (busy && id == runId) {
                runId++; busy = false; pendingDiagnostic?.cancel(true)
                checkNotice = "Checks took too long. Review the connection and retry."
                updateLiveViews()
            }
        }, 20_000)
    }

    private fun renderSampleAnalysis() {
        val n = selected?.takeIf { it.demo } ?: samples().first()
        heading("Sample connection report")
        add(body, line("SAMPLE DATA · NOT A LIVE CONNECTION", 12f, amber, true), 7)
        val readings = listOf(-2, 0, -1, -4, -3, 0, 1, -1, 0, -2).mapIndexed { i, d ->
            ConnectionAnalytics.Sample(i * 2000L, n.level + d)
        }
        card(body, 16) {
            add(this, line(n.ssid, 22f, white, true))
            add(this, line("Fictional network and illustrative signal readings", 12f, amber), 8)
            val chart = SignalChartView(this@WifiExplorerActivity).apply { submit(readings) }
            addView(chart, LinearLayout.LayoutParams(-1, dp(190)).apply { topMargin = dp(14) })
            add(this, line(statisticsText(ConnectionAnalytics.summarise(readings)), 12f, muted), 12)
        }
        card(body) {
            add(this, line(WifiSecurityRules.classify(n.capabilities).label, 18f, white, true))
            add(this, line("This preview explains the interface only. DNS, HTTPS and connection checks have not been run. Sample data is not saved to collector evidence.", 13f, muted), 9)
        }
        add(body, button("Exit sample and analyse my Wi-Fi", true) { exitDemo(); showPage(1) }, 16)
    }

    private fun renderCommunity() {
        add(body, line("Community Reports", 23f, white, true), 18)
        add(body, line("Share experiences responsibly. No live crowd-rating service is connected in this version.", 13f, muted), 6)
        card(body, 16) {
            add(this, line("COMMUNITY TRUST", 11f, violet, true))
            add(this, line("Not yet available", 20f, white, true), 10)
            add(this, line("We do not fabricate crowd scores or label a hotspot malicious based on a single complaint. Reports require moderation, abuse resistance and an approved server before public aggregation.", 13f, muted), 8)
        }
        card(body, 12) {
            add(this, line("CREATE AN OPTIONAL LOCAL REPORT", 11f, violet, true))
            add(this, line("No SSID, BSSID, location, password or device ID is automatically included. Avoid entering personal or network identifiers in the note.", 12f, muted), 8)
            val categories = CrowdReports.allowedCategories
            val spin = Spinner(this@WifiExplorerActivity).apply {
                adapter = ArrayAdapter(this@WifiExplorerActivity,
                    android.R.layout.simple_spinner_dropdown_item, categories)
                background = bgDrawable(panelSoft)
            }
            add(this, spin, 11)
            val note = EditText(this@WifiExplorerActivity).apply {
                hint = "Optional short description (max 160 characters)"
                setHintTextColor(muted)
                setTextColor(white)
                textSize = 14f
                maxLines = 3
                background = bgDrawable(panelSoft)
                setPadding(dp(12), dp(13), dp(12), dp(13))
            }
            add(this, note, 12)
            add(this, button("Save report privately", true) {
                CrowdReports.add(this@WifiExplorerActivity, spin.selectedItem.toString(), note.text.toString())
                Toast.makeText(this@WifiExplorerActivity, "Saved locally. Not published.", Toast.LENGTH_SHORT).show()
                showPage(2)
            }, 11)
        }
        val drafts = CrowdReports.read(this)
        card(body, 12) {
            add(this, line("YOUR LOCAL DRAFTS  •  ${drafts.size}", 11f, violet, true))
            if (drafts.isEmpty()) add(this, line("No local reports saved. No public crowd reports are available.", 13f, muted), 10)
            drafts.take(10).forEach { draft ->
                separator(this)
                add(this, line(draft.category, 14f, white, true))
                if (draft.note.isNotBlank()) add(this, line(draft.note, 12f, muted), 6)
                add(this, button("Review and share via Android") { confirmShare(draft) }, 8)
            }
            if (drafts.isNotEmpty()) add(this, button("Delete all local drafts") {
                AlertDialog.Builder(this@WifiExplorerActivity)
                    .setTitle("Delete local reports?")
                    .setMessage("This removes all optional local community drafts from this device.")
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Delete") { _, _ -> CrowdReports.deleteAll(this@WifiExplorerActivity); showPage(2) }
                    .show()
            }, 14)
        }
    }

    private fun confirmShare(draft: CrowdReports.Draft) {
        AlertDialog.Builder(this)
            .setTitle("Share this community report?")
            .setMessage("This opens the Android share sheet. Review the report and choose the recipient yourself. No automatic upload occurs.\n\n${CrowdReports.shareText(draft)}")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Review share options") { _, _ ->
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, CrowdReports.shareText(draft))
                }
                startActivity(Intent.createChooser(intent, "Share optional report"))
            }.show()
    }

    @Deprecated("Legacy permission callback for the existing Android project")
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 300) {
            monitor.stop(); monitor.start(); refreshConnection(); showPage(page)
        }
    }
}
