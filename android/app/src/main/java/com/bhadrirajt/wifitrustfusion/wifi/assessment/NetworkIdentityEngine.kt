package com.bhadrirajt.wifitrustfusion.wifi.assessment

import com.bhadrirajt.wifitrustfusion.wifi.model.TimestampState
import com.bhadrirajt.wifitrustfusion.wifi.model.WifiObservation
import com.bhadrirajt.wifitrustfusion.wifi.model.ScanRequestRecord

/**
 * M4 development-only, session-local identity summary. Not an attack detector.
 * Never exposes BSSIDs or SSIDs in a returned result or UI message.
 */
class NetworkIdentityEngine {
    private data class Profile(val security: String, val band: String)
    private val observedProfiles = mutableMapOf<String, Profile>()

    data class Result(
        val qualifiedObservations: Int,
        val firstObservedHere: Int,
        val previouslyObservedHere: Int,
        val changedSecurity: Int,
        val changedBand: Int,
        val unknownIdentity: Int,
        val knownProfilesTotal: Int,
        val securityBreakdown: Map<String, Int>,
        val explanation: String
    )

    fun reset() {
        observedProfiles.clear()
    }

    fun evaluate(request: ScanRequestRecord, observations: List<WifiObservation>): Result {
        val allowed = request.broadcastReceived && request.resultsUpdated == true &&
            request.resultCount == observations.size
        var qualified = 0
        var first = 0
        var previous = 0
        var securityChanged = 0
        var bandChanged = 0
        var unknown = 0
        val breakdown = linkedMapOf<String, Int>()

        if (allowed) {
            for (o in observations) {
                val id = o.bssid?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }
                val timestampUsable = o.scanTimestampUs != null && o.scanTimestampUs > 0L &&
                    o.seenAfterRequest == true &&
                    o.timestampState in setOf(TimestampState.FIRST_SEEN, TimestampState.ADVANCED)
                if (id == null || !timestampUsable) {
                    unknown++
                    continue
                }
                qualified++
                val profile = Profile(securityClass(o.capabilities), bandClass(o.frequencyMhz))
                breakdown[profile.security] = (breakdown[profile.security] ?: 0) + 1
                val old = observedProfiles[id]
                if (old == null) {
                    first++
                } else {
                    previous++
                    // First-seen in this runtime alone is not proof of an AP change.
                    // Only compare when the timestamp ADVANCED and is after the request.
                    if (o.timestampState == TimestampState.ADVANCED) {
                        if (old.security != profile.security) securityChanged++
                        if (old.band != profile.band) bandChanged++
                    }
                }
                observedProfiles[id] = profile
            }
        } else {
            unknown = observations.size
        }
        val message = if (!allowed) {
            "Scan update or count not verified; baseline not updated."
        } else if (qualified == 0) {
            "No identity observations qualified for a session-local comparison."
        } else {
            "These are session-local changes, NOT evidence of an attack or a trusted network."
        }
        return Result(qualified, first, previous, securityChanged, bandChanged,
            unknown, observedProfiles.size, breakdown, message)
    }

    companion object {
        fun securityClass(capabilities: String): String {
            val value = capabilities.uppercase()
            return when {
                "SAE" in value -> "WPA3-SAE"
                "OWE" in value -> "OWE"
                "EAP" in value -> "Enterprise"
                "WPA2" in value || "RSN" in value -> "WPA2/RSN"
                "WPA" in value -> "WPA"
                "WEP" in value -> "WEP"
                value.isBlank() || "ESS" in value && "WPA" !in value -> "Open/unspecified"
                else -> "Unknown"
            }
        }

        fun bandClass(frequencyMhz: Int): String = when (frequencyMhz) {
            in 2400..2500 -> "2.4 GHz"
            in 4900..5925 -> "5 GHz"
            in 5926..7125 -> "6 GHz"
            else -> "Other"
        }
    }
}
