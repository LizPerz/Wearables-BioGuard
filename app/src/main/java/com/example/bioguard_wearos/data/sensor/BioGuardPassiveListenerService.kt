package com.example.bioguard_wearos.data.sensor

import android.util.Log
import androidx.health.services.client.PassiveListenerService
import androidx.health.services.client.data.DataPointContainer
import androidx.health.services.client.data.DataType
import com.example.bioguard_wearos.data.energy.DutyCycleManager
import com.example.bioguard_wearos.domain.repository.BiometricReadingRepository
import com.example.bioguard_wearos.domain.repository.SensorDataRepository
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class BioGuardPassiveListenerService : PassiveListenerService() {

    companion object {
        private const val TAG = "BIOGUARD_PASSIVE_SENSOR"
    }

    @Inject
    lateinit var dutyCycleManager: DutyCycleManager

    @Inject
    lateinit var sensorDataRepository: SensorDataRepository

    override fun onNewDataPointsReceived(dataPoints: DataPointContainer) {
        // Ejecutar procesamiento rápido en un micro WakeLock para liberar CPU
        // NOTA: Se lee frecuencia cardíaca (HEART_RATE_BPM) de forma pasiva.
        // GSR/Sudoración NO se lee aquí ya que es un valor estimado en la
        // mayoría de smartwatches Wear OS (derivado de HR + temperatura).
        dutyCycleManager.runWithMicroWakeLock {
            val heartRateData = dataPoints.getData(DataType.HEART_RATE_BPM)
            for (dataPoint in heartRateData) {
                val bpm = dataPoint.value.toFloat()
                if (bpm > 0f) {
                    Log.d(TAG, "Lectura de pulso pasiva recibida: $bpm BPM")
                }
            }
        }
    }
}
