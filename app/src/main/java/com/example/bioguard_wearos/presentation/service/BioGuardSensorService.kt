package com.example.bioguard_wearos.presentation.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.bioguard_wearos.R
import com.example.bioguard_wearos.data.local.BioGuardPreferences
import com.example.bioguard_wearos.data.local.db.BiometricReadingEntity
import com.example.bioguard_wearos.domain.model.SensorData
import com.example.bioguard_wearos.domain.repository.BiometricReadingRepository
import com.example.bioguard_wearos.domain.repository.SensorDataRepository
import com.example.bioguard_wearos.domain.repository.SyncRepository
import com.example.bioguard_wearos.domain.risk.AlertManager
import com.example.bioguard_wearos.domain.risk.BiometricInput
import com.example.bioguard_wearos.domain.risk.BiometricRiskEngine
import com.example.bioguard_wearos.domain.risk.RiskLevel
import com.example.bioguard_wearos.presentation.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

@AndroidEntryPoint
class BioGuardSensorService : Service() {

    companion object {
        private const val TAG = "BIOGUARD_RD_BG"
        private const val CHANNEL_ID = "bioguard_sensor_channel"
        private const val ALERT_CHANNEL_ID = "bioguard_alert_channel"
        private const val NOTIFICATION_ID = 1001
        private const val ALERT_NOTIFICATION_ID = 1002
        private const val NOTIFICATION_MIN_INTERVAL_MS = 5000L
        private const val BPM_SIGNIFICANT_CHANGE = 5f
        private const val TEMP_SIGNIFICANT_CHANGE = 0.3f
        private const val GSR_SIGNIFICANT_CHANGE = 3f
        private const val RISK_EVAL_INTERVAL_MS = 3000L
        private const val DEFAULT_BMI = 25f
    }

    inner class LocalBinder : Binder() {
        fun getService(): BioGuardSensorService = this@BioGuardSensorService
    }

    private val binder = LocalBinder()
    private var wakeLock: PowerManager.WakeLock? = null
    private var notificationManager: NotificationManager? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Inject
    lateinit var sensorDataRepository: SensorDataRepository

    @Inject
    lateinit var alertManager: AlertManager

    @Inject
    lateinit var syncRepository: SyncRepository

    @Inject
    lateinit var readingRepository: BiometricReadingRepository

    @Inject
    lateinit var preferences: BioGuardPreferences

    private val riskEngine = BiometricRiskEngine()

    private val _currentData = MutableStateFlow(SensorData())
    private val currentData: StateFlow<SensorData> = _currentData.asStateFlow()
    private var lastNotificationTimestamp = 0L
    private var lastNotifiedBpm = 0f
    private var lastNotifiedTemp = 0f
    private var lastNotifiedGsr = 0f
    private var lastRiskEvalTimestamp = 0L
    private var cachedBmi = DEFAULT_BMI

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Servicio creado")

        notificationManager = getSystemService(NOTIFICATION_SERVICE) as? NotificationManager

        createNotificationChannel()
        createAlertNotificationChannel()
        acquireWakeLock()
        loadPatientBmi()
        observeSensorData()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Servicio iniciado")

        val hasBodySensors = ContextCompat.checkSelfPermission(
            this, android.Manifest.permission.BODY_SENSORS
        ) == PackageManager.PERMISSION_GRANTED

        val hasHeartRate = try {
            ContextCompat.checkSelfPermission(
                this, "android.permission.health.READ_HEART_RATE"
            ) == PackageManager.PERMISSION_GRANTED
        } catch (e: Exception) { false }

        if (!hasBodySensors && !hasHeartRate) {
            Log.w(TAG, "Sin permisos de sensores. BODY_SENSORS=$hasBodySensors, HR=$hasHeartRate")
        }

        try {
            startForeground(NOTIFICATION_ID, buildNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH)
            Log.d(TAG, "startForeground exitoso (tipo HEALTH)")
        } catch (e: Exception) {
            Log.e(TAG, "startForeground falló [${e::class.simpleName}]: ${e.message}")
            Log.w(TAG, "El servicio será detenido por el sistema sin foreground")
        }

