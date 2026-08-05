package com.example.bioguard_wearos.data.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import com.example.bioguard_wearos.BuildConfig
import com.example.bioguard_wearos.domain.model.SensorData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlin.math.sin
import kotlin.random.Random

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
    private var _isAvailable = false
    private var simulationRunning = false
    private var simTime = 0f
    private var lastBpm = 70f

    override val isAvailable: Boolean get() = _isAvailable

    override fun bindToHeartRate(dataFlow: StateFlow<SensorData>) {
        scope.launch {
            dataFlow.collect { data ->
                if (data.bpm > 0f) lastBpm = data.bpm
            }
        }
    }

    override fun start() {
        tempSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_AMBIENT_TEMPERATURE)
        if (tempSensor != null) {
            sensorManager?.registerListener(object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent?) {
                    event ?: return
                    if (event.sensor?.type == Sensor.TYPE_AMBIENT_TEMPERATURE) {
                        if (event.values.isNotEmpty() && event.values[0] in 10.0f..50.0f) {
                            onTemperatureChanged(event.values[0])
                        }
                    }
                }
                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
            }, tempSensor, SensorManager.SENSOR_DELAY_NORMAL)
            _isAvailable = true
            Log.d("BIOGUARD", "Temperatura real disponible via SensorManager")
            return
        }

        if (BuildConfig.DEBUG) {
            Log.w("BIOGUARD", "Sensor de temperatura HW no disponible. Usando simulación correlacionada con BPM")
            startSimulation()
        } else {
            Log.w("BIOGUARD", "Sensor de temperatura HW no disponible en release. Temperatura desactivada.")
        }
    }

    override fun stop() {
        simulationRunning = false
        _isAvailable = false
    }

    private fun startSimulation() {
        if (simulationRunning) return
        simulationRunning = true
        _isAvailable = true

        scope.launch {
            delay(2500)
            Log.d("BIOGUARD", "Simulador de temperatura iniciado")

            while (simulationRunning) {
                val hrFactor = ((lastBpm - 50f) / 150f).coerceIn(0f, 1f)
                val baseTemp = 36.2f + hrFactor * 1.3f
                val oscillation = sin(simTime * 0.5f) * 0.15f
                val noise = (Random.nextFloat() - 0.5f) * 0.1f
                val temp = (baseTemp + oscillation + noise).coerceIn(35.5f, 37.8f)

                onTemperatureChanged(temp)
                Log.d("BIOGUARD", "Temp estimada: ${String.format("%.1f", temp)}°C (BPM=${String.format("%.0f", lastBpm)})")

                simTime += 0.4f
                delay(1000)
            }
        }
    }
}
