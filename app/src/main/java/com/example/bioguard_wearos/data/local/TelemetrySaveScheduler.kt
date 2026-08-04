package com.example.bioguard_wearos.data.local

import com.example.bioguard_wearos.data.local.db.BiometricReadingEntity
import com.example.bioguard_wearos.domain.model.SensorData
import com.example.bioguard_wearos.domain.repository.BiometricReadingRepository
import com.example.bioguard_wearos.domain.risk.BiometricInput
import com.example.bioguard_wearos.domain.risk.BiometricRiskEngine
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class TelemetrySaveScheduler(
    private val readingRepository: BiometricReadingRepository,
    private val sensorDataFlow: StateFlow<SensorData>,
    private val riskEngine: BiometricRiskEngine = BiometricRiskEngine(),
    private val intervalMs: Long = INTERVAL_MS
) {
    private var saveJob: Job? = null

    fun start(scope: CoroutineScope) {
        saveJob?.cancel()
        saveJob = scope.launch {
            while (isActive) {
                delay(intervalMs)
                saveOnce()
            }
        }
    }

    fun stop() {
        saveJob?.cancel()
        saveJob = null
    }

    suspend fun saveOnce() {
        val data = sensorDataFlow.value
        if (data.bpm <= 0f) return

        val input = BiometricInput(
            bpm = data.bpm,
            temperature = data.temperature,
            gsr = data.gsr,
            bmi = DEFAULT_BMI
        )
        val assessment = riskEngine.evaluate(input)

        val entity = BiometricReadingEntity(
            timestamp = System.currentTimeMillis(),
            bpm = data.bpm,
            temperature = data.temperature,
            gsr = data.gsr,
            bmi = DEFAULT_BMI,
            rmssd = data.rmssd,
            sdnn = data.sdnn,
            riskLevel = assessment.level,
            isSimulated = data.isSimulated,
            synced = false
        )
        try {
            readingRepository.saveReading(entity)
        } catch (e: Exception) {
            Log.w("BIOGUARD", "Error guardando lectura: ${e.message}")
        }
    }

    companion object {
        const val INTERVAL_MS = 10_000L
        const val DEFAULT_BMI = 24.0f
    }
}
