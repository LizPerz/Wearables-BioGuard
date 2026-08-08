package com.example.bioguard_wearos.presentation.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.BatteryManager
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
import com.example.bioguard_wearos.domain.risk.RiskLevel
import com.example.bioguard_wearos.domain.risk.RiskAssessment
import com.example.bioguard_wearos.domain.risk.RiskThresholdController
import com.example.bioguard_wearos.domain.risk.RiskThresholds
import com.example.bioguard_wearos.domain.risk.TriggerSource
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import org.json.JSONObject
import com.example.bioguard_wearos.presentation.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

@AndroidEntryPoint
class BioGuardSensorService : Service(), MessageClient.OnMessageReceivedListener {

    companion object {
        private const val TAG = "BIOGUARD_RD_BG"
        private const val CHANNEL_ID = "bioguard_sensor_channel"
        private const val ALERT_CHANNEL_ID = "bioguard_alert_channel"
        private const val NOTIFICATION_ID = 1001
        private const val ALERT_NOTIFICATION_ID = 1002
        private const val BATTERY_NOTIFICATION_ID = 1003
        private const val NOTIFICATION_MIN_INTERVAL_MS = 5000L
        private const val BPM_SIGNIFICANT_CHANGE = 5f
        private const val TEMP_SIGNIFICANT_CHANGE = 0.3f
        private const val GSR_SIGNIFICANT_CHANGE = 3f
        private const val RISK_EVAL_INTERVAL_MS = 3000L
        private const val HEARTBEAT_INTERVAL_MS = 5 * 60 * 1000L
        private const val FLUSH_INTERVAL_MS = 60_000L
        private const val BATTERY_CHECK_INTERVAL_MS = 5 * 60 * 1000L
        private val BATTERY_THRESHOLDS = intArrayOf(30, 15, 5)
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

    @Inject
    lateinit var riskThresholdController: RiskThresholdController

    @Inject
    lateinit var dutyCycleManager: com.example.bioguard_wearos.data.energy.DutyCycleManager

    private val _currentData = MutableStateFlow(SensorData())
    private val currentData: StateFlow<SensorData> = _currentData.asStateFlow()
    private var lastNotificationTimestamp = 0L
    private var lastNotifiedBpm = 0f
    private var lastNotifiedTemp = 0f
    private var lastNotifiedGsr = 0f
    private var lastRiskEvalTimestamp = 0L
    private var cachedBmi = DEFAULT_BMI
    private val batteryThresholdsNotified = mutableSetOf<Int>()
    private var criticalEpisodeActive = false
    private var lastRiskAssessment: RiskAssessment? = null

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Servicio creado")

        notificationManager = getSystemService(NOTIFICATION_SERVICE) as? NotificationManager

        createNotificationChannel()
        createAlertNotificationChannel()
        acquireWakeLock()
        loadPatientBmi()
        loadRiskThresholds()
        observeSensorData()
        startRiskEvaluationLoop()
        startBatteryMonitor()
        startHeartbeatLoop()
        
        try {
            Wearable.getMessageClient(this).addListener(this)
            Log.d(TAG, "MessageClient listener registrado")
        } catch (e: Exception) {
            Log.e(TAG, "Error registrando MessageClient listener: ${e.message}")
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Servicio iniciado")

        val hasBodySensors = ContextCompat.checkSelfPermission(
            this, android.Manifest.permission.BODY_SENSORS
        ) == PackageManager.PERMISSION_GRANTED

        val hasHeartRate = hasHealthHeartRatePermission()

        if (!hasBodySensors || !hasHeartRate) {
            Log.w(TAG, "Sin permisos requeridos de sensores. BODY_SENSORS=$hasBodySensors, HR=$hasHeartRate")
            stopSelf()
            return START_NOT_STICKY
        }

        try {
            startForeground(NOTIFICATION_ID, buildNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH)
            Log.d(TAG, "startForeground exitoso (tipo HEALTH)")
        } catch (e: Exception) {
            Log.e(TAG, "startForeground falló [${e::class.simpleName}]: ${e.message}")
            Log.w(TAG, "El servicio será detenido por el sistema sin foreground")
        }

        serviceScope.launch {
            sensorDataRepository.startSensors()
        }

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Servicio destruido")
        try {
            Wearable.getMessageClient(this).removeListener(this)
            Log.d(TAG, "MessageClient listener removido")
        } catch (e: Exception) {
            Log.w(TAG, "Error removiendo MessageClient listener: ${e.message}")
        }
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

    private fun loadRiskThresholds() {
        serviceScope.launch {
            try {
                val thresholds = preferences.riskThresholds.first()
                riskThresholdController.update(thresholds)
                Log.d(TAG, "Umbrales de riesgo cargados: $thresholds")
            } catch (e: Exception) {
                Log.w(TAG, "Error cargando umbrales de riesgo: ${e.message}")
            }
        }
    }

    private fun startRiskEvaluationLoop() {
        serviceScope.launch {
            while (true) {
                evaluateRisk()
                delay(RISK_EVAL_INTERVAL_MS)
            }
        }
    }

    private fun evaluateRisk() {
        val data = _currentData.value
        if (data.bpm <= 0f) return

        val assessment = RiskAssessment.fromBiometrics(
            bpm = data.bpm,
            temp = data.temperature,
            gsr = data.gsr,
            thresholds = riskThresholdController.current
        )
        lastRiskAssessment = assessment

        when (assessment.level) {
            RiskLevel.CRITICAL_HIGH -> {
                if (!criticalEpisodeActive && !alertManager.alertState.value.isActive) {
                    criticalEpisodeActive = true
                    Log.w(TAG, "Riesgo local CRÍTICO detectado (umbrales: ${riskThresholdController.current})")
                    showCriticalNotification(data)
                    alertManager.triggerAlert(assessment)
                }
            }
            RiskLevel.MODERATE_HIGH -> {
                if (criticalEpisodeActive && !alertManager.alertState.value.isActive) {
                    Log.d(TAG, "Riesgo local moderado-alto detectado: ${assessment.level.label}")
                }
            }
            else -> {
                if (criticalEpisodeActive && !alertManager.alertState.value.isActive) {
                    Log.d(TAG, "Riesgo local normalizado, episodio crítico finalizado")
                }
                criticalEpisodeActive = false
            }
        }
    }

    private fun observeSensorData() {
        serviceScope.launch {
            sensorDataRepository.sensorData.collect { data ->
                _currentData.value = data
                updateNotification()
                saveReading(data)
            }
        }
    }

    private fun saveReading(data: SensorData) {
        if (data.bpm <= 0f) {
            Log.d(TAG, "Lectura omitida: sin BPM real (posible reloj fuera de la muneca)")
            return
        }
        serviceScope.launch {
            try {
                readingRepository.saveReading(
                    BiometricReadingEntity(
                        bpm = data.bpm,
                        temperature = data.temperature,
                        gsr = data.gsr,
                        bmi = cachedBmi,
                        riskLevel = lastRiskAssessment?.level ?: RiskLevel.OPTIMAL
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
        Log.d(TAG, "Utilizando gestión de energía ultra eficiente por DutyCycling (sin WakeLock sostenido)")
        dutyCycleManager.runWithMicroWakeLock(1000L) {
            Log.d(TAG, "Micro-WakeLock ejecutado para arranque inicial de sensores")
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

    private fun startBatteryMonitor() {
        serviceScope.launch {
            while (true) {
                val level = obtenerBateria()
                if (level != null) {
                    val threshold = BATTERY_THRESHOLDS.firstOrNull { level <= it }
                    if (threshold != null && batteryThresholdsNotified.add(threshold)) {
                        mostrarAlertaBateria(level, threshold)
                    }
                    if (level > (BATTERY_THRESHOLDS.maxOrNull() ?: 30)) {
                        batteryThresholdsNotified.clear()
                    }
                }
                delay(BATTERY_CHECK_INTERVAL_MS)
            }
        }
    }

    private fun obtenerBateria(): Int? {
        return try {
            val intent = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
            if (level >= 0 && scale > 0) (level * 100 / scale) else null
        } catch (e: Exception) {
            Log.w(TAG, "Error leyendo batería: ${e.message}")
            null
        }
    }

    private fun mostrarAlertaBateria(level: Int, threshold: Int) {
        val notification = NotificationCompat.Builder(this, ALERT_CHANNEL_ID)
            .setContentTitle("Batería baja")
            .setContentText("Batería del reloj al $level%. Conéctalo a cargar.")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_SYSTEM)
            .setAutoCancel(true)
            .setContentIntent(createContentIntent())
            .build()

        notificationManager?.notify(BATTERY_NOTIFICATION_ID, notification)
        Log.w(TAG, "Alerta de batería baja: $level% (umbral $threshold)")
    }

    private fun startHeartbeatLoop() {
        serviceScope.launch {
            while (true) {
                enviarHeartbeat()
                delay(HEARTBEAT_INTERVAL_MS)
            }
        }
    }

    private fun enviarHeartbeat() {
        val data = _currentData.value
        val sensores = buildList {
            if (data.bpm > 0f) add("pulso")
            if (data.temperature > 0f) add("temperatura")
            if (data.gsr > 0f) add("sudoracion")
            if (data.rmssd > 0f || data.sdnn > 0f) add("hrv")
        }
        val bateria = obtenerBateria() ?: -1
        serviceScope.launch {
            syncRepository.enviarHeartbeat(bateria, sensores)
                .onSuccess { Log.d(TAG, "Heartbeat enviado: batería=$bateria%, sensores=$sensores") }
                .onFailure { Log.w(TAG, "Heartbeat falló: ${it.message}") }
        }
    }

    private fun hasHealthHeartRatePermission(): Boolean {
        if (android.os.Build.VERSION.SDK_INT < 36) return true
        return try {
            ContextCompat.checkSelfPermission(
                this, "android.permission.health.READ_HEART_RATE"
            ) == PackageManager.PERMISSION_GRANTED
        } catch (_: Exception) {
            false
        }
    }

    override fun onMessageReceived(event: MessageEvent) {
        Log.d(TAG, "Mensaje recibido de Wearable Data Layer: path=${event.path}")
        when (event.path) {
            "/commands/alert" -> {
                try {
                    val json = String(event.data, Charsets.UTF_8)
                    Log.d(TAG, "Comando de alerta recibido: $json")
                    val command = JSONObject(json)
                    val bpm = command.optDouble("bpm", 0.0).toFloat()
                    val temp = command.optDouble("temperatura", 0.0).toFloat()
                    val gsr = command.optDouble("gsr", 0.0).toFloat()
                    val probability = command.optDouble("probability", 0.0).toFloat()

                    val assessment = RiskAssessment(
                        level = RiskLevel.CRITICAL_HIGH,
                        probability = probability,
                        triggerSource = TriggerSource.SIGMOID,
                        input = BiometricInput(bpm = bpm, temperature = temp, gsr = gsr, bmi = cachedBmi)
                    )
                    showCriticalNotification(SensorData(bpm = bpm, temperature = temp, gsr = gsr))
                    alertManager.triggerAlert(assessment)
                } catch (e: Exception) {
                    Log.e(TAG, "Error procesando comando de alerta del teléfono", e)
                }
            }
            "/commands/dismiss" -> {
                Log.d(TAG, "Comando de descarte recibido")
                alertManager.dismissAlert()
            }
            "/bioguard/ack" -> {
                Log.d(TAG, "ACK de sincronización recibido de la app móvil")
                serviceScope.launch {
                    try {
                        val ack = JSONObject(String(event.data, Charsets.UTF_8))
                        val ackPath = ack.optString("ackPath")
                        val readingId = ackPath.removePrefix("/bioguard/telemetry/").toLongOrNull()
                        if (readingId != null) {
                            readingRepository.markAsSynced(listOf(readingId))
                            Log.d(TAG, "Lectura $readingId marcada como sincronizada tras ACK del movil")
                        } else {
                            Log.d(TAG, "ACK recibido para ruta no persistente: $ackPath")
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Error procesando ACK en el reloj: ${e.message}")
                    }
                }
            }
            "/commands/sampling_rate" -> {
                try {
                    val json = String(event.data, Charsets.UTF_8)
                    val command = JSONObject(json)
                    val intervalSeconds = command.optInt("intervalSeconds", 60)
                    Log.d(TAG, "Comando de intervalo de muestreo recibido del móvil: $intervalSeconds segundos")
                } catch (e: Exception) {
                    Log.e(TAG, "Error procesando comando de muestreo del móvil", e)
                }
            }
            "/commands/sensor_toggle" -> {
                try {
                    val json = String(event.data, Charsets.UTF_8)
                    val command = JSONObject(json)
                    val sensor = command.optString("sensor", "")
                    val enabled = command.optBoolean("enabled", true)
                    Log.d(TAG, "Comando de conmutación de sensor recibido del móvil: sensor=$sensor, enabled=$enabled")
                } catch (e: Exception) {
                    Log.e(TAG, "Error procesando comando de conmutación de sensor del móvil", e)
                }
            }
            "/risk-thresholds" -> {
                try {
                    val json = String(event.data, Charsets.UTF_8)
                    Log.d(TAG, "Umbrales de riesgo recibidos del teléfono: $json")
                    val command = JSONObject(json)
                    val thresholds = RiskThresholds(
                        criticalBpmHigh = command.optDouble("criticalBpmHigh", RiskThresholds.DEFAULT.criticalBpmHigh.toDouble()).toFloat(),
                        criticalBpmLow = command.optDouble("criticalBpmLow", RiskThresholds.DEFAULT.criticalBpmLow.toDouble()).toFloat(),
                        criticalTemp = command.optDouble("criticalTemp", RiskThresholds.DEFAULT.criticalTemp.toDouble()).toFloat(),
                        moderateBpm = command.optDouble("moderateBpm", RiskThresholds.DEFAULT.moderateBpm.toDouble()).toFloat(),
                        moderateTemp = command.optDouble("moderateTemp", RiskThresholds.DEFAULT.moderateTemp.toDouble()).toFloat(),
                        moderateGsr = command.optDouble("moderateGsr", RiskThresholds.DEFAULT.moderateGsr.toDouble()).toFloat()
                    )
                    riskThresholdController.update(thresholds)
                    serviceScope.launch {
                        try {
                            preferences.saveRiskThresholds(thresholds)
                            Log.d(TAG, "Umbrales de riesgo persistidos: $thresholds")
                        } catch (e: Exception) {
                            Log.e(TAG, "Error persistiendo umbrales de riesgo: ${e.message}")
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error procesando umbrales de riesgo del teléfono", e)
                }
            }
        }
    }
}
