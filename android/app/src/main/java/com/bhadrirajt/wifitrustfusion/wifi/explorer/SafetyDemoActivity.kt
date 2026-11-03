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
import android.widget.AdapterView
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
    private val bg = Color.rgb(13, 18, 31)
    private val panel = Color.rgb(23, 32, 49)
    private val soft = Color.rgb(33, 45, 65)
    private val white = Color.rgb(246, 248, 255)
    private val muted = Color.rgb(175, 189, 209)
    private val teal = Color.rgb(117, 234, 213)
    private val amber = Color.rgb(250, 194, 100)
    private val handler = Handler(Looper.getMainLooper())
    private val rule = LivePauseRule()
    private val configRule = LiveSecurityPauseRule()
    private lateinit var policyChoice: Spinner
    private fun isArmed() = rule.isArmed || configRule.isArmed
    private val history = ConnectionAnalytics.SignalWindow()
    private lateinit var monitor: WifiConnectionMonitor
    private lateinit var wifi: WifiManager
    private lateinit var live: TextView
    private lateinit var ruleStatus: TextView
    private lateinit var actionStatus: TextView
    private lateinit var eventView: TextView
    private lateinit var liveChart: SignalChartView
    private lateinit var thresholdChoice: Spinner
    private lateinit var armButton: Button
    private var foreground = false
    private var ruleMessage = "Disarmed. Arming applies only while this screen stays in the foreground."
    private var pendingOffUntilMs: Long? = null
    private var expectedOff = false
    private var confirmation: AlertDialog? = null
    private val events = ArrayDeque<String>()
    private val thresholds = listOf(-40, -55, -65, -75, -85)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        monitor = WifiConnectionMonitor(applicationContext)
        wifi = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        buildScreen()
        policyChoice.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) { updatePolicyControls() }
            override fun onNothingSelected(parent: AdapterView<*>?) { }
        }
        updatePolicyControls()
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
        foreground = false; handler.removeCallbacks(poll)
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
            val decision = if (configRule.isArmed) configRule.observe(snapshot.key, snapshot.security,
                snapshot.securityFromCurrentConnection, snapshot.securityAgeSeconds, now)
                else rule.observe(snapshot.key, snapshot.rssiDbm, now)
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
        header.addView(label("Live response", 23f, white, true), LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(button("Back") { finish() }, LinearLayout.LayoutParams(dp(70), dp(48)))
        root.addView(header)
        val scroll = ScrollView(this); root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        val body = column().apply { setPadding(dp(18), 0, dp(18), dp(28)) }; scroll.addView(body)
        add(body, label("These controls use the phone's actual connection. Nothing is armed by default. They do not identify hackers or guarantee personal-data protection.", 14f, muted), 4)
        card(body, "LIVE CONNECTION - THIS PHONE") { box ->
            live = label("Reading the current Wi-Fi connection..."); add(box, live)
            liveChart = SignalChartView(this); add(box, liveChart, 12, dp(180))
            add(box, label("Foreground polls about every 2 seconds; Android may repeat cached radio values.", 12f, muted))
            add(box, button("Allow network details") {
                requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), 301)
            })
            add(box, button("Open Wi-Fi controls") { disarm("Disarmed: opened Wi-Fi controls."); openWifiControls() })
        }
        card(body, "ARM AN OPTIONAL LIVE RESPONSE") { box ->
            add(box, label("Choose a condition, then explicitly arm it. After 3 matching foreground polls, request Wi-Fi off once. Open/WEP uses Android current security or a matching-AP advertisement no older than 30s. Cached observations may repeat. Unknown metadata does not trigger. Signal is a demo condition, not hacker evidence.", 14f, muted))
            policyChoice = spinner(listOf("Open/WEP configuration", "Low signal (demonstration)")).apply { contentDescription = "Real Wi-Fi pause condition" }
            add(box, policyChoice)
            add(box, label("Signal threshold below applies only to the low-signal profile.", 12f, muted))
            thresholdChoice = spinner(thresholds.map { if (it == -40) "Below -40 dBm (quick demo)" else "Below $it dBm" }).apply {
                setSelection(thresholds.indexOf(-75)); contentDescription = "Live signal pause threshold"
            }; add(box, thresholdChoice)
            ruleStatus = label(ruleMessage, 14f, muted); add(box, ruleStatus)
            armButton = button("Arm live pause rule") { if (isArmed()) disarm("Disarmed by the user.") else confirmArm() }
            add(box, armButton); add(box, button("Pause Wi-Fi on this phone now") { confirmManualPause() })
            actionStatus = label("No pause requested. This affects this phone only; mobile data, the router and other devices are outside this action.", 13f, muted)
            add(box, actionStatus)
            add(box, label(if (Build.VERSION.SDK_INT < 29)
                "This Android version permits a shutdown request. The app checks whether Wi-Fi actually turns off."
                else "Android 10+ requires you to switch Wi-Fi off in system controls. Automatic shutdown is restricted by Android.", 13f, amber))
        }
        card(body, "TEACHING CASES ARE IN THE DEMO TAB", amber) { box ->
            add(box, label("The 20-case failure simulator cannot change the real phone's Wi-Fi. Leave this screen to return to the Demo tab; both live rules disarm automatically.", 13f, muted))
            add(box, button("Back to the app") { finish() })
        }
        card(body, "SESSION EVENTS") { box ->
            eventView = label("No events yet.", 12f, muted); add(box, eventView)
            add(box, label("Events stay in memory. No passwords or personal files are accessed. Mobile-data traffic is not blocked.", 12f, muted))
        }
    }

    private fun confirmArm() {
        val target = monitor.snapshot()
        val configuration = policyChoice.selectedItemPosition == 0
        if (!target.connected || (!configuration && !ConnectionAnalytics.validRssi(target.rssiDbm))) {
            ruleMessage = "Connect to Wi-Fi first. Signal mode also needs a valid RSSI."; showRuleState(); return
        }
        val threshold = thresholds[thresholdChoice.selectedItemPosition]
        val condition = if (configuration) "Current/recent Open or WEP configuration for 3 polls" else "Below $threshold dBm for 3 polls"
        confirmation = AlertDialog.Builder(this).setTitle("Arm a real Wi-Fi pause response?")
            .setMessage("Connection: ${target.ssid ?: "name unavailable"}\n$condition.\n\nThis can interrupt this phone's Wi-Fi. The configuration profile needs current Android security or a matching-AP advertisement <=30s old. Unknown evidence does not trigger. Android 10+ requires you to finish in system controls. Mobile data and the router are unaffected.")
            .setNegativeButton("Cancel", null).setPositiveButton("Arm live response") { _, _ ->
                val fresh = monitor.snapshot()
                if (foreground && fresh.key == target.key && fresh.connected) {
                    val armed = if (configuration) configRule.arm(fresh.key, SystemClock.elapsedRealtime())
                        else if (ConnectionAnalytics.validRssi(fresh.rssiDbm)) rule.arm(fresh.key, threshold, SystemClock.elapsedRealtime()) else false
                    ruleMessage = if (armed) "Armed: $condition. Waiting for live observations." else "No valid live input; not armed."
                    if (armed) event("LIVE", "User armed: $condition.")
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
            .setNegativeButton("Cancel", null).setPositiveButton("Continue") { _, _ -> requestPause(target.key) }.show()
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
        val wasArmed = isArmed(); rule.disarm(); configRule.disarm(); ruleMessage = reason
        if (wasArmed && ::eventView.isInitialized) event("LIVE", reason)
        if (::ruleStatus.isInitialized) showRuleState()
    }
    private fun updatePolicyControls() {
        thresholdChoice.visibility = if (policyChoice.selectedItemPosition == 1) View.VISIBLE else View.GONE
    }
    private fun showRuleState() {
        ruleStatus.text = ruleMessage; ruleStatus.setTextColor(if (isArmed()) amber else muted)
        armButton.text = if (isArmed()) "Disarm live response" else "Arm live response"
        thresholdChoice.isEnabled = !isArmed()
        policyChoice.isEnabled = !isArmed()
        updatePolicyControls()
    }
    private fun event(source: String, message: String) {
        events.addFirst("${DateFormat.getTimeInstance(DateFormat.MEDIUM).format(Date())} | $source | $message")
        while (events.size > 8) events.removeLast()
        eventView.text = events.joinToString("\n\n")
    }
}
