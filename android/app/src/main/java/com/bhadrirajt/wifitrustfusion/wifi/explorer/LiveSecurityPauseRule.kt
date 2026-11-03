package com.bhadrirajt.wifitrustfusion.wifi.explorer

/** Explicitly armed configuration preference; not an attacker/vulnerability classifier. */
internal class LiveSecurityPauseRule {
    var isArmed = false
        private set
    private var key: String? = null
    private var lastMs = 0L
    private var count = 0
    fun arm(connectionKey: String?, nowMs: Long): Boolean {
        disarm()
        if (connectionKey.isNullOrBlank()) return false
        key=connectionKey; lastMs=nowMs; isArmed=true
        return true
    }
    fun disarm() { isArmed=false; key=null; count=0 }
    fun observe(connectionKey: String?, security: String, fromCurrentConnection: Boolean,
        advertisementAgeSeconds: Long?, nowMs: Long): LivePauseRule.Decision {
        fun result(kind:LivePauseRule.Kind, message:String) = LivePauseRule.Decision(kind,count,message)
        if(!isArmed) return result(LivePauseRule.Kind.IDLE,"Configuration response disarmed.")
        if(connectionKey==null || connectionKey!=key) {
            disarm(); return result(LivePauseRule.Kind.DISARMED,"Disarmed: the Wi-Fi connection changed or was lost.")
        }
        val gap=nowMs-lastMs
        if(gap<0 || gap>6000) { disarm(); return result(LivePauseRule.Kind.DISARMED,"Disarmed: a timing gap occurred.") }
        if(gap<1500) return result(LivePauseRule.Kind.COUNTING,"Waiting for the next live configuration poll.")
        lastMs=nowMs
        val fresh=fromCurrentConnection || (advertisementAgeSeconds!=null && advertisementAgeSeconds in 0L..30L)
        val condition=security in listOf("Open Wi-Fi","WEP (legacy)") && fresh
        count=if(condition) count+1 else 0
        if(count>=3) {
            disarm()
            return LivePauseRule.Decision(LivePauseRule.Kind.TRIGGER,3,
                "Three live polls reported a current/recent Open or WEP configuration. Your armed preference requested a one-shot pause.")
        }
        return result(LivePauseRule.Kind.COUNTING,
            if(!fresh) "Waiting: no current or <=30s matching-AP security observation. Unknown does not trigger a pause."
            else "$count/3 live polls match Open/WEP. This is a configuration preference, not evidence of a hacker.")
    }
}
