package com.bhadrirajt.wifitrustfusion.wifi.explorer

/** Routing decisions do not require Android to expose location-sensitive Wi-Fi names. */
internal object WifiConnectionPolicy {
    fun allowLegacyInfo(api: Int, wifiNetworks: Int, selectedIsDefault: Boolean): Boolean =
        wifiNetworks == 1 || (api < 29 && selectedIsDefault)

    fun sameConnection(
        previousNetwork: Long?, currentNetwork: Long?,
        previousSsid: String? = null, currentSsid: String? = null,
        previousBssid: String? = null, currentBssid: String? = null
    ): Boolean {
        if (previousNetwork == null || currentNetwork == null || previousNetwork != currentNetwork) return false
        if (previousSsid != null && currentSsid != null && previousSsid != currentSsid) return false
        if (previousBssid != null && currentBssid != null && !previousBssid.equals(currentBssid, true)) return false
        return true
    }
}
