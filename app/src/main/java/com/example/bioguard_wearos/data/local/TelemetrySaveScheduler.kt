package com.example.bioguard_wearos.data.local

import com.example.bioguard_wearos.data.local.db.BiometricReadingEntity
import com.example.bioguard_wearos.domain.model.SensorData
import com.example.bioguard_wearos.domain.repository.BiometricReadingRepository
import com.example.bioguard_wearos.domain.risk.RiskAssessment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class TelemetrySaveScheduler(
    private val readingRepository: BiometricReadingRepository,
    private val sensorDataFlow: StateFlow<SensorData>,
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

        val assessment = RiskAssessment.fromBiometrics(
            bpm = data.bpm,
            temp = data.temperature,
            gsr = data.gsr
        )

        val entity = BiometricReadingEntity(
            timestamp = System.currentTimeMillis(),
            bpm = data.bpm,
            temperature = data.temperature,
            gsr = data.gsr,
            hrvRmssd = data.rmssd,
            hrvSdnn = data.sdnn,
            stressEstimate = data.stressEstimate,
            steps = data.steps,
            bmi = DEFAULT_BMI,
            riskLevel = assessment.level
        )
        readingRepository.saveReading(entity)
    }

    companion object {
        const val INTERVAL_MS = 10_000L
        const val DEFAULT_BMI = 24.0f
    }
}
