package com.bhadrirajt.wifitrustfusion.wifi.explorer

/** Foreground demo rule. Only live monitor readings enter observe; RSSI is not attack evidence. */
internal class LivePauseRule {
    enum class Kind { IDLE, COUNTING, TRIGGER, DISARMED }
    data class Decision(val kind: Kind, val count: Int, val message: String)
    var isArmed = false
        private set
    var thresholdDbm = -75
        private set
    var consecutive = 0
        private set
    private var connectionKey: String? = null
    private var lastPollMs = 0L
    fun arm(key: String?, threshold: Int, nowMs: Long): Boolean {
        require(threshold in -95..-35)
        disarm()
        if (key.isNullOrBlank()) return false
        connectionKey = key; thresholdDbm = threshold; lastPollMs = nowMs; isArmed = true
        return true
    }
    fun disarm() { isArmed = false; connectionKey = null; consecutive = 0 }
    fun observe(key: String?, rssiDbm: Int?, nowMs: Long): Decision {
        if (!isArmed) return Decision(Kind.IDLE, 0, "Live pause rule is disarmed.")
        if (key == null || key != connectionKey) {
            disarm(); return Decision(Kind.DISARMED, 0, "Disarmed: Wi-Fi connection changed or was lost.")
        }
        val gap = nowMs - lastPollMs
        if (gap < 0 || gap > 6000) {
            disarm(); return Decision(Kind.DISARMED, 0, "Disarmed: observation timing changed or a long gap occurred.")
        }
        if (gap < 1500) return Decision(Kind.COUNTING, consecutive, "Waiting for the next live poll.")
        lastPollMs = nowMs
        consecutive = if (ConnectionAnalytics.validRssi(rssiDbm) && rssiDbm!! < thresholdDbm) consecutive + 1 else 0
        if (consecutive >= 3) {
            val reached = consecutive; disarm()
            return Decision(Kind.TRIGGER, reached, "Three consecutive live polls were below $thresholdDbm dBm. One-shot pause requested.")
        }
        return Decision(Kind.COUNTING, consecutive,
            "$consecutive/3 consecutive polls below $thresholdDbm dBm. Signal alone does not identify an attack.")
    }
}
