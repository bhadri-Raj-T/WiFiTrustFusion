package com.bhadrirajt.wifitrustfusion.wifi.explorer

import android.Manifest
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import java.text.DateFormat
import java.util.Date

/** Local teaching examples; real pause is explicit, one-shot and foreground-only. */
class SafetyDemoActivity : AppCompatActivity() {
    private data class Scenario(val name: String, val observation: String, val meaning: String,
        val action: String, val values: List<Int>)
    private val bg = Color.rgb(13, 18, 31)
    private val panel = Color.rgb(23, 32, 49)
    private val soft = Color.rgb(33, 45, 65)
    private val white = Color.rgb(246, 248, 255)
    private val muted = Color.rgb(175, 189, 209)
    private val teal = Color.rgb(117, 234, 213)
    private val amber = Color.rgb(250, 194, 100)
    private val handler = Handler(Looper.getMainLooper())
    private val rule = LivePauseRule()
    private val history = ConnectionAnalytics.SignalWindow()
    private lateinit var monitor: WifiConnectionMonitor
    private lateinit var wifi: WifiManager
    private lateinit var live: TextView
    private lateinit var ruleStatus: TextView
    private lateinit var actionStatus: TextView
    private lateinit var eventView: TextView
    private lateinit var liveChart: SignalChartView
    private lateinit var trainingChart: SignalChartView
    private lateinit var trainingResult: TextView
    private lateinit var scenarioChoice: Spinner
    private lateinit var thresholdChoice: Spinner
    private lateinit var armButton: Button
    private var foreground = false
    private var ruleMessage = "Disarmed. Arming applies only while this screen stays in the foreground."
    private var pendingOffUntilMs: Long? = null
    private var expectedOff = false
    private var simulation: Runnable? = null
    private var confirmation: AlertDialog? = null
    private val events = ArrayDeque<String>()
    private val thresholds = listOf(-40, -55, -65, -75, -85)
    private val scenarios = listOf(
        Scenario("Weak signal", "Example RSSI falls below -75 dBm.",
            "Distance, walls and interference can weaken reception. This does not establish that a hacker is present.",
            "Move closer or use a known connection. A live pause demonstration is available separately above.",
            listOf(-48, -52, -57, -63, -70, -77, -80, -82)),
        Scenario("Changing signal", "Example signal values vary by more than 12 dB.",
            "Movement and the radio environment can explain variation. This curve cannot identify an attack.",
            "Compare positions and observe a longer window before drawing a conclusion.",
            listOf(-45, -47, -63, -49, -71, -52, -65, -46)),
        Scenario("Open or legacy Wi-Fi", "Example network advertises Open Wi-Fi or WEP.",
            "Link protection is absent or obsolete in this invented example. It is not a hacker verdict.",
            "Prefer a known network with modern protection; consider pausing this phone's Wi-Fi.",
            listOf(-48, -48, -47, -49, -48, -47, -49, -48)),
        Scenario("Look-alike hotspot name", "Invented names: CampusGuest and CampusGuest_Free.",
            "A similar name can mislead a user. These names are not scan results or detected attackers.",
            "Confirm the exact network with its operator before entering credentials.",
            listOf(-44, -45, -44, -46, -45, -44, -45, -43)),
        Scenario("Same name, different access point", "Example SSID stays the same while its access-point identifier changes.",
            "A different AP can be normal roaming or a replacement router. No authenticated owner baseline is available.",
            "Verify with the operator; pause Wi-Fi if uncertain. This example does not detect an evil twin.",
            listOf(-64, -65, -63, -44, -45, -43, -44, -42)),
        Scenario("Captive sign-in page", "Example network requires a portal login.",
            "A portal is not itself evidence of phishing. Live analysis uses Android's portal flag where available.",
            "Use Android's sign-in screen and verify the operator and address before entering information.",
            listOf(-51, -50, -51, -49, -50, -50, -51, -49)),
        Scenario("DNS or HTTPS check incomplete", "Example DNS times out or HTTPS verification does not complete.",
            "A portal, filtering, timeout or endpoint problem may cause this. It does not prove DNS poisoning or interception.",
            "Retry on a known network. Do not bypass certificate warnings; inspect the real diagnostic result.",
            listOf(-45, -44, -46, -45, -44, -45, -46, -45)),
        Scenario("Unexpected connection loss", "Example Wi-Fi connection disappears.",
            "Range or router restart may explain loss. This prototype cannot identify deauthentication frames or their sender.",
            "Pause sensitive activity and reconnect to a known network. This demo sends no deauthentication traffic.",
            listOf(-51, -56, -65, -76, -84, -90, -95, -98))
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        monitor = WifiConnectionMonitor(applicationContext)
        wifi = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        buildScreen()
    }
    override fun onStart() { super.onStart(); monitor.start() }
    override fun onResume() {
        super.onResume(); foreground = true
        if (expectedOff && Build.VERSION.SDK_INT >= 29 && !isWifiOff()) {
            expectedOff = false
            actionStatus.text = "Wi-Fi is still on; no pause confirmed. Use Android's controls if you want to switch it off."
        }
        handler.removeCallbacks(poll); handler.post(poll)
    }
    override fun onPause() {
        foreground = false; handler.removeCallbacks(poll); stopSimulation()
        disarm("Disarmed: Safety demo is no longer in the foreground.")
        confirmation?.dismiss(); confirmation = null
        super.onPause()
    }
    override fun onStop() { monitor.stop(); super.onStop() }
    override fun onDestroy() { handler.removeCallbacksAndMessages(null); super.onDestroy() }

