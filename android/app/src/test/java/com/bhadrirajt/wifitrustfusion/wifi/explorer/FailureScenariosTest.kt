package com.bhadrirajt.wifitrustfusion.wifi.explorer

import org.junit.Assert.*
import org.junit.Test

class FailureScenariosTest {
    private fun scenario(id:String)=FailureScenarios.all.first { it.id==id }
    @Test fun catalogHasTwentyUniqueCasesAndTwelveRowsPerCase() {
        assertEquals(20,FailureScenarios.all.size)
        assertEquals(20,FailureScenarios.all.map { it.id }.toSet().size)
        assertTrue(FailureScenarios.all.all { it.checks.size==12 && it.checks.map { row->row.id }.toSet().size==12 })
    }
    @Test fun everyCopiedFixtureReportRemainsExplicitlySimulated() {
        FailureScenarios.all.forEach {
            val result=FailureScenarios.outcome(it,true)
            assertEquals(ReportOrigin.SIMULATED,result.report.origin)
            assertTrue(result.report.asText().startsWith("WiFiTrustFusion | SIMULATED"))
            assertTrue(result.response.contains("real Wi-Fi was not changed"))
        }
    }
    @Test fun eligibleFailurePausesOnlyItsVirtualSession() {
        assertTrue(FailureScenarios.outcome(scenario("tls"),true).virtualPaused)
        assertFalse(FailureScenarios.outcome(scenario("tls"),false).virtualPaused)
    }
    @Test fun healthyUnknownAndMissingConnectionCasesDoNotInventAnIsolation() {
        for(id in listOf("healthy","security_unknown","identity_hidden","wifi_off","disconnected"))
            assertFalse(id,FailureScenarios.outcome(scenario(id),true).virtualPaused)
    }
    @Test fun dnsFailureDoesNotFabricateSuccessfulDependentChecks() {
        val rows=scenario("dns").checks.associateBy { it.id }
        assertEquals(CheckState.FAILED,rows.getValue("dns").state)
        for(id in listOf("tcp","https","http")) assertEquals(CheckState.PENDING,rows.getValue(id).state)
    }
    @Test fun endpointErrorDoesNotTurnSuccessfulTlsIntoCertificateFailure() {
        val rows=scenario("http").checks.associateBy { it.id }
        assertEquals(CheckState.PASS,rows.getValue("https").state)
        assertEquals(CheckState.WARNING,rows.getValue("http").state)
        assertFalse(FailureScenarios.outcome(scenario("http"),true).virtualPaused)
    }
    @Test fun connectionChangeDiscardsAllActiveResults() {
        val rows=scenario("changed").checks.associateBy { it.id }
        for(id in listOf("dns","tcp","https","http","continuity")) assertEquals(CheckState.CANCELLED,rows.getValue(id).state)
    }
    @Test fun disconnectionDoesNotInventRadioSamples() {
        assertTrue(scenario("wifi_off").samples.isEmpty())
        assertTrue(scenario("disconnected").samples.isEmpty())
    }
}
