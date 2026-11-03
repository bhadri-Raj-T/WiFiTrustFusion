package com.bhadrirajt.wifitrustfusion.wifi.explorer

import org.junit.Assert.*
import org.junit.Test

class ConnectionCheckReportTest {
    private fun facts(dns:Boolean?=true,https:Boolean?=true,success:Int=3,attempts:Int=3,code:Int?=200,stable:Boolean=true)=
        ConnectionCheckReports.Diagnostics(dns,https,success,attempts,35.0,"DNS result","HTTPS result",code,stable)
    private fun report(d:ConnectionCheckReports.Diagnostics?=null)=ConnectionCheckReports.live(
        ConnectionCheckReports.Inputs(connected=true,identityVisible=true,rssiDbm=-48,diagnostics=d))
    @Test fun reportContainsTwelveDistinctItems() {
        val r=report(); assertEquals(12,r.items.size); assertEquals(12,r.items.map { it.id }.toSet().size)
        assertEquals(ReportOrigin.LIVE,r.origin)
    }
    @Test fun unavailableSecurityAndUntestedProbesAreNotPasses() {
        val rows=report().items.associateBy { it.id }
        assertEquals(CheckState.UNAVAILABLE,rows.getValue("security").state)
        for(id in listOf("dns","tcp","https","http","continuity")) assertEquals(CheckState.PENDING,rows.getValue(id).state)
    }
    @Test fun dnsFailureDoesNotMarkSkippedTlsSuccessful() {
        val rows=report(facts(dns=false,https=null,success=0,attempts=0,code=null)).items.associateBy { it.id }
        assertEquals(CheckState.FAILED,rows.getValue("dns").state)
        assertEquals(CheckState.PENDING,rows.getValue("https").state)
    }
    @Test fun successfulTlsAndHttpErrorRemainDifferentObservations() {
        val rows=report(facts(code=503)).items.associateBy { it.id }
        assertEquals(CheckState.PASS,rows.getValue("https").state)
        assertEquals(CheckState.WARNING,rows.getValue("http").state)
    }
    @Test fun partialTcpSuccessShowsAttention() {
        assertEquals(CheckState.WARNING,report(facts(success=1)).items.first { it.id=="tcp" }.state)
    }
    @Test fun staleConnectionResultsCannotBecomeSuccessfulLiveProbes() {
        val rows=report(facts(stable=false)).items.associateBy { it.id }
        for(id in listOf("dns","tcp","https","http")) assertEquals(CheckState.PENDING,rows.getValue(id).state)
        assertEquals(CheckState.CANCELLED,rows.getValue("continuity").state)
    }
    @Test fun strongSignalDoesNotCreateAPassedSecurityCertification() {
        val r=ConnectionCheckReports.live(ConnectionCheckReports.Inputs(connected=true,rssiDbm=-35,
            security="WPA3-Personal",securitySource="Current connection"))
        assertEquals(CheckState.INFO,r.items.first { it.id=="security" }.state)
    }
}
