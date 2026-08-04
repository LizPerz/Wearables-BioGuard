package com.example.bioguard_wearos.data.local.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.bioguard_wearos.domain.risk.RiskLevel

@Entity(tableName = "biometric_readings", indices = [Index(value = ["synced", "timestamp"])])
data class BiometricReadingEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val bpm: Float,
    val temperature: Float,
    val gsr: Float,
    val bmi: Float,
    val rmssd: Float = 0f,
    val sdnn: Float = 0f,
    val riskLevel: RiskLevel,
    val isSimulated: Boolean = false,
    val synced: Boolean = false
)