    private val poll = object : Runnable {
        override fun run() {
            if (!foreground) return
            val snapshot = monitor.snapshot()
            val now = SystemClock.elapsedRealtime()
            history.record(snapshot.key, now, snapshot.rssiDbm)
            liveChart.submit(history.samples())
            val off = isWifiOff()
            live.text = when {
                off -> "LIVE: Wi-Fi radio is off on this phone."
                !snapshot.connected -> snapshot.status
                else -> "LIVE: ${snapshot.ssid ?: "Network name unavailable"}\n" +
                    "Signal: ${snapshot.rssiDbm?.let { "$it dBm" } ?: "unavailable"}\n" +
                    "${snapshot.internetLabel}\n${snapshot.security}\n${snapshot.securitySource}"
            }
            if (expectedOff && off) {
                expectedOff = false; pendingOffUntilMs = null
                actionStatus.text = "OBSERVED: Wi-Fi radio is off. Mobile data may still carry traffic. This is not proof that personal data is secure."
                event("LIVE", "Observed Wi-Fi radio off on this phone.")
            } else if (pendingOffUntilMs?.let { now >= it } == true) {
                pendingOffUntilMs = null; expectedOff = false
                actionStatus.text = "Wi-Fi shutdown was not confirmed. Use Android's Wi-Fi controls to inspect its state."
                event("LIVE", "Shutdown request was not confirmed.")
            }
            val decision = rule.observe(snapshot.key, snapshot.rssiDbm, now)
            when (decision.kind) {
                LivePauseRule.Kind.IDLE -> Unit
                LivePauseRule.Kind.COUNTING -> ruleMessage = decision.message
                LivePauseRule.Kind.DISARMED -> { ruleMessage = decision.message; event("LIVE", decision.message) }
                LivePauseRule.Kind.TRIGGER -> {
                    ruleMessage = decision.message; event("LIVE", decision.message); requestPause(snapshot.key)
                }
            }
            showRuleState()
            if (foreground) handler.postDelayed(this, 2000)
        }
    }

