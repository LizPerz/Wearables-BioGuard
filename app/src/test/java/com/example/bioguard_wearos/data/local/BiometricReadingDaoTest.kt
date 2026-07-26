package com.example.bioguard_wearos.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.bioguard_wearos.data.local.db.BiometricReadingDao
import com.example.bioguard_wearos.data.local.db.BiometricReadingEntity
import com.example.bioguard_wearos.data.local.db.BioGuardDatabase
import com.example.bioguard_wearos.domain.risk.RiskLevel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class BiometricReadingDaoTest {

    private lateinit var database: BioGuardDatabase
    private lateinit var dao: BiometricReadingDao

    @Before
    fun setup() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            BioGuardDatabase::class.java
        ).allowMainThreadQueries().build()
        dao = database.biometricReadingDao()
    }

    @After
    fun teardown() {
        database.close()
    }

    @Test
    fun `insert and retrieve single reading`() = runTest {
        val entity = createReading(bpm = 75f, temperature = 36.5f, gsr = 25f, riskLevel = RiskLevel.OPTIMAL)
        val id = dao.insert(entity)
        assertTrue(id > 0)

        val latest = dao.getLatestReading()
        assertNotNull(latest)
        assertEquals(75f, latest!!.bpm)
        assertEquals(36.5f, latest.temperature)
        assertEquals(25f, latest.gsr)
        assertEquals(RiskLevel.OPTIMAL, latest.riskLevel)
    }

    @Test
    fun `insert multiple readings and query latest`() = runTest {
        val readings = listOf(
            createReading(bpm = 70f, riskLevel = RiskLevel.OPTIMAL, timestampOffset = 0),
            createReading(bpm = 95f, riskLevel = RiskLevel.MODERATE_HIGH, timestampOffset = 1000),
            createReading(bpm = 120f, riskLevel = RiskLevel.CRITICAL_HIGH, timestampOffset = 2000)
        )
        dao.insertAll(readings)

        val latest = dao.getLatestReadings(2).first()
        assertEquals(2, latest.size)
        assertEquals(120f, latest[0].bpm)
        assertEquals(95f, latest[1].bpm)
    }

    @Test
    fun `risk level enum mapping roundtrip`() = runTest {
        for (level in RiskLevel.entries) {
            val entity = createReading(bpm = 80f, riskLevel = level, timestampOffset = level.ordinal * 1000L)
            dao.insert(entity)
        }

        val all = dao.getLatestReadings(10).first()
        assertEquals(3, all.size)

        val levels = all.map { it.riskLevel }.toSet()
        assertTrue(levels.contains(RiskLevel.OPTIMAL))
        assertTrue(levels.contains(RiskLevel.MODERATE_HIGH))
        assertTrue(levels.contains(RiskLevel.CRITICAL_HIGH))
    }

    @Test
    fun `getUnsyncedReadings returns only unsynced`() = runTest {
        dao.insert(createReading(bpm = 70f, synced = false, timestampOffset = 0))
        dao.insert(createReading(bpm = 80f, synced = true, timestampOffset = 1000))
        dao.insert(createReading(bpm = 90f, synced = false, timestampOffset = 2000))

        val unsynced = dao.getUnsyncedReadings()
        assertEquals(2, unsynced.size)
        assertTrue(unsynced.all { !it.synced })
    }

    @Test
    fun `markAsSynced updates synced flag`() = runTest {
        val id1 = dao.insert(createReading(bpm = 70f, synced = false, timestampOffset = 0))
        val id2 = dao.insert(createReading(bpm = 80f, synced = false, timestampOffset = 1000))

        dao.markAsSynced(listOf(id1))

        val unsynced = dao.getUnsyncedReadings()
        assertEquals(1, unsynced.size)
        assertEquals(id2, unsynced[0].id)
    }

    @Test
    fun `deleteOlderThan removes old readings`() = runTest {
        val now = System.currentTimeMillis()
        dao.insert(createReading(bpm = 70f, timestamp = now - TimeUnit.DAYS.toMillis(2)))
        dao.insert(createReading(bpm = 80f, timestamp = now - TimeUnit.HOURS.toMillis(12)))
        dao.insert(createReading(bpm = 90f, timestamp = now))

        val threshold = now - TimeUnit.DAYS.toMillis(1)
        dao.deleteOlderThan(threshold)

        val remaining = dao.getLatestReadings(10).first()
        assertEquals(2, remaining.size)
        assertTrue(remaining.all { it.timestamp >= threshold })
    }

    @Test
    fun `count returns correct total`() = runTest {
        assertEquals(0, dao.count())

        dao.insert(createReading(bpm = 70f, timestampOffset = 0))
        dao.insert(createReading(bpm = 80f, timestampOffset = 1000))
        assertEquals(2, dao.count())
    }

    @Test
    fun `deleteAll clears entire table`() = runTest {
        dao.insert(createReading(bpm = 70f, timestampOffset = 0))
        dao.insert(createReading(bpm = 80f, timestampOffset = 1000))
        assertEquals(2, dao.count())

        dao.deleteAll()
        assertEquals(0, dao.count())
    }

    @Test
    fun `insert with REPLACE strategy updates existing by primary key`() = runTest {
        val entity = createReading(bpm = 70f, timestampOffset = 0)
        val id1 = dao.insert(entity)

        val updated = entity.copy(id = id1, bpm = 85f)
        val id2 = dao.insert(updated)

        assertEquals(id1, id2)
        val latest = dao.getLatestReading()
        assertEquals(85f, latest!!.bpm)
        assertEquals(1, dao.count())
    }

    private fun createReading(
        bpm: Float = 75f,
        temperature: Float = 36.5f,
        gsr: Float = 25f,
        bmi: Float = 24f,
        riskLevel: RiskLevel = RiskLevel.OPTIMAL,
        synced: Boolean = false,
        timestamp: Long = System.currentTimeMillis(),
        timestampOffset: Long = 0
    ): BiometricReadingEntity {
        return BiometricReadingEntity(
            timestamp = timestamp + timestampOffset,
            bpm = bpm,
            temperature = temperature,
            gsr = gsr,
            bmi = bmi,
            riskLevel = riskLevel,
            synced = synced
        )
    }
}
