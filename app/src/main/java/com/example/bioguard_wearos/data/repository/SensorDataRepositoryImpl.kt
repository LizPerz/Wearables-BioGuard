package com.example.bioguard_wearos.data.repository

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import androidx.health.services.client.HealthServices
import androidx.health.services.client.MeasureCallback
import androidx.health.services.client.data.Availability
import androidx.health.services.client.data.DataPointContainer
import androidx.health.services.client.data.DataType
import androidx.health.services.client.data.DeltaDataType
import androidx.health.services.client.data.MeasureCapabilities
import com.example.bioguard_wearos.data.hrv.HrvCalculator
import com.example.bioguard_wearos.data.hrv.StressMapper
import com.example.bioguard_wearos.data.local.TelemetrySaveScheduler
import com.example.bioguard_wearos.data.sensor.SensorManagerTemperatureProvider
import com.example.bioguard_wearos.data.sensor.TemperatureProvider
import com.example.bioguard_wearos.domain.model.SensorAvailability
import com.example.bioguard_wearos.domain.model.SensorData
import com.example.bioguard_wearos.domain.repository.BiometricReadingRepository
import com.example.bioguard_wearos.domain.repository.SensorDataRepository
import com.example.bioguard_wearos.domain.risk.RiskAssessment
import com.example.bioguard_wearos.domain.risk.RiskThresholdController
import com.google.common.util.concurrent.FutureCallback
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SensorDataRepositoryImpl(
    private val context: Context,
    private val readingRepository: BiometricReadingRepository? = null,
    private val riskThresholdController: RiskThresholdController = RiskThresholdController()
) : SensorDataRepository, SensorEventListener {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private var heartRateSensor: Sensor? = null

    private val dataLock = Any()

    private val _sensorData = MutableStateFlow(SensorData())
    override val sensorData: StateFlow<SensorData> = _sensorData.asStateFlow()

    private val _sensorAvailability = MutableStateFlow(SensorAvailability())
    override val sensorAvailability: StateFlow<SensorAvailability> = _sensorAvailability.asStateFlow()

    private val hrvCalculator = HrvCalculator(maxBufferSize = 60)
    private val stressMapper = StressMapper()

    private val telemetryScheduler: TelemetrySaveScheduler? = readingRepository?.let {
        TelemetrySaveScheduler(it, _sensorData)
    }

    private val temperatureProvider: TemperatureProvider = SensorManagerTemperatureProvider(context) { _ -> }

    init {
        temperatureProvider.bindToHeartRate(_sensorData)
    }

    @Volatile private var heartRateStarted = false
    @Volatile private var hasRealBpmData = false

    private val measureClient = try {
        HealthServices.getClient(context).measureClient
    } catch (e: Exception) {
        Log.e("BIOGUARD", "Error al crear HealthServicesClient", e)
        null
    }

    private val heartRateCallback = object : MeasureCallback {
        override fun onRegistered() {
            heartRateStarted = true
            Log.d("BIOGUARD", "Health Services callback registrado correctamente")
        }

        override fun onRegistrationFailed(throwable: Throwable) {
            heartRateStarted = false
            _sensorAvailability.value = _sensorAvailability.value.copy(
                heartRateAvailable = false,
                statusMessage = "No se pudo iniciar frecuencia cardiaca"
            )
            Log.w("BIOGUARD", "Health Services falló el registro, intentando SensorManager fallback")
            startHeartRateSensorManager()
        }

        override fun onAvailabilityChanged(dataType: DeltaDataType<*, *>, availability: Availability) {
            Log.d("BIOGUARD", "Disponibilidad cambiada: $dataType -> $availability")
            if (dataType == DataType.HEART_RATE_BPM) {
                val offBody = availability.toString().contains("UNAVAILABLE_DEVICE_OFF_BODY", ignoreCase = true)
                _sensorAvailability.value = _sensorAvailability.value.copy(
                    heartRateAvailable = !offBody && hasRealBpmData,
                    heartRateOffBody = offBody,
                    statusMessage = if (offBody) "Coloca el reloj en la muneca para medir pulso" else null
                )
            }
        }

        override fun onDataReceived(data: DataPointContainer) {
            val dataPoints = data.getData(DataType.HEART_RATE_BPM)
            for (dataPoint in dataPoints) {
                val bpm = dataPoint.value.toFloat()
                if (bpm > 0f) {
                    hasRealBpmData = true
                    processHeartRate(bpm)
                    _sensorAvailability.value = _sensorAvailability.value.copy(
                        heartRateAvailable = true,
                        heartRateOffBody = false,
                        statusMessage = null
                    )
                    Log.d("BIOGUARD", "Health Services BPM: $bpm")
                }
            }
        }
    }

    override fun startSensors() {
        Log.d("BIOGUARD", "=== startSensors() llamado ===")
        Log.d("BIOGUARD", "measureClient: ${if (measureClient != null) "disponible" else "NULL"}")
        Log.d("BIOGUARD", "sensorManager: ${if (sensorManager != null) "disponible" else "NULL"}")
        Log.d("BIOGUARD", "heartRateSensor HW: ${sensorManager?.getDefaultSensor(Sensor.TYPE_HEART_RATE)?.name ?: "NO DISPONIBLE"}")
        startHeartRateMeasure()
        temperatureProvider.start()
        startHrvUpdater()
        startPeriodicSave()
    }

    override fun stopSensors() {
        stopHeartRateMeasure()
        temperatureProvider.stop()
        heartRateStarted = false
        telemetryScheduler?.stop()
    }

    private fun startPeriodicSave() {
        telemetryScheduler?.start(scope)
    }

    private fun processHeartRate(bpm: Float) {
        hrvCalculator.addIbi(bpm)
        val rmssd = hrvCalculator.computeRmssd()
        val sdnn = hrvCalculator.computeSdnn()
        val stressUs = stressMapper.mapToStressUs(rmssd)
        val label = stressMapper.getStressLabel(stressUs)

        val temp = _sensorData.value.temperature
        val riskAssessment = RiskAssessment.fromBiometrics(
            bpm = bpm,
            temp = temp,
            gsr = 0f,
            thresholds = riskThresholdController.current
        )
        if (riskAssessment.level.isElevated) {
            Log.w("BIOGUARD_RISK", "Evaluación de riesgo local elevada: ${riskAssessment.level.label} (Prob=${riskAssessment.probability})")
        }

        synchronized(dataLock) {
            _sensorData.value = _sensorData.value.copy(
                bpm = bpm,
                rmssd = rmssd,
                sdnn = sdnn,
                stressEstimate = stressUs,
                stressLabel = label
            )
        }
    }

    private fun startHrvUpdater() {
        scope.launch {
            delay(3000)
            Log.d("BIOGUARD", "Actualizador HRV iniciado")
            while (isActive) {
                val currentBpm = _sensorData.value.bpm
                if (currentBpm > 0f) {
                    val rmssd = hrvCalculator.computeRmssd()
                    val sdnn = hrvCalculator.computeSdnn()
                    val stressUs = stressMapper.mapToStressUs(rmssd)
                    val label = stressMapper.getStressLabel(stressUs)

                    synchronized(dataLock) {
                        _sensorData.value = _sensorData.value.copy(
                            rmssd = rmssd,
                            sdnn = sdnn,
                            stressEstimate = stressUs,
                            stressLabel = label
                        )
                    }
                }
                delay(1000)
            }
        }
    }

    private fun startHeartRateMeasure() {
        if (heartRateStarted) {
            Log.d("BIOGUARD", "HR ya iniciado, ignorando")
            return
        }

        if (measureClient == null) {
            Log.w("BIOGUARD", "MeasureClient null, usando SensorManager fallback")
            startHeartRateSensorManager()
            return
        }

        Log.d("BIOGUARD", "Obteniendo capacidades Health Services...")
        val future = measureClient.getCapabilitiesAsync()
        Futures.addCallback(future, object : FutureCallback<MeasureCapabilities> {
            override fun onSuccess(capabilities: MeasureCapabilities) {
                val supportedMeasure = capabilities.supportedDataTypesMeasure
                Log.d("BIOGUARD", "Capacidades Health Services: $supportedMeasure")
                Log.d("BIOGUARD", "HEART_RATE_BPM soportado: ${DataType.HEART_RATE_BPM in supportedMeasure}")

                if (DataType.HEART_RATE_BPM in supportedMeasure) {
                    try {
                        Log.d("BIOGUARD", "Registrando MeasureCallback para HEART_RATE_BPM...")
                        measureClient.registerMeasureCallback(DataType.HEART_RATE_BPM, heartRateCallback)
                        Log.d("BIOGUARD", "registerMeasureCallback invocado OK")
                    } catch (e: Exception) {
                        heartRateStarted = false
                        Log.e("BIOGUARD", "Error registrando MeasureCallback: ${e.message}", e)
                        startHeartRateSensorManager()
                    }
                } else {
                    Log.w("BIOGUARD", "HEART_RATE_BPM NO soportado en measure")
                    startHeartRateSensorManager()
                }
            }

            override fun onFailure(t: Throwable) {
                Log.e("BIOGUARD", "Error al obtener capacidades: ${t.message}", t)
                startHeartRateSensorManager()
            }
        }, MoreExecutors.directExecutor())
    }

    private fun startHeartRateSensorManager() {
        if (heartRateStarted) {
            Log.d("BIOGUARD", "SensorManager HR ya iniciado, ignorando")
            return
        }

        Log.d("BIOGUARD", "Intentando SensorManager fallback...")
        heartRateSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_HEART_RATE)
        heartRateSensor?.let {
            Log.d("BIOGUARD", "Sensor HR encontrado: ${it.name}, vendor: ${it.vendor}")
            val registered = sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
            if (registered == true) {
                heartRateStarted = true
                Log.d("BIOGUARD", "SensorManager TYPE_HEART_RATE registrado OK")
            } else {
                Log.e("BIOGUARD", "Fallo al registrar SensorManager TYPE_HEART_RATE (returned false)")
            }
        } ?: run {
            Log.e("BIOGUARD", "Sensor TYPE_HEART_RATE NO EXISTE en este dispositivo")
            Log.e("BIOGUARD", "Sensores disponibles: ${sensorManager?.getSensorList(Sensor.TYPE_ALL)?.map { "${it.name}(${it.type})" }?.joinToString()}")
        }
    }

    private fun stopHeartRateMeasure() {
        if (heartRateStarted && heartRateSensor != null) {
            sensorManager?.unregisterListener(this, heartRateSensor)
            Log.d("BIOGUARD", "SensorManager heart rate desregistrado")
        }
        measureClient?.let { client ->
            try {
                val future = client.unregisterMeasureCallbackAsync(DataType.HEART_RATE_BPM, heartRateCallback)
                Futures.addCallback(future, object : FutureCallback<Void> {
                    override fun onSuccess(result: Void?) {
                        Log.d("BIOGUARD", "Health Services MeasureCallback desregistrado")
                    }

                    override fun onFailure(t: Throwable) {
                        Log.e("BIOGUARD", "Error al desregistrar Health Services MeasureCallback", t)
                    }
                }, MoreExecutors.directExecutor())
            } catch (e: Exception) {
                Log.e("BIOGUARD", "Error al desregistrar Health Services: ${e.message}")
            }
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        event ?: return
        when (event.sensor?.type) {
            Sensor.TYPE_HEART_RATE -> {
                if (event.values.isNotEmpty() && event.accuracy > 0) {
                    val bpm = event.values[0]
                    if (bpm > 0f) {
                        hasRealBpmData = true
                        processHeartRate(bpm)
                        _sensorAvailability.value = _sensorAvailability.value.copy(
                            heartRateAvailable = true,
                            heartRateOffBody = false,
                            statusMessage = null
                        )
                        Log.d("BIOGUARD", "SensorManager BPM: $bpm (accuracy=${event.accuracy})")
                    }
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        Log.d("BIOGUARD", "Accuracy changed: ${sensor?.name} -> $accuracy")
    }
}
