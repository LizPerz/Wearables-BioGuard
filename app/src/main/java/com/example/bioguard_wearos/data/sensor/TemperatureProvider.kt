package com.example.bioguard_wearos.data.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import com.example.bioguard_wearos.domain.model.SensorData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

interface TemperatureProvider {
    fun start()
    fun stop()
    fun bindToHeartRate(dataFlow: StateFlow<SensorData>)
    val isAvailable: Boolean
}

class SensorManagerTemperatureProvider(
    context: Context,
    private val onTemperatureChanged: (Float) -> Unit
) : TemperatureProvider {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private var tempSensor: Sensor? = null
    private var tempListener: SensorEventListener? = null
    private var _isAvailable = false
    private var lastBpm = 70f
    private var lastReportedTemp = 0f

    override val isAvailable: Boolean get() = _isAvailable

    override fun bindToHeartRate(dataFlow: StateFlow<SensorData>) {
        scope.launch {
            dataFlow.collect { data ->
                if (data.bpm > 0f) lastBpm = data.bpm
            }
        }
    }

    override fun start() {
        stop()
        // Prioridad 1: sensor de temperatura infrarroja/ambiente (el que expone el Galaxy Watch).
        tempSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_AMBIENT_TEMPERATURE)
        if (tempSensor == null) {
            // Prioridad 2: sensor de temperatura del dispositivo (deprecado desde API 14).
            @Suppress("DEPRECATION")
            tempSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_TEMPERATURE)
        }
        if (tempSensor == null) {
            _isAvailable = false
            Log.i("BIOGUARD", "Sensor de temperatura no disponible en este dispositivo")
            return
        }
        _isAvailable = true
        tempListener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                event ?: return
                val temp = event.values.firstOrNull() ?: return
                if (temp <= 0f) return
                // Se reporta solo ante cambios significativos para no saturar el flujo de datos.
                if (kotlin.math.abs(temp - lastReportedTemp) >= 0.1f) {
                    lastReportedTemp = temp
                    onTemperatureChanged(temp)
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
                // No-op.
            }
        }
        sensorManager?.registerListener(tempListener, tempSensor, SensorManager.SENSOR_DELAY_NORMAL)
        Log.i("BIOGUARD", "Sensor de temperatura infrarroja registrado: ${tempSensor?.name}")
    }

    override fun stop() {
        tempListener?.let { listener ->
            sensorManager?.unregisterListener(listener)
        }
        tempListener = null
        _isAvailable = false
    }
}
