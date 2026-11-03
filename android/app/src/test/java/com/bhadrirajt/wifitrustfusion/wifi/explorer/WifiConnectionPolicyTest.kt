package com.bhadrirajt.wifitrustfusion.wifi.explorer

import org.junit.Assert.*
import org.junit.Test

class WifiConnectionPolicyTest {
    @Test fun olderPhoneCanReadDefaultWifiWithSeveralWifiNetworkRecords() {
        assertTrue(WifiConnectionPolicy.allowLegacyInfo(27, 2, true))
        assertTrue(WifiConnectionPolicy.allowLegacyInfo(28, 3, true))
    }
    @Test fun ambiguousLegacyWifiIsNotAttributedToAnUnrelatedNetwork() {
        assertFalse(WifiConnectionPolicy.allowLegacyInfo(27, 2, false))
        assertFalse(WifiConnectionPolicy.allowLegacyInfo(31, 2, true))
    }
    @Test fun singleWifiCanUseLegacyInformationOnSupportedVersions() {
        for (api in listOf(27, 28, 29, 31, 36)) assertTrue(WifiConnectionPolicy.allowLegacyInfo(api, 1, false))
    }
    @Test fun hiddenNamesDoNotBlockChecksOnTheSameWifiRoute() {
        assertTrue(WifiConnectionPolicy.sameConnection(101L, 101L))
    }
    @Test fun sameNameOnADifferentNetworkDoesNotPassTheRouteCheck() {
        assertFalse(WifiConnectionPolicy.sameConnection(101L, 102L, "Campus", "Campus"))
    }
    @Test fun knownAccessPointOrNameChangesInvalidateResults() {
        assertFalse(WifiConnectionPolicy.sameConnection(101L, 101L, "A", "B"))
        assertFalse(WifiConnectionPolicy.sameConnection(101L, 101L, "A", "A", "aa:bb:cc:dd:ee:01", "aa:bb:cc:dd:ee:02"))
    }
    @Test fun RevealingMetadataDoesNotInvalidateTheBoundWifiRoute() {
        assertTrue(WifiConnectionPolicy.sameConnection(101L, 101L, null, "Campus", null, "aa:bb:cc:dd:ee:01"))
    }
    @Test fun disconnectedStateCannotPassEvenWhenNamesMatch() {
        assertFalse(WifiConnectionPolicy.sameConnection(101L, null, "Campus", "Campus"))
        assertFalse(WifiConnectionPolicy.sameConnection(null, null))
    }
}
