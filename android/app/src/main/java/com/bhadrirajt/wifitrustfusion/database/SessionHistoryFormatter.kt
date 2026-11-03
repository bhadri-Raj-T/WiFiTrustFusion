package com.bhadrirajt.wifitrustfusion.database

/** Display only: does not write a new scientific evidence record. */
object SessionHistoryFormatter {
    fun render(rows: List<SessionHistoryRow>): String {
        if (rows.isEmpty()) {
            return "No Room-mirrored sessions found on this phone.\n" +
                "Run a development session, then refresh history.\n" +
                "CSV evidence remains authoritative."
        }
        return buildString {
            appendLine("ROOM-MIRROR SESSION HISTORY (${rows.size} most recent)")
            appendLine("Aggregate counts only; no SSID/BSSID displayed.")
            rows.forEachIndexed { index, row ->
                val state = when (row.cleanClose) {
                    true -> "CLEAN STOP (recorded)"
                    false -> "NON-CLEAN STOP"
                    null -> "NO COMPLETION RECORD"
                }
                appendLine("${index + 1}. Session ...${row.sessionId.takeLast(8)}")
                appendLine("   Started UTC: ${row.startedUtcIso.take(19)}")
                appendLine("   Room: ${row.requestCount} requests, ${row.observationCount} observations")
                appendLine("   State: $state")
                appendLine("   Reason: ${(row.stopReason ?: "not recorded").take(48)}")
            }
            appendLine("CSV files are the authoritative acquisition evidence.")
            append("M4/M5 assessments are NOT persisted in this history view.")
        }
    }
}
