package com.bhadrirajt.wifitrustfusion.wifi.explorer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WifiSecurityRulesTest {
    @Test fun openWifiIsNotCalledSafe() {
        val result = WifiSecurityRules.classify("[ESS]")
        assertEquals("Open Wi-Fi", result.label)
        assertTrue(result.caution.contains("sensitive"))
    }
    @Test fun wpa3AdvertisingIsNotProofOfLegitimacy() {
        val result = WifiSecurityRules.classify("[RSN-SAE-CCMP][ESS]")
        assertEquals("WPA3 advertised", result.label)
        assertTrue(result.caution.contains("not proof"))
    }
    @Test fun enterpriseAndWepAreDistinguished() {
        assertEquals("Enterprise authentication", WifiSecurityRules.classify("[RSN-EAP-CCMP][ESS]").label)
        assertEquals("WEP (legacy)", WifiSecurityRules.classify("[WEP][ESS]").label)
    }
    @Test fun bssidMasked() {
        assertEquals("xx:xx:xx:xx:44:55", WifiSecurityRules.maskedBssid("02:11:22:33:44:55"))
    }
    @Test fun bandsAndSignal() {
        assertEquals("6 GHz", WifiSecurityRules.band(5955))
        assertEquals("Weak", WifiSecurityRules.signalLabel(-85))
    }
}
