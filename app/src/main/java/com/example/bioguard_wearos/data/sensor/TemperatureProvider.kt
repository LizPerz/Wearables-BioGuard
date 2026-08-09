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

    override val isAvailable: Boolean get() = _isAvailable

    override fun bindToHeartRate(dataFlow: StateFlow<SensorData>) {
        scope.launch {
            dataFlow.collect { data ->
                if (data.bpm > 0f) lastBpm = data.bpm
            }
        }
    }

    override fun start() {
        _isAvailable = false
        // Ambient temperature is not body temperature and must not feed clinical alerts.
        Log.i("BIOGUARD", "Body temperature unavailable; ambient sensor is intentionally not used")
    }

    override fun stop() {
        tempListener?.let { listener ->
            sensorManager?.unregisterListener(listener)
        }
        tempListener = null
        _isAvailable = false
    }
}
