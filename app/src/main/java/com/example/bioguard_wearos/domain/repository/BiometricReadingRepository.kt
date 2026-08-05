package com.example.bioguard_wearos.domain.repository

import com.example.bioguard_wearos.data.local.db.BiometricReadingEntity
import kotlinx.coroutines.flow.Flow

interface BiometricReadingRepository {

    suspend fun saveReading(reading: BiometricReadingEntity): Long

    suspend fun saveAllReadings(readings: List<BiometricReadingEntity>): List<Long>

    fun getLatestReadings(limit: Int = 50): Flow<List<BiometricReadingEntity>>

    suspend fun getUnsyncedReadings(): List<BiometricReadingEntity>

    suspend fun markAsSynced(ids: List<Long>)

    suspend fun purgeOldReadings()

    suspend fun getCount(): Int
}
