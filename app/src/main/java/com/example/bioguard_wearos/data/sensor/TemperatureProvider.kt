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
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

interface TemperatureProvider {
    fun start()
    fun stop()
    fun bindToHeartRate(dataFlow: StateFlow<SensorData>)
    val isAvailable: Boolean
}

/**
 * Captura de temperatura REAL usando exclusivamente la API genérica
 * [SensorManager] del SDK de Android (NO Health Services: esta métrica no
 * está soportada en Health Services).
 *
 * Técnica: se recorre [Sensor.TYPE_ALL] con getSensorList(Sensor.TYPE_ALL) y
 * se busca un sensor cuyo nombre o vendor contenga "temperature", "temp",
 * "therm", "skin", "piel", "infrared", "ir", "calor" o "samsung" (sensores
 * propietarios de fabricantes como Samsung BioActive / termómetro de piel).
 * Una vez localizado se registra un [SensorEventListener] sobre ese sensor y
 * cada lectura válida se pinta al instante en el flujo [onTemperatureChanged],
 * que alimenta la UI del reloj y el JSON de telemetría.
 */
class SensorManagerTemperatureProvider(
    context: Context,
    private val onTemperatureChanged: (Float) -> Unit
) : TemperatureProvider {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private var tempSensor: Sensor? = null
    private var tempListener: SensorEventListener? = null
    private var rediscoveryJob: Job? = null
    private var _isAvailable = false
    private var lastReportedTemp = 0f

    override val isAvailable: Boolean get() = _isAvailable

    private val temperatureKeywords = listOf(
        "temperature", "temp", "therm", "skin", "piel", "infrared", "ir", "calor", "samsung"
    )

    override fun bindToHeartRate(dataFlow: StateFlow<SensorData>) {
        // La temperatura se reporta tal cual la entrega el sensor, sin correlación
        // con el pulso. Se mantiene la suscripción para dejar el hook disponible.
        scope.launch { dataFlow.collect { } }
    }

    override fun start() {
        stop()
        val sm = sensorManager ?: run {
            Log.e(TAG, "SensorManager no disponible en este dispositivo")
            return
        }

        // Diagnóstico: inventario completo del hardware (logcat "BIOGUARD_TEMP").
        Log.d(TAG, "=== Inventario de sensores (Sensor.TYPE_ALL) ===")
        sm.getSensorList(Sensor.TYPE_ALL).forEach { s ->
            Log.d(TAG, "  [type=${s.type}] name='${s.name}' vendor='${s.vendor}'")
        }

        tempSensor = discoverTemperatureSensor()
        if (tempSensor != null) {
            registerTempListener(tempSensor!!)
        } else {
            _isAvailable = false
            Log.w(TAG, "Sin sensor de temperatura detectable; reintentando cada $REDISCOVER_INTERVAL_MS ms")
            startRediscoveryLoop()
        }
    }

    /**
     * El sensor puede no exponerse en el primer arranque (reloj en reposo, Samsung
     * Health reclamándolo). Se reintenta el escaneo periódicamente hasta localizarlo.
     */
    private fun startRediscoveryLoop() {
        rediscoveryJob?.cancel()
        rediscoveryJob = scope.launch {
            while (isActive) {
                delay(REDISCOVER_INTERVAL_MS)
                val found = discoverTemperatureSensor()
                if (found != null) {
                    Log.i(TAG, "Sensor de temperatura detectado en reintento: ${found.name}")
                    registerTempListener(found)
                    return@launch
                }
            }
        }
    }

    private fun registerTempListener(sensor: Sensor) {
        stopListener()
        tempSensor = sensor
        _isAvailable = true
        tempListener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                event ?: return
                val temp = event.values.firstOrNull() ?: return
                // Rango físico plausible (-10 a 80 °C) para descartar basura del vendor.
                if (temp < -10f || temp > 80f) {
                    Log.d(TAG, "Lectura de temperatura fuera de rango ignorada: $temp °C")
                    return
                }
                // El primer valor válido se pinta AL INSTANTE; después, solo cambios >= 0.1 °C.
                if (kotlin.math.abs(temp - lastReportedTemp) >= 0.1f) {
                    lastReportedTemp = temp
                    Log.i(TAG, "Temperatura real: ${String.format(java.util.Locale.US, "%.1f", temp)} °C (${event.sensor?.name}, accuracy=${event.accuracy})")
                    onTemperatureChanged(temp)
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
                Log.d(TAG, "Accuracy temperatura ${sensor?.name}: $accuracy")
            }
        }
        val registered = try {
            sensorManager?.registerListener(tempListener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
        } catch (e: Exception) {
            Log.e(TAG, "Error registrando listener de temperatura: ${e.message}")
            null
        }
        if (registered == true) {
            Log.i(TAG, "Sensor de temperatura registrado: ${sensor.name} (vendor=${sensor.vendor}, type=${sensor.type})")
        } else {
            _isAvailable = false
            Log.e(TAG, "No se pudo registrar el listener del sensor ${sensor.name}; reintentando...")
            startRediscoveryLoop()
        }
    }

    /**
     * Descubre el sensor de temperatura del hardware:
     *  1) Escaneo por palabras clave en nombre/vendor sobre Sensor.TYPE_ALL
     *     (sensores propietarios de fabricante: piel/IR/Samsung/termómetro).
     *  2) Fallback a los tipos estándar TYPE_AMBIENT_TEMPERATURE y TYPE_TEMPERATURE.
     */
    private fun discoverTemperatureSensor(): Sensor? {
        val sm = sensorManager ?: return null
        val allSensors = sm.getSensorList(Sensor.TYPE_ALL)

        // 1) Coincidencia por nombre/vendor (requerido para relojes Samsung).
        val keywordMatches = allSensors.filter { s ->
            val name = s.name.lowercase()
            val vendor = s.vendor.lowercase()
            temperatureKeywords.any { it in name || it in vendor }
        }
        if (keywordMatches.isNotEmpty()) {
            val chosen = keywordMatches.maxByOrNull { scoreSensor(it) }
            Log.d(TAG, "Sensor por keyword elegido: ${chosen?.name} (vendor=${chosen?.vendor}, type=${chosen?.type})")
            return chosen
        }

        // 2) Tipos estándar del SDK.
        sm.getDefaultSensor(Sensor.TYPE_AMBIENT_TEMPERATURE)?.let {
            Log.d(TAG, "Sensor estándar TYPE_AMBIENT_TEMPERATURE: ${it.name}")
            return it
        }
        @Suppress("DEPRECATION")
        sm.getDefaultSensor(Sensor.TYPE_TEMPERATURE)?.let {
            Log.d(TAG, "Sensor estándar TYPE_TEMPERATURE: ${it.name}")
            return it
        }

        return null
    }

    private fun scoreSensor(sensor: Sensor): Int {
        val name = sensor.name.lowercase()
        val vendor = sensor.vendor.lowercase()
        var score = 0
        if (name.contains("skin") || name.contains("piel") || name.contains("infrared") || name.contains("ir")) score += 100
        if (name.contains("therm") || name.contains("temperature") || name.contains("temp")) score += 80
        if (name.contains("ambient") || name.contains("ambiente")) score += 40
        if (vendor.contains("samsung") || vendor.contains("maxim") || vendor.contains("sensirion") || vendor.contains("ti")) score += 10
        return score
    }

    private fun stopListener() {
        tempListener?.let { listener ->
            sensorManager?.unregisterListener(listener)
        }
        tempListener = null
    }

    override fun stop() {
        stopListener()
        rediscoveryJob?.cancel()
        rediscoveryJob = null
        tempSensor = null
        _isAvailable = false
    }

    companion object {
        private const val TAG = "BIOGUARD_TEMP"
        private const val REDISCOVER_INTERVAL_MS = 10_000L
    }
}
