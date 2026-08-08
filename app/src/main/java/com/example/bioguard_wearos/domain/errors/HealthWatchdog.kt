package com.example.bioguard_wearos.domain.errors

import android.util.Log
import com.example.bioguard_wearos.domain.repository.BiometricReadingRepository
import com.example.bioguard_wearos.domain.repository.SensorDataRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HealthWatchdog @Inject constructor(
    private val sensorDataRepository: SensorDataRepository,
    private val readingRepository: BiometricReadingRepository
) {
    companion object {
        private const val TAG = "BIOGUARD_WATCHDOG"
        private const val CHECK_INTERVAL_MS = 2 * 60 * 1000L // 2 minutos
        private const val MAX_DB_CAPACITY_READINGS = 10_000
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    @Volatile private var isRunning = false

    fun start() {
        if (isRunning) return
        isRunning = true
        Log.d(TAG, "HealthWatchdog iniciado")
        scope.launch {
            while (isRunning) {
                try {
                    checkSensorHealth()
                    enforceDatabaseCapacity()
                } catch (e: Exception) {
                    Log.e(TAG, "Error en ciclo de vigilancia de HealthWatchdog: ${e.message}", e)
                }
                delay(CHECK_INTERVAL_MS)
            }
        }
    }

    fun stop() {
        isRunning = false
        Log.d(TAG, "HealthWatchdog detenido")
    }

    private fun checkSensorHealth() {
        val currentData = sensorDataRepository.sensorData.value
        if (currentData.bpm <= 0f) {
            Log.w(TAG, "ADVERTENCIA: No se reciben datos de pulso de sensores. Reiniciando subsistema de captura.")
            try {
                sensorDataRepository.stopSensors()
                sensorDataRepository.startSensors()
            } catch (e: Exception) {
                Log.e(TAG, "Error durante reinicio preventivo de sensores: ${e.message}", e)
            }
        } else {
            Log.d(TAG, "Chequeo de sensores OK (BPM=${currentData.bpm}, Temp=${currentData.temperature})")
        }
    }

    private suspend fun enforceDatabaseCapacity() {
        try {
            val unsyncedReadings = readingRepository.getUnsyncedReadings()
            if (unsyncedReadings.size > MAX_DB_CAPACITY_READINGS) {
                val excess = unsyncedReadings.size - MAX_DB_CAPACITY_READINGS
                Log.w(TAG, "Capacidad de almacenamiento excedida (${unsyncedReadings.size} lecturas). Ejecutando purga FIFO de $excess lecturas rutinarias.")
                val routineToDrop = unsyncedReadings
                    .filter { it.riskLevel.label != "CRITICAL_HIGH" }
                    .take(excess)
                    .map { it.id }
                if (routineToDrop.isNotEmpty()) {
                    readingRepository.markAsSynced(routineToDrop)
                    Log.d(TAG, "Purga FIFO completada. ${routineToDrop.size} lecturas rutinarias descartadas para preservar eventos de alerta.")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error en control de capacidad de almacenamiento: ${e.message}", e)
        }
    }
}