        sensorDataRepository.startSensors()

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Servicio destruido")
        sensorDataRepository.stopSensors()
        releaseWakeLock()
        serviceScope.cancel()
    }

    private fun loadPatientBmi() {
        serviceScope.launch {
            try {
                val result = syncRepository.obtenerPaciente()
                result.onSuccess { (_, peso, estatura) ->
                    if (estatura > 0f) {
                        val estaturaM = estatura / 100f
                        cachedBmi = peso / (estaturaM * estaturaM)
                        Log.d(TAG, "BMI del paciente: $cachedBmi (peso=$peso, estatura=$estatura)")
                    }
                }.onFailure {
                    Log.w(TAG, "No se pudo obtener perfil del paciente, usando BMI default=$DEFAULT_BMI")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error cargando BMI: ${e.message}")
            }
        }
    }

    private fun observeSensorData() {
        serviceScope.launch {
            sensorDataRepository.sensorData.collect { data ->
                _currentData.value = data
                updateNotification()
                evaluateRiskIfNeeded(data)
                saveReading(data)
            }
        }
    }

    private fun evaluateRiskIfNeeded(data: SensorData) {
        val now = System.currentTimeMillis()
        if (now - lastRiskEvalTimestamp < RISK_EVAL_INTERVAL_MS) return
        if (data.bpm <= 0f) return

        lastRiskEvalTimestamp = now

        val input = BiometricInput(
            bpm = data.bpm,
            temperature = data.temperature,
            gsr = data.gsr,
            bmi = cachedBmi
        )

        val assessment = riskEngine.evaluate(input)
        Log.d(TAG, "Evaluación: nivel=${assessment.level.name}, " +
                "P(Pico)=${String.format(Locale.US, "%.3f", assessment.probability)}, " +
                "fuente=${assessment.triggerSource.name}")

        when (assessment.level) {
            RiskLevel.CRITICAL_HIGH -> {
                Log.w(TAG, ">>> CRÍTICO ALTO detectado, activando alerta <<<")
                showCriticalNotification(data)
                alertManager.triggerAlert(assessment)
                serviceScope.launch {
                    syncRepository.enviarEvento(
                        bpm = data.bpm, temperatura = data.temperature, gsr = data.gsr,
                        nivelRiesgo = RiskLevel.CRITICAL_HIGH.name,
                        tipoEvento = "CRITICAL_ALERT",
                        descripcion = "Motor F3: P(Pico)=${assessment.probability}"
                    )
                    syncRepository.enviarAlerta(
                        tipoAlerta = "CRITICAL_HYPOGLYCEMIA",
                        mensaje = "Pico crítico: BPM=${data.bpm}, Temp=${data.temperature}°C, GSR=${data.gsr}µS",
                        nivelRiesgo = RiskLevel.CRITICAL_HIGH.name,
                        bpm = data.bpm, temperatura = data.temperature, gsr = data.gsr
                    )
                }
            }
            RiskLevel.MODERATE_HIGH -> {
                Log.w(TAG, ">>> MODERADO ALTO detectado, enviando notificación preventiva <<<")
                serviceScope.launch {
                    syncRepository.enviarEvento(
                        bpm = data.bpm, temperatura = data.temperature, gsr = data.gsr,
                        nivelRiesgo = RiskLevel.MODERATE_HIGH.name,
                        tipoEvento = "MODERATE_ALERT",
                        descripcion = "Hiperglucemia severa detectada"
                    )
                }
            }
            RiskLevel.OPTIMAL -> {
                Log.d(TAG, "Estado óptimo, almacenamiento silencioso")
            }
        }
    }

    private fun saveReading(data: SensorData) {
        serviceScope.launch {
            try {
                val input = BiometricInput(
                    bpm = data.bpm, temperature = data.temperature,
                    gsr = data.gsr, bmi = cachedBmi
                )
                val level = riskEngine.evaluateMatrix(input)

                readingRepository.saveReading(
                    BiometricReadingEntity(
                        bpm = data.bpm,
                        temperature = data.temperature,
                        gsr = data.gsr,
                        bmi = cachedBmi,
                        riskLevel = level
                    )
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error guardando lectura: ${e.message}")
            }
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "BioGuard Sensores",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Lectura continua de sensores biométricos"
            setShowBadge(false)
        }
        notificationManager?.createNotificationChannel(channel)
    }

    private fun createAlertNotificationChannel() {
        val channel = NotificationChannel(
            ALERT_CHANNEL_ID,
            "BioGuard Alertas",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Alertas críticas de salud"
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 500, 200, 500, 200, 500)
            setBypassDnd(true)
        }
        notificationManager?.createNotificationChannel(channel)
    }

    private fun showCriticalNotification(data: SensorData) {
        val notification = NotificationCompat.Builder(this, ALERT_CHANNEL_ID)
            .setContentTitle("ALERTA CRITICA")
            .setContentText("Pico critico detectado. BPM=${data.bpm}")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setVibrate(longArrayOf(0, 500, 200, 500, 200, 500))
            .setContentIntent(createContentIntent())
            .build()

        notificationManager?.notify(ALERT_NOTIFICATION_ID, notification)
    }

    private fun acquireWakeLock() {
        try {
            val powerManager = getSystemService(POWER_SERVICE) as? PowerManager
            wakeLock = powerManager?.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "BioGuard::SensorWakeLock"
            )?.apply {
                acquire(60 * 60 * 1000L)
            }
            Log.d(TAG, "WakeLock adquirido correctamente")
        } catch (e: SecurityException) {
            Log.e(TAG, "No se pudo adquirir WakeLock: ${e.message}")
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let {
            if (it.isHeld) it.release()
        }
        wakeLock = null
    }

    private fun updateNotification() {
        val data = _currentData.value
        val now = System.currentTimeMillis()
        val timeSinceLast = now - lastNotificationTimestamp

        val bpmChanged = kotlin.math.abs(data.bpm - lastNotifiedBpm) >= BPM_SIGNIFICANT_CHANGE
        val tempChanged = kotlin.math.abs(data.temperature - lastNotifiedTemp) >= TEMP_SIGNIFICANT_CHANGE
        val gsrChanged = kotlin.math.abs(data.gsr - lastNotifiedGsr) >= GSR_SIGNIFICANT_CHANGE
        val significantChange = bpmChanged || tempChanged || gsrChanged

        if (timeSinceLast < NOTIFICATION_MIN_INTERVAL_MS && !significantChange) {
            return
        }

        lastNotificationTimestamp = now
        lastNotifiedBpm = data.bpm
        lastNotifiedTemp = data.temperature
        lastNotifiedGsr = data.gsr

        val bpm = String.format(Locale.US, "%.0f", data.bpm)
        val temp = String.format(Locale.US, "%.1f", data.temperature)
        val gsr = String.format(Locale.US, "%.0f", data.gsr)

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("BioGuard Activo")
            .setContentText("BPM: $bpm | Temp: ${temp}\u00b0C | Estr\u00e9s: ${gsr}\u00b5S")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(createContentIntent())
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()

        notificationManager?.notify(NOTIFICATION_ID, notification)
    }

    private fun buildNotification(): Notification {
        val data = _currentData.value
        val bpm = String.format(Locale.US, "%.0f", data.bpm)
        val temp = String.format(Locale.US, "%.1f", data.temperature)
        val gsr = String.format(Locale.US, "%.0f", data.gsr)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("BioGuard Activo")
            .setContentText("BPM: $bpm | Temp: ${temp}\u00b0C | Estr\u00e9s: ${gsr}\u00b5S")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(createContentIntent())
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun createContentIntent(): PendingIntent {
        return PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
