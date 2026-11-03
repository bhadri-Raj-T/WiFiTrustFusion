package com.bhadrirajt.wifitrustfusion.database

import com.bhadrirajt.wifitrustfusion.wifi.model.CollectorEventRecord
import com.bhadrirajt.wifitrustfusion.wifi.model.CollectorSessionMetadata
import com.bhadrirajt.wifitrustfusion.wifi.model.ScanRequestRecord
import com.bhadrirajt.wifitrustfusion.wifi.model.WifiObservation

/** M3.3A: tested Room write boundary. NOT yet wired into MainActivity.
 *
 * All methods are suspend. Never invoke on the Android main thread via runBlocking.
 * A failed request bundle must leave no partial request/observation rows.
 * Duplicate records ABORT instead of replacing evidence.
 */
class EvidenceWriteGateway(private val dao: EvidenceDao) {
    suspend fun saveSession(record: CollectorSessionMetadata) {
        require(record.sessionId.isNotBlank()) { "Blank session ID" }
        dao.insertSession(EvidenceRecordMapper.session(record))
    }

    suspend fun saveRequestBundle(
        record: ScanRequestRecord,
        observations: List<WifiObservation>
    ) {
        require(record.sessionId.isNotBlank() && record.requestId.isNotBlank()) {
            "Blank request identity"
        }
        require(record.resultCount >= 0 && record.resultCount == observations.size) {
            "Request result count does not match observations"
        }
        require(observations.all {
            it.sessionId == record.sessionId && it.requestId == record.requestId
        }) {
            "Observation does not belong to request"
        }
        dao.insertRequestBundle(
            EvidenceRecordMapper.request(record),
            observations.map(EvidenceRecordMapper::observation)
        )
    }

    suspend fun saveEvent(record: CollectorEventRecord) {
        require(record.sessionId.isNotBlank()) { "Blank event session ID" }
        dao.insertEvent(EvidenceRecordMapper.event(record))
    }

    suspend fun saveCompletion(
        sessionId: String,
        endedUtcIso: String,
        stopReason: String,
        completedRequestCount: Int,
        cleanClose: Boolean
    ) {
        require(sessionId.isNotBlank()) { "Blank completion session ID" }
        require(completedRequestCount >= 0) { "Negative completed request count" }
        dao.insertCompletion(EvidenceRecordMapper.completion(
            sessionId, endedUtcIso, stopReason, completedRequestCount, cleanClose
        ))
    }

    suspend fun requestCount(sessionId: String): Int = dao.requestCount(sessionId)
    suspend fun observationCount(sessionId: String): Int = dao.observationCount(sessionId)
}
