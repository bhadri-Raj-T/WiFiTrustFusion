package com.bhadrirajt.wifitrustfusion.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bhadrirajt.wifitrustfusion.wifi.model.CollectorSessionMetadata
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class M3_4D_D2C_RoomNonCleanCompletionTest {
    @Test fun nonCleanCompletionNeverClaimsMatch() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, EvidenceDatabase::class.java).build()
        val id = "SYNTH_D2C_ROOM_NONCLEAN"
        val latch = CountDownLatch(1)
        val observed = AtomicReference<RoomEvidenceMirror.MirrorStatus>()
        val mirror = RoomEvidenceMirror(db, onStatus = { status ->
            if (status.state == "MATCH" || status.state == "REVIEW_REQUIRED") {
                observed.set(status)
                latch.countDown()
            }
        })
        try {
            val session = CollectorSessionMetadata(
                id, "SYNTH_COLLECTOR", "SYNTH_PROTOCOL", "SYNTH_SCHEMA", "test", 1L,
                "EMULATOR_TEST", "SYNTH", "SYNTH", "SYNTH", "11", 30,
                "2026-11-01T00:00:00Z", 100000L, 35000L, "SYNTH_ONLY"
            )
            mirror.recordSession(session)
            mirror.recordCompletion(
                id, "2026-11-01T00:01:00Z", "LOGGER_WRITE_FAILED", 0, false
            )
            assertTrue("Room completion callback timed out", latch.await(20, TimeUnit.SECONDS))
            assertEquals("REVIEW_REQUIRED", observed.get()?.state)
            runBlocking {
                assertEquals(false, db.evidenceDao().completionForSession(id)?.cleanClose)
            }
        } finally {
            mirror.shutdown()
            db.close()
        }
    }
}
