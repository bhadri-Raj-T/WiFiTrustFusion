package com.bhadrirajt.wifitrustfusion.wifi.explorer

import org.junit.Assert.*
import org.junit.Test

class LiveSecurityPauseRuleTest {
    @Test fun threeCurrentOpenObservationsTriggerOnlyOnce() {
        val r=LiveSecurityPauseRule();r.arm("a",0)
        assertEquals(1,r.observe("a","Open Wi-Fi",true,null,2000).count)
        assertEquals(2,r.observe("a","Open Wi-Fi",true,null,4000).count)
        assertEquals(LivePauseRule.Kind.TRIGGER,r.observe("a","Open Wi-Fi",true,null,6000).kind)
        assertFalse(r.isArmed)
        assertEquals(LivePauseRule.Kind.IDLE,r.observe("a","Open Wi-Fi",true,null,8000).kind)
    }
    @Test fun oldOrUnknownAdvertisementDoesNotTrigger() {
        val r=LiveSecurityPauseRule();r.arm("a",0)
        for(t in listOf(2000L,4000L,6000L)) assertEquals(0,r.observe("a","Open Wi-Fi",false,31,t).count)
        assertEquals(0,r.observe("a","Open Wi-Fi",false,null,8000).count)
    }
    @Test fun recentMatchingWepObservationCanTrigger() {
        val r=LiveSecurityPauseRule();r.arm("a",0)
        r.observe("a","WEP (legacy)",false,10,2000);r.observe("a","WEP (legacy)",false,12,4000)
        assertEquals(LivePauseRule.Kind.TRIGGER,r.observe("a","WEP (legacy)",false,14,6000).kind)
    }
    @Test fun enhancedOpenIsNotClassifiedAsUnencryptedOpen() {
        val r=LiveSecurityPauseRule();r.arm("a",0)
        assertEquals(0,r.observe("a","Enhanced Open (OWE)",true,null,2000).count)
    }
    @Test fun connectionChangeAndBackgroundDisarmClearTheCounter() {
        val r=LiveSecurityPauseRule();r.arm("a",0);r.observe("a","Open Wi-Fi",true,null,2000)
        assertEquals(LivePauseRule.Kind.DISARMED,r.observe("b","Open Wi-Fi",true,null,4000).kind)
        r.arm("b",6000);r.disarm();assertFalse(r.isArmed)
    }
    @Test fun protectedConfigurationResetsPartialTrigger() {
        val r=LiveSecurityPauseRule();r.arm("a",0);r.observe("a","Open Wi-Fi",true,null,2000)
        assertEquals(0,r.observe("a","WPA2/WPA-Personal advertised",false,3,4000).count)
        assertEquals(1,r.observe("a","Open Wi-Fi",true,null,6000).count)
    }
    @Test fun fastRepeatedCallsAndLongGapsDoNotTrigger() {
        val r=LiveSecurityPauseRule();r.arm("a",0)
        assertEquals(0,r.observe("a","Open Wi-Fi",true,null,500).count)
        assertEquals(LivePauseRule.Kind.DISARMED,r.observe("a","Open Wi-Fi",true,null,6500).kind)
    }
    @Test fun negativeAgeIsNotFreshEvidence() {
        val r=LiveSecurityPauseRule();r.arm("a",0)
        assertEquals(0,r.observe("a","Open Wi-Fi",false,-1,2000).count)
    }
}
