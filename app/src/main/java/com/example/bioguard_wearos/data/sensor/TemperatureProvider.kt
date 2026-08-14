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
        tempSensor = discoverTemperatureSensor()
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
        Log.i("BIOGUARD", "Sensor de temperatura registrado: ${tempSensor?.name} (vendor=${tempSensor?.vendor})")
    }

    /**
     * Descubre un sensor de temperatura en el dispositivo.
     * Prioridades:
     *  1) Sensor de temperatura ambiente estándar (TYPE_AMBIENT_TEMPERATURE).
     *  2) Sensor de temperatura del dispositivo (TYPE_TEMPERATURE, deprecado desde API 14).
     *  3) Sensores propietarios expuestos por fabricantes (p. ej. Samsung BioActive, termómetro
     *     infrarrojo de piel) que usan tipos no estándar pero se identifican por nombre/vendor.
     */
    private fun discoverTemperatureSensor(): Sensor? {
        val sm = sensorManager ?: return null

        val ambient = sm.getDefaultSensor(Sensor.TYPE_AMBIENT_TEMPERATURE)
        if (ambient != null) return ambient

        @Suppress("DEPRECATION")
        val deviceTemp = sm.getDefaultSensor(Sensor.TYPE_TEMPERATURE)
        if (deviceTemp != null) return deviceTemp

        // Fallback: barrer todos los sensores buscando uno de temperatura propietario.
        val keywords = listOf(
            "temperature", "temp", "therm", "skin", "infrared", "ir", "calor", "piel"
        )
        val candidates = sm.getSensorList(Sensor.TYPE_ALL).filter { sensor ->
            sensor.type >= Sensor.TYPE_DEVICE_PRIVATE_BASE ||
                keywords.any { it in sensor.name.lowercase() || it in sensor.vendor.lowercase() }
        }
        if (candidates.isEmpty()) return null

        val chosen = candidates.maxByOrNull { scoreTemperatureSensor(it) }
        Log.d("BIOGUARD", "Sensor de temperatura propietario detectado: ${chosen?.name} (vendor=${chosen?.vendor}, type=${chosen?.type})")
        return chosen
    }

    private fun scoreTemperatureSensor(sensor: Sensor): Int {
        val name = sensor.name.lowercase()
        val vendor = sensor.vendor.lowercase()
        var score = 0
        if (name.contains("skin") || name.contains("piel") || name.contains("infrared") || name.contains("ir")) score += 100
        if (name.contains("therm") || name.contains("temperature") || name.contains("temp")) score += 80
        if (name.contains("ambient") || name.contains("ambiente")) score += 40
        if (sensor.type >= Sensor.TYPE_DEVICE_PRIVATE_BASE) score += 20
        if (vendor.contains("samsung") || vendor.contains("maxim") || vendor.contains("ti")) score += 10
        return score
    }

    override fun stop() {
        tempListener?.let { listener ->
            sensorManager?.unregisterListener(listener)
        }
        tempListener = null
        _isAvailable = false
    }
}
