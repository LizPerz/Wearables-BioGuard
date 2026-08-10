package com.example.bioguard_wearos.data.local.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.bioguard_wearos.domain.risk.RiskLevel

@Entity(tableName = "biometric_readings")
data class BiometricReadingEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val bpm: Float,
    val temperature: Float,
    val gsr: Float,
    val hrvRmssd: Float = 0f,
    val hrvSdnn: Float = 0f,
    val stressEstimate: Float = 0f,
    val steps: Int? = null,
    val bmi: Float,
    val riskLevel: RiskLevel,
    val synced: Boolean = false
)
