package com.bhadrirajt.wifitrustfusion.wifi.explorer

/** Descriptive parsing of advertised AP capabilities, NOT an attack classifier. */
internal object WifiSecurityRules {
    data class SecurityInfo(
        val label: String,
        val description: String,
        val caution: String
    )

    fun classify(capabilities: String): SecurityInfo {
        val c = capabilities.uppercase()
        return when {
            c.contains("WEP") -> SecurityInfo(
                "WEP (legacy)",
                "The access point advertises obsolete WEP link security.",
                "Avoid sending sensitive information. Prefer a modern protected network."
            )
            c.contains("SAE") || c.contains("WPA3") -> SecurityInfo(
                "WPA3 advertised",
                "This access point advertises WPA3 capabilities. The negotiated connection has not been verified.",
                "A modern security setting is not proof that the hotspot is legitimate."
            )
            c.contains("EAP") || c.contains("802.1X") -> SecurityInfo(
                "Enterprise authentication",
                "The access point advertises enterprise authentication.",
                "Confirm your organization's official network name and trusted authentication configuration."
            )
            c.contains("PSK") || c.contains("WPA2") -> SecurityInfo(
                "WPA2/WPA-Personal advertised",
                "A password-protected Wi-Fi link is advertised. Actual connection security has not been verified.",
                "Check the official network identity before entering credentials."
            )
            c.contains("OWE") -> SecurityInfo(
                "Enhanced Open (OWE)",
                "Opportunistic encryption is advertised without a shared Wi-Fi password.",
                "Encryption alone does not authenticate the operator of this hotspot."
            )
            c.contains("WAPI") -> SecurityInfo(
                "WAPI advertised",
                "This access point advertises WAPI network security.",
                "Verify compatibility and the hotspot operator independently."
            )
            c.contains("ESS") && !c.contains("WPA") && !c.contains("RSN") -> SecurityInfo(
                "Open Wi-Fi",
                "No WPA2/WPA3 link protection is advertised in this scan result.",
                "Avoid sensitive activity unless you trust the operator and use end-to-end encryption."
            )
            else -> SecurityInfo(
                "Unclassified",
                "The advertised security configuration could not be reliably classified.",
                "Do not assume the network is safe or unsafe from this scan alone."
            )
        }
    }

    fun band(mhz: Int): String = when (mhz) {
        in 2400..2500 -> "2.4 GHz"
        in 4900..5899 -> "5 GHz"
        in 5925..7125 -> "6 GHz"
        else -> "Other/unknown band"
    }

    fun signalLabel(dbm: Int): String = when {
        dbm >= -55 -> "Strong"
        dbm >= -70 -> "Good"
        dbm >= -82 -> "Fair"
        else -> "Weak"
    }

    fun maskedBssid(bssid: String): String {
        val parts = bssid.split(":")
        return if (parts.size == 6) "xx:xx:xx:xx:${parts[4]}:${parts[5]}" else "Hidden"
    }
}
