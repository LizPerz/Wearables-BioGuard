package com.example.bioguard_wearos.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface BiometricReadingDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(reading: BiometricReadingEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(readings: List<BiometricReadingEntity>): List<Long>

    @Query("SELECT * FROM biometric_readings ORDER BY timestamp DESC LIMIT :limit")
    fun getLatestReadings(limit: Int): Flow<List<BiometricReadingEntity>>

    @Query("SELECT * FROM biometric_readings WHERE synced = 0 ORDER BY timestamp ASC")
    suspend fun getUnsyncedReadings(): List<BiometricReadingEntity>

    @Query("SELECT * FROM biometric_readings ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestReading(): BiometricReadingEntity?

    @Query("UPDATE biometric_readings SET synced = 1 WHERE id IN (:ids)")
    suspend fun markAsSynced(ids: List<Long>)

    @Query("DELETE FROM biometric_readings WHERE timestamp < :thresholdTimestamp")
    suspend fun deleteOlderThan(thresholdTimestamp: Long)

    @Query("DELETE FROM biometric_readings")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM biometric_readings")
    suspend fun count(): Int
}