    private fun dp(n: Int) = (resources.displayMetrics.density * n + 0.5f).toInt()
    private fun column() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    private fun label(value: String, size: Float = 14f, color: Int = white, bold: Boolean = false) = TextView(this).apply {
        text = value; textSize = size; setTextColor(color); setLineSpacing(dp(3).toFloat(), 1f)
        if (bold) setTypeface(typeface, Typeface.BOLD)
    }
    private fun shape(color: Int) = GradientDrawable().apply { setColor(color); cornerRadius = dp(16).toFloat() }
    private fun add(parent: LinearLayout, view: View, top: Int = 10, height: Int = -2) {
        parent.addView(view, LinearLayout.LayoutParams(-1, height).apply { topMargin = dp(top) })
    }
    private fun button(value: String, action: () -> Unit) = Button(this).apply {
        text = value; isAllCaps = false; textSize = 14f; setTextColor(white); minHeight = dp(48)
        background = RippleDrawable(ColorStateList.valueOf(Color.argb(50, 117, 234, 213)), shape(soft), null)
        setOnClickListener { action() }
    }
    private fun card(parent: LinearLayout, title: String, color: Int = teal, content: (LinearLayout) -> Unit) {
        val box = column().apply { setPadding(dp(16), dp(15), dp(16), dp(16)); background = shape(panel) }
        add(box, label(title, 13f, color, true), 0); content(box); add(parent, box, 14)
    }
    private fun spinner(labels: List<String>) = Spinner(this).apply {
        adapter = object : ArrayAdapter<String>(this@SafetyDemoActivity, android.R.layout.simple_spinner_item, labels) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View =
                super.getView(position, convertView, parent).apply { (this as? TextView)?.setTextColor(white) }
            override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View =
                super.getDropDownView(position, convertView, parent).apply {
                    (this as? TextView)?.apply { setTextColor(white); setPadding(dp(12), dp(14), dp(12), dp(14)) }
                }
        }.also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        minimumHeight = dp(48); setBackgroundColor(soft); setPopupBackgroundDrawable(shape(soft))
    }

    private fun buildScreen() {
        val root = column().apply { setBackgroundColor(bg) }
        WindowCompat.setDecorFitsSystemWindows(window, false); setContentView(root)
        WindowInsetsControllerCompat(window, root).apply {
            isAppearanceLightStatusBars = false; isAppearanceLightNavigationBars = false
        }
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom); insets
        }
        val header = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(dp(18), dp(8), dp(18), dp(8)) }
        header.addView(label("Safety demo", 23f, white, true), LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(button("Back") { finish() }, LinearLayout.LayoutParams(dp(70), dp(48)))
        root.addView(header)
        val scroll = ScrollView(this); root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        val body = column().apply { setPadding(dp(18), 0, dp(18), dp(28)) }; scroll.addView(body)
        add(body, label("LIVE measurements and SIMULATED teaching examples stay separate. This prototype does not detect hackers or certify a hotspot.", 14f, muted), 4)
        card(body, "LIVE CONNECTION - THIS PHONE") { box ->
            live = label("Reading the current Wi-Fi connection..."); add(box, live)
            liveChart = SignalChartView(this); add(box, liveChart, 12, dp(180))
            add(box, label("Foreground polls about every 2 seconds; Android may repeat cached radio values.", 12f, muted))
            add(box, button("Allow network details") {
                requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), 301)
            })
            add(box, button("Open Wi-Fi controls") { disarm("Disarmed: opened Wi-Fi controls."); openWifiControls() })
        }
        card(body, "LIVE SIGNAL-TRIGGERED PAUSE DEMO") { box ->
            add(box, label("Optional: after 3 consecutive polls below the chosen RSSI, request Wi-Fi off on this phone. Weak signal is not an attack indicator. The one-shot rule disarms on leaving this screen or changing connection.", 14f, muted))
            thresholdChoice = spinner(thresholds.map { if (it == -40) "Below -40 dBm (quick demo)" else "Below $it dBm" }).apply {
                setSelection(thresholds.indexOf(-75)); contentDescription = "Live signal pause threshold"
            }; add(box, thresholdChoice)
            ruleStatus = label(ruleMessage, 14f, muted); add(box, ruleStatus)
            armButton = button("Arm live pause rule") { if (rule.isArmed) disarm("Disarmed by the user.") else confirmArm() }
            add(box, armButton); add(box, button("Pause Wi-Fi on this phone now") { confirmManualPause() })
            actionStatus = label("No pause requested. This affects this phone only; mobile data, the router and other devices are outside this action.", 13f, muted)
            add(box, actionStatus)
            add(box, label(if (Build.VERSION.SDK_INT < 29)
                "This Android version permits a shutdown request. The app checks whether Wi-Fi actually turns off."
                else "Android 10+ requires you to switch Wi-Fi off in system controls. Automatic shutdown is restricted by Android.", 13f, amber))
        }
        card(body, "SIMULATED SECURITY SCENARIOS", amber) { box ->
            add(box, label("Invented examples, including the chart. No attack or real disconnection is performed by this panel. Playing an example disarms the live rule.", 14f, muted))
            scenarioChoice = spinner(scenarios.map { it.name }).apply { contentDescription = "Simulated Wi-Fi security scenario" }
            add(box, scenarioChoice); add(box, button("Play simulated example") { runScenario(scenarios[scenarioChoice.selectedItemPosition]) })
            trainingResult = label("SIMULATED: choose an example for its observation, limits and response.", 14f, amber)
            add(box, trainingResult); trainingChart = SignalChartView(this); add(box, trainingChart, 12, dp(160))
        }
        card(body, "SESSION EVENTS") { box ->
            eventView = label("No events yet.", 12f, muted); add(box, eventView)
            add(box, label("Events stay in memory. No passwords or personal files are accessed. Mobile-data traffic is not blocked.", 12f, muted))
        }
    }

    private fun confirmArm() {
        val target = monitor.snapshot()
        if (!target.connected || !ConnectionAnalytics.validRssi(target.rssiDbm)) {
            ruleMessage = "Connect to Wi-Fi and wait for a valid live RSSI before arming."; showRuleState(); return
        }
        val threshold = thresholds[thresholdChoice.selectedItemPosition]
        confirmation = AlertDialog.Builder(this).setTitle("Arm a real Wi-Fi pause demo?")
            .setMessage("Connection: ${target.ssid ?: "name unavailable"}\nBelow $threshold dBm for 3 polls.\n\nThis can interrupt this phone's Wi-Fi. Android 10+ requires you to finish in system controls. It does not detect an attacker, protect the router or disable mobile data.")
            .setNegativeButton("Cancel", null).setPositiveButton("Arm live rule") { _, _ ->
                val fresh = monitor.snapshot()
                if (foreground && fresh.key == target.key && fresh.connected && ConnectionAnalytics.validRssi(fresh.rssiDbm)) {
                    stopSimulation(); trainingResult.text = "SIMULATED: playback stopped while the live rule is armed."
                    if (rule.arm(fresh.key, threshold, SystemClock.elapsedRealtime())) {
                        ruleMessage = "Armed: waiting for 3 consecutive live polls below $threshold dBm."
                        event("LIVE", "User armed the foreground pause demo at $threshold dBm.")
                    }
                } else ruleMessage = "Connection changed. Check the current connection and arm again."
                showRuleState()
            }.show()
    }
    private fun confirmManualPause() {
        disarm("Disarmed for a manual pause confirmation.")
        val target = monitor.snapshot()
        if (!target.connected) { actionStatus.text = "No connected Wi-Fi route. Use Open Wi-Fi controls to inspect the radio."; return }
        confirmation = AlertDialog.Builder(this).setTitle("Pause Wi-Fi on this phone?")
            .setMessage("This interrupts this phone's Wi-Fi. On Android 10+, finish the change in system Wi-Fi controls. Mobile data may continue carrying traffic. No router setting or personal file is changed.")
            .setNegativeButton("Cancel", null).setPositiveButton("Continue") { _, _ -> stopSimulation(); requestPause(target.key) }.show()
    }
    @Suppress("DEPRECATION")
    private fun requestPause(expectedConnection: String?) {
        val current = monitor.snapshot()
        if (!foreground || expectedConnection == null || current.key != expectedConnection || !current.connected) {
            actionStatus.text = "Pause cancelled: the live connection or foreground screen changed."
            event("LIVE", "Pause cancelled because the connection or screen changed."); return
        }
        if (Build.VERSION.SDK_INT >= 29) {
            actionStatus.text = "Use Android's Wi-Fi controls to switch Wi-Fi off. The app checks its state when you return."
            expectedOff = true; event("LIVE", "System Wi-Fi controls requested; no automatic shutdown claimed.")
            openWifiControls(); return
        }
        val accepted = try { wifi.setWifiEnabled(false) } catch (_: Exception) { false }
        if (accepted) {
            expectedOff = true; pendingOffUntilMs = SystemClock.elapsedRealtime() + 8000
            actionStatus.text = "Shutdown requested. Waiting to observe Wi-Fi radio off. Mobile data is unaffected."
            event("LIVE", "Shutdown request accepted; radio state not yet confirmed.")
        } else {
            actionStatus.text = "Android refused the request. Use Wi-Fi controls to pause manually."
            event("LIVE", "Shutdown refused; no protection success claimed.")
        }
    }
    private fun openWifiControls() {
        val action = if (Build.VERSION.SDK_INT >= 29) Settings.Panel.ACTION_WIFI else Settings.ACTION_WIFI_SETTINGS
        try { startActivity(Intent(action)) }
        catch (_: Exception) {
            try { startActivity(Intent(Settings.ACTION_WIFI_SETTINGS)) }
            catch (_: Exception) { actionStatus.text = "Open the phone's Wi-Fi settings manually; the requested screen was unavailable." }
        }
    }
    private fun isWifiOff() = try { !wifi.isWifiEnabled } catch (_: Exception) { false }
    private fun disarm(reason: String) {
        val wasArmed = rule.isArmed; rule.disarm(); ruleMessage = reason
        if (wasArmed && ::eventView.isInitialized) event("LIVE", reason)
        if (::ruleStatus.isInitialized) showRuleState()
    }
    private fun showRuleState() {
        ruleStatus.text = ruleMessage; ruleStatus.setTextColor(if (rule.isArmed) amber else muted)
        armButton.text = if (rule.isArmed) "Disarm live pause rule" else "Arm live pause rule"
        thresholdChoice.isEnabled = !rule.isArmed
    }
    private fun event(source: String, message: String) {
        events.addFirst("${DateFormat.getTimeInstance(DateFormat.MEDIUM).format(Date())} | $source | $message")
        while (events.size > 8) events.removeLast()
        eventView.text = events.joinToString("\n\n")
    }
    private fun stopSimulation() { simulation?.let { handler.removeCallbacks(it) }; simulation = null }
    private fun runScenario(scenario: Scenario) {
        disarm("Disarmed: simulated examples cannot trigger the live pause action."); stopSimulation()
        trainingChart.submit(emptyList()); event("SIMULATED", "Started invented example: ${scenario.name}.")
        var count = 0
        val animation = object : Runnable {
            override fun run() {
                if (!foreground) return
                count++
                val samples = scenario.values.take(count).mapIndexed { i, value -> ConnectionAnalytics.Sample(i * 2000L, value) }
                trainingChart.submit(samples)
                trainingChart.contentDescription = "SIMULATED chart for ${scenario.name}; invented readings"
                val progress = if (count < scenario.values.size) "Invented readings: $count/${scenario.values.size}. No settings changed."
                    else "Example complete. No phone settings changed; use the LIVE panel for a real pause demo."
                trainingResult.text = "SIMULATED - ${scenario.name}\n\nObservation: ${scenario.observation}\n\nMeaning: ${scenario.meaning}\n\nResponse: ${scenario.action}\n\n$progress"
                if (count < scenario.values.size) handler.postDelayed(this, 1000)
                else { simulation = null; event("SIMULATED", "Example finished; no real Wi-Fi action performed.") }
            }
        }
        simulation = animation; handler.post(animation)
    }
}
