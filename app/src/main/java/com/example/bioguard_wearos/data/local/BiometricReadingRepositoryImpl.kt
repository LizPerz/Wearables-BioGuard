package com.example.bioguard_wearos.data.local

import com.example.bioguard_wearos.data.local.db.BiometricReadingDao
import com.example.bioguard_wearos.data.local.db.BiometricReadingEntity
import com.example.bioguard_wearos.domain.repository.BiometricReadingRepository
import kotlinx.coroutines.flow.Flow
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BiometricReadingRepositoryImpl @Inject constructor(
    private val dao: BiometricReadingDao
) : BiometricReadingRepository {

    override suspend fun saveReading(reading: BiometricReadingEntity): Long {
        return dao.insert(reading)
    }

    override suspend fun saveAllReadings(readings: List<BiometricReadingEntity>): List<Long> {
        return dao.insertAll(readings)
    }

    override fun getLatestReadings(limit: Int): Flow<List<BiometricReadingEntity>> {
        return dao.getLatestReadings(limit)
    }

    override suspend fun getUnsyncedReadings(): List<BiometricReadingEntity> {
        return dao.getUnsyncedReadings()
    }

    override suspend fun markAsSynced(ids: List<Long>) {
        dao.markAsSynced(ids)
    }

    override suspend fun purgeOldReadings() {
        val threshold = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(RETENTION_DAYS)
        dao.deleteOlderThan(threshold)
    }

    override suspend fun getCount(): Int {
        return dao.count()
    }

    companion object {
        private const val RETENTION_DAYS = 1L
    }
}
