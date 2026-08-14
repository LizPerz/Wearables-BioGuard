package com.example.bioguard_wearos.data.sensor

import android.content.Context
import android.util.Log
import com.example.bioguard_wearos.domain.model.SensorData
import com.samsung.android.service.health.tracking.ConnectionListener
import com.samsung.android.service.health.tracking.HealthTracker
import com.samsung.android.service.health.tracking.HealthTrackerException
import com.samsung.android.service.health.tracking.HealthTrackingService
import com.samsung.android.service.health.tracking.data.DataPoint
import com.samsung.android.service.health.tracking.data.HealthTrackerType
import com.samsung.android.service.health.tracking.data.ValueKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Temperatura de piel REAL en Galaxy Watch5+ usando el Samsung Health Sensor SDK
 * (Health Sensor Service). Los sensores propietarios Samsung (Skin Temp / Thermistor)
 * NO se pueden registrar con SensorManager porque exigen com.samsung.permission.SSENSOR
 * (permiso de firma de Samsung inaccesible para apps de terceros).
 *
 * Esta clase conecta con HealthTrackingService y registra un tracker
 * [HealthTrackerType.SKIN_TEMPERATURE_CONTINUOUS]; cada DataPoint entrega
 * OBJECT_TEMPERATURE (°C) que se pinta al instante en [onTemperatureChanged].
 */
class SamsungTemperatureProvider(
    context: Context,
    private val onTemperatureChanged: (Float) -> Unit
) : TemperatureProvider {

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private var healthTrackingService: HealthTrackingService? = null
    private var skinTempTracker: HealthTracker? = null
    private var _isAvailable = false
    private var lastReportedTemp = 0f

    override val isAvailable: Boolean get() = _isAvailable

    override fun bindToHeartRate(dataFlow: StateFlow<SensorData>) {
        scope.launch { dataFlow.collect { } }
    }

    private val connectionListener = object : ConnectionListener {
        override fun onConnectionSuccess() {
            Log.i(TAG, "Conexion con Health Sensor Service exitosa")
            try {
                val supported = healthTrackingService
                    ?.getTrackingCapability()
                    ?.getSupportHealthTrackerTypes()
                    ?.toSet()
                    ?: emptySet()
                if (HealthTrackerType.SKIN_TEMPERATURE_CONTINUOUS !in supported) {
                    Log.w(TAG, "SKIN_TEMPERATURE_CONTINUOUS NO soportado por este reloj")
                    _isAvailable = false
                    return
                }
                val tracker = healthTrackingService?.getHealthTracker(
                    HealthTrackerType.SKIN_TEMPERATURE_CONTINUOUS
                )
                tracker?.setEventListener(skinTempListener)
                skinTempTracker = tracker
                _isAvailable = true
                Log.i(TAG, "Tracker SKIN_TEMPERATURE_CONTINUOUS registrado (temp de piel real)")
            } catch (e: Throwable) {
                _isAvailable = false
                Log.e(TAG, "Error configurando el tracker de temperatura: ${e.message}", e)
            }
        }

        override fun onConnectionEnded() {
            _isAvailable = false
            Log.w(TAG, "Conexion con Health Sensor Service finalizada")
        }

        override fun onConnectionFailed(exception: HealthTrackerException) {
            _isAvailable = false
            Log.e(
                TAG,
                "Fallo la conexion con Health Sensor Service (code=${exception.errorCode}): $exception"
            )
        }
    }

    private val skinTempListener = object : HealthTracker.TrackerEventListener {
        override fun onDataReceived(data: List<DataPoint>) {
            for (dp in data) {
                try {
                    val status = dp.getValue(ValueKey.SkinTemperatureSet.STATUS)
                    if (status == -1) {
                        Log.d(TAG, "Medicion de temperatura descartada por status invalido")
                        continue
                    }
                    val temp = dp.getValue(ValueKey.SkinTemperatureSet.OBJECT_TEMPERATURE) ?: continue
                    // Rango fisico plausible de piel (-10 a 80 °C).
                    if (temp <= 0f || temp < -10f || temp > 80f) continue
                    if (kotlin.math.abs(temp - lastReportedTemp) >= 0.1f) {
                        lastReportedTemp = temp
                        Log.i(
                            TAG,
                            "Temperatura de piel Samsung: ${String.format(Locale.US, "%.1f", temp)} C"
                        )
                        onTemperatureChanged(temp)
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Error parseando DataPoint de temperatura: ${e.message}")
                }
            }
        }

        override fun onFlushCompleted() = Unit

        override fun onError(error: HealthTracker.TrackerError) {
            if (error == HealthTracker.TrackerError.SDK_POLICY_ERROR) {
                Log.e(
                    TAG,
                    "SDK_POLICY_ERROR: activa el Developer Mode en el reloj " +
                        "(Ajustes > Apps > Health Sensor Service > toca el titulo ~10 veces > Developer mode)"
                )
            } else {
                Log.e(TAG, "TrackerError de temperatura: $error")
            }
        }
    }

    override fun start() {
        stop()
        try {
            healthTrackingService = HealthTrackingService(connectionListener, appContext)
            healthTrackingService?.connectService()
            Log.i(TAG, "Conectando con Health Sensor Service (Samsung)...")
        } catch (e: Throwable) {
            _isAvailable = false
            Log.w(TAG, "Samsung Health Sensor SDK no disponible en este dispositivo: ${e.message}")
        }
    }

    override fun stop() {
        try {
            skinTempTracker?.unsetEventListener()
        } catch (_: Throwable) {
        }
        skinTempTracker = null
        try {
            healthTrackingService?.disconnectService()
        } catch (_: Throwable) {
        }
        healthTrackingService = null
        _isAvailable = false
        lastReportedTemp = 0f
    }

    companion object {
        private const val TAG = "BIOGUARD_SAMSUNG_TEMP"
    }
}
