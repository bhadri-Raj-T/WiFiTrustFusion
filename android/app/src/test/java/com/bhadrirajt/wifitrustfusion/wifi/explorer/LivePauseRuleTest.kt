package com.bhadrirajt.wifitrustfusion.wifi.explorer

import org.junit.Assert.*
import org.junit.Test

class LivePauseRuleTest {
    @Test fun unarmedRuleNeverTriggers() {
        assertEquals(LivePauseRule.Kind.IDLE, LivePauseRule().observe("wifi-a", -90, 2000).kind)
    }
    @Test fun threeSpacedWeakPollsTriggerOnce() {
        val r = LivePauseRule(); assertTrue(r.arm("wifi-a", -75, 0))
        assertEquals(1, r.observe("wifi-a", -80, 2000).count)
        assertEquals(2, r.observe("wifi-a", -81, 4000).count)
        assertEquals(LivePauseRule.Kind.TRIGGER, r.observe("wifi-a", -82, 6000).kind)
        assertFalse(r.isArmed)
        assertEquals(LivePauseRule.Kind.IDLE, r.observe("wifi-a", -90, 8000).kind)
    }
    @Test fun strongOrEqualPollResetsTheCounter() {
        val r = LivePauseRule(); r.arm("wifi-a", -75, 0); r.observe("wifi-a", -80, 2000)
        assertEquals(0, r.observe("wifi-a", -75, 4000).count)
        assertEquals(1, r.observe("wifi-a", -80, 6000).count)
    }
    @Test fun invalidRssiResetsTheCounter() {
        val r = LivePauseRule(); r.arm("wifi-a", -75, 0); r.observe("wifi-a", -80, 2000)
        assertEquals(0, r.observe("wifi-a", -127, 4000).count)
        assertEquals(0, r.observe("wifi-a", null, 6000).count)
    }
    @Test fun connectionChangeDisarmsRatherThanPausingAnotherNetwork() {
        val r = LivePauseRule(); r.arm("wifi-a", -75, 0); r.observe("wifi-a", -80, 2000)
        assertEquals(LivePauseRule.Kind.DISARMED, r.observe("wifi-b", -90, 4000).kind); assertFalse(r.isArmed)
    }
    @Test fun disconnectionDisarms() {
        val r = LivePauseRule(); r.arm("wifi-a", -75, 0)
        assertEquals(LivePauseRule.Kind.DISARMED, r.observe(null, -90, 2000).kind)
    }
    @Test fun tooFrequentCallsDoNotCountAsNewPolls() {
        val r = LivePauseRule(); r.arm("wifi-a", -75, 0)
        assertEquals(0, r.observe("wifi-a", -90, 500).count)
        assertEquals(1, r.observe("wifi-a", -90, 2000).count)
        assertEquals(1, r.observe("wifi-a", -90, 2500).count)
    }
    @Test fun longGapOrBackwardsClockDisarms() {
        val r = LivePauseRule(); r.arm("wifi-a", -75, 0)
        assertEquals(LivePauseRule.Kind.DISARMED, r.observe("wifi-a", -90, 6001).kind)
        r.arm("wifi-a", -75, 9000)
        assertEquals(LivePauseRule.Kind.DISARMED, r.observe("wifi-a", -90, 8000).kind)
    }
    @Test fun explicitDisarmClearsPartialTrigger() {
        val r = LivePauseRule(); r.arm("wifi-a", -75, 0); r.observe("wifi-a", -90, 2000); r.disarm()
        assertEquals(0, r.consecutive); assertEquals(LivePauseRule.Kind.IDLE, r.observe("wifi-a", -90, 4000).kind)
    }
    @Test fun missingConnectionCannotBeArmed() { assertFalse(LivePauseRule().arm(null, -75, 0)) }
}
