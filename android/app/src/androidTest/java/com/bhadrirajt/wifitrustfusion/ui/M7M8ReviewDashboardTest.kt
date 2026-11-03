package com.bhadrirajt.wifitrustfusion.ui

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class M7M8ReviewDashboardTest {
    private fun example(
        source: ReviewDashboardFormatter.Source = ReviewDashboardFormatter.Source.NONE,
        quality: String? = null,
        csvFailure: Boolean = false,
        running: Boolean = false,
        history: Boolean = false
    ) = ReviewDashboardFormatter.Snapshot(
        source = source, sessionRunning = running, requestsCompleted = 2,
        observations = 3, evidenceQuality = quality,
        securityChanges = 1, bandChanges = 1,
        roomState = "REVIEW_REQUIRED", csvFailureSeen = csvFailure,
        historyLoaded = history
    )

    @Test fun idleDoesNotClaimAssessment() {
        val result = ReviewDashboardFormatter.render(example())
        assertTrue(result.contains("NO ASSESSMENT YET"))
        assertTrue(result.contains("Evidence quality: NOT ASSESSED"))
        assertFalse(result.contains("SOURCE: LIVE ANDROID"))
    }
    @Test fun syntheticIsExplicitlyMarked() {
        val result = ReviewDashboardFormatter.render(
            example(ReviewDashboardFormatter.Source.SYNTHETIC, "USABLE")
        )
        assertTrue(result.contains("SYNTHETIC DEMONSTRATION - NOT MEASURED"))
        assertFalse(result.contains("SOURCE: LIVE ANDROID"))
    }
    @Test fun liveIsDistinctFromSynthetic() {
        val result = ReviewDashboardFormatter.render(
            example(ReviewDashboardFormatter.Source.LIVE, "PROVISIONAL")
        )
        assertTrue(result.contains("LIVE ANDROID SCAN"))
        assertFalse(result.contains("SYNTHETIC DEMONSTRATION"))
    }
    @Test fun missingQualityIsUnknownNotUsable() {
        val result = ReviewDashboardFormatter.render(
            example(ReviewDashboardFormatter.Source.LIVE)
        )
        assertTrue(result.contains("Evidence quality: UNKNOWN"))
    }
    @Test fun loggerErrorIsClearlyVisible() {
        assertTrue(ReviewDashboardFormatter.render(example(csvFailure = true))
            .contains("CSV write error seen: YES"))
    }
    @Test fun negativeCountsCannotBeDisplayed() {
        val result = ReviewDashboardFormatter.render(example().copy(
            requestsCompleted = -1, observations = -7, securityChanges = -3,
            source = ReviewDashboardFormatter.Source.LIVE))
        assertTrue(result.contains("Live session completed requests: 0"))
        assertTrue(result.contains("Observations in displayed assessment: 0"))
        assertTrue(result.contains("Security-category changes: 0"))
    }
    @Test fun historyAndRunningStatesVisible() {
        val result = ReviewDashboardFormatter.render(example(running = true, history = true))
        assertTrue(result.contains("Session: RUNNING"))
        assertTrue(result.contains("Session history: REQUESTED"))
    }
    @Test fun unvalidatedClaimsAreNotMade() {
        val result = ReviewDashboardFormatter.render(example())
        assertTrue(result.contains("No validated WFAI, attack verdict"))
        assertTrue(result.contains("CSV remains authoritative"))
        assertTrue(ReviewDashboardFormatter.reviewGuide().contains("3-5 MIN"))
    }
}
