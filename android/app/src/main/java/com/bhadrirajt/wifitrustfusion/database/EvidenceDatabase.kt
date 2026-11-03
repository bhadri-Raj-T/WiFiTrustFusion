package com.bhadrirajt.wifitrustfusion.database

import androidx.room.Database
import androidx.room.RoomDatabase

/** M3.1 schema only. Not connected to the frozen collector/CSV logger. */
@Database(
    entities = [
        CollectorSessionEntity::class,
        ScanRequestEntity::class,
        WifiObservationEntity::class,
        CollectorEventEntity::class,
        SessionCompletionEntity::class
    ],
    version = 1,
    exportSchema = true
)
abstract class EvidenceDatabase : RoomDatabase() {
    abstract fun evidenceDao(): EvidenceDao

    companion object {
        const val DATABASE_NAME = "wifitrustfusion_evidence_m3.db"
    }
}
