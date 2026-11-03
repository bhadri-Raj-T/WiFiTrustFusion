package com.bhadrirajt.wifitrustfusion.wifi.assessment

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bhadrirajt.wifitrustfusion.wifi.model.ScanCompletionReason
import com.bhadrirajt.wifitrustfusion.wifi.model.ScanRequestRecord
import com.bhadrirajt.wifitrustfusion.wifi.model.TimestampState
import com.bhadrirajt.wifitrustfusion.wifi.model.WifiObservation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class M4M5AssessmentTest {
    private fun request(updated: Boolean? = true, count: Int = 1,
        broadcast: Boolean = true, start: Boolean? = true) = ScanRequestRecord(
        sessionId = "SYNTHETIC_TEST", requestId = "REQ_1",
        requestUtcIso = "2026-01-01T00:00:00Z", requestElapsedUs = 1_000_000L,
        requestedIntervalMs = 35_000L, startScanReturned = start,
        broadcastReceived = broadcast, resultsUpdated = updated, resultCount = count,
        completionReason = ScanCompletionReason.BROADCAST_RESULTS_UPDATED,
        completedUtcIso = "2026-01-01T00:00:01Z", completedElapsedUs = 1_200_000L
    )

    private fun observation(state: TimestampState = TimestampState.FIRST_SEEN,
        security: String = "[WPA2-PSK-CCMP][ESS]", bssid: String? = "02:00:00:00:00:01",
        after: Boolean? = true, age: Long? = 500L) = WifiObservation(
        observationId = "SYNTHETIC_1", sessionId = "SYNTHETIC_TEST", requestId = "REQ_1",
        observedUtcIso = "2026-01-01T00:00:01Z", observedElapsedUs = 1_300_000L,
        ssid = null, bssid = bssid, rssiDbm = -52, frequencyMhz = 2412,
        channelNumber = 1, channelWidth = null, capabilities = security,
        scanTimestampUs = 1_000_500L, scanAgeMs = age,
        timestampState = state, seenAfterRequest = after
    )

    @Test fun baselineIsOnlySessionLocal() {
        val engine = NetworkIdentityEngine()
        assertEquals(1, engine.evaluate(request(), listOf(observation())).firstObservedHere)
        engine.reset()
        assertEquals(1, engine.evaluate(request(), listOf(observation())).firstObservedHere)
    }

    @Test fun securityChangeRequiresAdvancingTimestamp() {
        val engine = NetworkIdentityEngine()
        engine.evaluate(request(), listOf(observation()))
        val result = engine.evaluate(request(), listOf(observation(
            TimestampState.ADVANCED, "[ESS]")))
        assertEquals(1, result.changedSecurity)
        assertEquals(1, result.previouslyObservedHere)
    }

    @Test fun repeatedTimestampDoesNotTriggerChange() {
        val engine = NetworkIdentityEngine()
        engine.evaluate(request(), listOf(observation()))
        assertEquals(0, engine.evaluate(request(), listOf(observation(
            TimestampState.REPEATED, "[ESS]"))).changedSecurity)
    }

    @Test fun updateFalseDoesNotSeedIdentityBaseline() {
        val engine = NetworkIdentityEngine()
        val refused = engine.evaluate(request(updated = false), listOf(observation()))
        assertEquals(0, refused.knownProfilesTotal)
        assertEquals(1, engine.evaluate(request(), listOf(observation())).firstObservedHere)
    }

    @Test fun nullBssidIsNotProfiled() {
        val r = NetworkIdentityEngine().evaluate(request(), listOf(observation(bssid = null)))
        assertEquals(0, r.knownProfilesTotal)
        assertEquals(1, r.unknownIdentity)
    }

    @Test fun securityAndBandClassification() {
        assertEquals("WPA3-SAE", NetworkIdentityEngine.securityClass("[RSN-SAE-CCMP]"))
        assertEquals("WEP", NetworkIdentityEngine.securityClass("[WEP][ESS]"))
        assertEquals("6 GHz", NetworkIdentityEngine.bandClass(5975))
    }

    @Test fun zeroObservationsYieldNoEvidence() {
        val q = EvidenceQualityEngine().evaluate(request(count = 0), emptyList())
        assertEquals(EvidenceQualityEngine.Level.NO_EVIDENCE, q.level)
    }

    @Test fun firstSeenIsOnlyProvisional() {
        val q = EvidenceQualityEngine().evaluate(request(), listOf(observation()))
        assertEquals(EvidenceQualityEngine.Level.PROVISIONAL, q.level)
    }

    @Test fun advancedFreshIsUsable() {
        val q = EvidenceQualityEngine().evaluate(request(), listOf(observation(
            TimestampState.ADVANCED)))
        assertEquals(EvidenceQualityEngine.Level.USABLE, q.level)
    }

    @Test fun nonUpdatedScanIsLowQuality() {
        val q = EvidenceQualityEngine().evaluate(request(updated = false),
            listOf(observation(TimestampState.ADVANCED)))
        assertEquals(EvidenceQualityEngine.Level.LOW, q.level)
    }

    @Test fun countMismatchDoesNotQualify() {
        val q = EvidenceQualityEngine().evaluate(request(count = 3),
            listOf(observation(TimestampState.ADVANCED)))
        assertEquals(EvidenceQualityEngine.Level.LOW, q.level)
    }

    @Test fun agedEvidenceCannotBeUsable() {
        val q = EvidenceQualityEngine().evaluate(request(), listOf(observation(
            TimestampState.ADVANCED, age = 20_000L)))
        assertEquals(EvidenceQualityEngine.Level.LOW, q.level)
        assertEquals(1, q.aged)
    }

    @Test fun missingTimestampIsNotUsable() {
        val q = EvidenceQualityEngine().evaluate(request(), listOf(observation(
            TimestampState.UNTRACKABLE, after = null)))
        assertEquals(EvidenceQualityEngine.Level.LOW, q.level)
        assertEquals(1, q.untrackable)
    }

    @Test fun syntheticDemoIsLabeledAndShowsDifference() {
        val (id, q) = AssessmentDemo.sample()
        assertEquals(1, id.changedSecurity)
        assertEquals(EvidenceQualityEngine.Level.USABLE, q.level)
        assertTrue(id.explanation.contains("NOT evidence of an attack"))
    }
}
