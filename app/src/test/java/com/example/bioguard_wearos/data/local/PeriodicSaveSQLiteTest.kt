package com.example.bioguard_wearos.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.bioguard_wearos.data.local.db.BiometricReadingDao
import com.example.bioguard_wearos.data.local.db.BioGuardDatabase
import com.example.bioguard_wearos.domain.model.SensorData
import com.example.bioguard_wearos.domain.risk.RiskLevel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class PeriodicSaveSQLiteTest {

    private lateinit var database: BioGuardDatabase
    private lateinit var dao: BiometricReadingDao
    private lateinit var repository: BiometricReadingRepositoryImpl

    @Before
    fun setup() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            BioGuardDatabase::class.java
        ).allowMainThreadQueries().build()
        dao = database.biometricReadingDao()
        repository = BiometricReadingRepositoryImpl(dao)
    }

    @After
    fun teardown() {
        database.close()
    }

    @Test
    fun `saveOnce persists all fields to SQLite correctly`() = runBlocking {
        val sensorData = MutableStateFlow(
            SensorData(bpm = 85f, temperature = 36.5f, estresPct = 30f)
        )
        val scheduler = TelemetrySaveScheduler(repository, sensorData)

        scheduler.saveOnce()

        assertEquals(1, dao.count())
        val reading = dao.getLatestReading()!!
        assertEquals(85f, reading.bpm)
        assertEquals(36.5f, reading.temperature)
        assertEquals(30f, reading.gsr)
        assertEquals(24f, reading.bmi)
        assertTrue(reading.timestamp > 0)
    }

    @Test
    fun `saveOnce evaluates CRITICAL_HIGH risk and stores it in SQLite`() = runBlocking {
        val sensorData = MutableStateFlow(
            SensorData(bpm = 120f, temperature = 38f, estresPct = 90f)
        )
        val scheduler = TelemetrySaveScheduler(repository, sensorData)

        scheduler.saveOnce()

        val reading = dao.getLatestReading()!!
        assertEquals(RiskLevel.CRITICAL_HIGH, reading.riskLevel)
    }

    @Test
    fun `saveOnce evaluates OPTIMAL risk and stores it in SQLite`() = runBlocking {
        val sensorData = MutableStateFlow(
            SensorData(bpm = 70f, temperature = 36.3f, estresPct = 25f)
        )
        val scheduler = TelemetrySaveScheduler(repository, sensorData)

        scheduler.saveOnce()

        val reading = dao.getLatestReading()!!
        assertEquals(RiskLevel.OPTIMAL, reading.riskLevel)
    }

    @Test
    fun `saveOnce skips when bpm is zero`() = runBlocking {
        val sensorData = MutableStateFlow(SensorData(bpm = 0f))
        val scheduler = TelemetrySaveScheduler(repository, sensorData)

        scheduler.saveOnce()

        assertEquals(0, dao.count())
    }

    @Test
    fun `multiple saveOnce calls accumulate records in SQLite`() = runBlocking {
        val sensorData = MutableStateFlow(
            SensorData(bpm = 88f, temperature = 36.8f, estresPct = 42f)
        )
        val scheduler = TelemetrySaveScheduler(repository, sensorData)

        repeat(5) { scheduler.saveOnce() }

        assertEquals(5, dao.count())
        val allReadings = dao.getLatestReadings(10).first()
        assertTrue(allReadings.all { it.bpm == 88f })
    }

    @Test
    fun `periodic scheduler saves to SQLite on each interval`() = runBlocking {
        val sensorData = MutableStateFlow(
            SensorData(bpm = 75f, temperature = 36.4f, estresPct = 22f)
        )
        val scheduler = TelemetrySaveScheduler(repository, sensorData, intervalMs = 1_000L)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        scheduler.start(scope)

        val countAfterFirst = awaitCountAtLeast(1)
        val countAfterSecond = awaitCountAtLeast(countAfterFirst + 1)
        val countAfterThird = awaitCountAtLeast(countAfterSecond + 1)

        scheduler.stop()
        scope.cancel()

        val readings = dao.getLatestReadings(countAfterThird).first()
        readings.forEach { reading ->
            assertEquals(75f, reading.bpm)
            assertEquals(36.4f, reading.temperature)
            assertEquals(22f, reading.gsr)
            assertEquals(RiskLevel.OPTIMAL, reading.riskLevel)
        }
    }

    @Test
    fun `scheduler does not save when bpm is zero`() = runBlocking {
        val sensorData = MutableStateFlow(SensorData(bpm = 0f))
        val scheduler = TelemetrySaveScheduler(repository, sensorData)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        scheduler.start(scope)
        delay(12_000)
        scheduler.stop()
        scope.cancel()

        assertEquals(0, dao.count())
    }

    @Test
    fun `stop prevents further saves to SQLite`() = runBlocking {
        val sensorData = MutableStateFlow(
            SensorData(bpm = 80f, temperature = 36.5f, estresPct = 25f)
        )
        val scheduler = TelemetrySaveScheduler(repository, sensorData, intervalMs = 10_000L)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        scheduler.start(scope)
        scheduler.saveOnce()
        val countBeforeStop = dao.count()
        assertTrue("Debe haber al menos 1 registro antes de stop", countBeforeStop >= 1)

        scheduler.stop()
        scope.cancel()

        delay(1_200)
        assertEquals("Tras stop no debe haber más registros", countBeforeStop, dao.count())
    }

    private suspend fun awaitCountAtLeast(expected: Int, timeoutMs: Long = 8_000): Int {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (dao.count() < expected && System.currentTimeMillis() < deadline) {
            delay(100)
        }
        val count = dao.count()
        assertTrue("Se esperaban >= $expected registros, got $count", count >= expected)
        return count
    }
}
