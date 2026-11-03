package com.bhadrirajt.wifitrustfusion.wifi.explorer

import org.junit.Assert.*
import org.junit.Test

class ConnectionAnalyticsTest {
    @Test fun summaryUsesValidReadingsAndCorrectEvenMedian() {
        val s = ConnectionAnalytics.summarise(listOf(
            ConnectionAnalytics.Sample(0, -80), ConnectionAnalytics.Sample(2000, -60),
            ConnectionAnalytics.Sample(4000, -70), ConnectionAnalytics.Sample(6000, -50),
            ConnectionAnalytics.Sample(8000, -127), ConnectionAnalytics.Sample(10000, 0)))
        assertEquals(4, s.count)
        assertEquals(6L, s.durationSeconds)
        assertEquals(-65.0, s.medianDbm!!, 0.001)
        assertEquals(30, s.rangeDb)
        assertEquals(11.1803, s.standardDeviationDb!!, 0.001)
    }

    @Test fun unavailableSignalDoesNotBecomeAWeakMeasurement() {
        val s = ConnectionAnalytics.summarise(listOf(ConnectionAnalytics.Sample(0, -127)))
        assertEquals(0, s.count)
        assertNull(s.medianDbm)
        assertNull(s.rangeDb)
    }

    @Test fun switchingAccessPointStartsANewWindowEvenForSameNetworkName() {
        val window = ConnectionAnalytics.SignalWindow()
        window.record("handle|Campus|ap1", 0, -50)
        window.record("handle|Campus|ap1", 2000, -55)
        window.record("handle|Campus|ap2", 4000, -80)
        assertEquals(listOf(-80), window.samples().map { it.rssiDbm })
    }

    @Test fun disconnectClearsHistory() {
        val window = ConnectionAnalytics.SignalWindow()
        window.record("wifi", 0, -60)
        window.record(null, 2000, null)
        assertTrue(window.samples().isEmpty())
        assertNull(window.connectionKey)
    }

    @Test fun backgroundGapDoesNotCreateAMisleadingContinuousTrend() {
        val window = ConnectionAnalytics.SignalWindow()
        window.record("wifi", 0, -60)
        window.record("wifi", 2000, -61)
        window.record("wifi", 12000, -75)
        assertEquals(1, window.samples().size)
        assertEquals(-75, window.samples().single().rssiDbm)
    }

    @Test fun rapidRefreshesDoNotInflateSampleCountsAndWindowIsBounded() {
        val window = ConnectionAnalytics.SignalWindow(3)
        window.record("wifi", 0, -60)
        window.record("wifi", 300, -61)
        window.record("wifi", 2000, -62)
        window.record("wifi", 4000, -63)
        window.record("wifi", 6000, -64)
        assertEquals(listOf(-62, -63, -64), window.samples().map { it.rssiDbm })
    }

    @Test fun redactedIdentityIsNotTreatedAsRealIdentity() {
        assertNull(ConnectionAnalytics.cleanSsid("\"<unknown ssid>\""))
        assertNull(ConnectionAnalytics.cleanBssid("02:00:00:00:00:00"))
        assertNull(ConnectionAnalytics.cleanBssid("not-an-address"))
        assertEquals("Campus", ConnectionAnalytics.cleanSsid("\"Campus\""))
        assertEquals("aa:bb:cc:dd:ee:ff", ConnectionAnalytics.cleanBssid("AA:BB:CC:DD:EE:FF"))
    }

    @Test fun repeatedValuesStillRepresentPolledReadings() {
        val s = ConnectionAnalytics.summarise((0..4).map { ConnectionAnalytics.Sample(it * 2000L, -60) })
        assertEquals(5, s.count)
        assertEquals(0.0, s.standardDeviationDb!!, 0.0)
    }

    @Test fun portalAndFailedChecksGenerateActionableAdviceWithoutAttackLabels() {
        val s = ConnectionAnalytics.summarise(emptyList())
        val advice = ConnectionAnalytics.recommendations(true, true, -82, s,
            true, false, "Open Wi-Fi", false, false)
        assertTrue(advice.any { it.title == "Complete the network sign-in" })
        assertTrue(advice.any { it.title == "Try a closer position" })
        assertTrue(advice.any { it.title == "Name lookup did not complete" })
        assertFalse(advice.any { it.title.contains("attack", true) || it.title.contains("safe", true) })
    }

    @Test fun disconnectedStateDoesNotReusePreviouslySuccessfulChecks() {
        val advice = ConnectionAnalytics.recommendations(false, false, null,
            ConnectionAnalytics.summarise(emptyList()), false, false, "Unavailable", true, true)
        assertEquals(1, advice.size)
        assertEquals("Connect to Wi-Fi", advice.single().title)
    }
}
