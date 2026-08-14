package com.example.bioguard_wearos.presentation

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.bioguard_wearos.R
import com.example.bioguard_wearos.domain.risk.RiskLevel

/**
 * Gestiona las notificaciones locales del wearable para alertas de salud.
 * Las alertas críticas se muestran directamente en el reloj con vibración
 * incluso cuando el teléfono no está disponible.
 */
class WearableAlertNotifier(private val context: Context) {

    private val manager = NotificationManagerCompat.from(context)

    companion object {
        const val CHANNEL_MONITORING = "bioguard_wear_monitoring"
        const val CHANNEL_ALERT = "bioguard_wear_alert"
        const val CHANNEL_CRITICAL = "bioguard_wear_critical"
        const val NOTIFICATION_MONITORING = 100
        const val NOTIFICATION_ALERT = 101
        const val NOTIFICATION_CRITICAL = 102
    }

    fun createChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val systemManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        systemManager.createNotificationChannel(
            NotificationChannel(CHANNEL_MONITORING, "Monitoreo activo", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Estado del monitoreo de sensores"
                setShowBadge(false)
            }
        )

        systemManager.createNotificationChannel(
            NotificationChannel(CHANNEL_ALERT, "Alertas de salud", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Cambios relevantes en tus constantes vitales"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 300, 150, 300)
            }
        )

        val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        systemManager.createNotificationChannel(
            NotificationChannel(CHANNEL_CRITICAL, "Alertas críticas", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Señales que requieren atención inmediata"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 250, 500, 250, 500)
                setSound(
                    alarmUri,
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
            }
        )
    }

    fun monitoringNotification(): Notification {
        return NotificationCompat.Builder(context, CHANNEL_MONITORING)
            .setContentTitle("BioGuard activo")
            .setContentText("Monitoreando tus signos vitales")
            .setSmallIcon(R.drawable.ic_notification)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    fun notifyRiskLevel(level: RiskLevel, bpm: Float, temperatura: Float, glucosaMgDl: Float = 0f) {
        if (!level.isElevated) return

        val isCritical = level.isCritical
        val pico = when (level) {
            RiskLevel.CRITICAL_HIGH -> "PICO ALTO"
            RiskLevel.MODERATE_HIGH -> "PICO MEDIO"
            RiskLevel.OPTIMAL -> "PICO BAJO"
        }
        val title = if (isCritical) "⚠️ PICO ALTO — BioGuard" else "PICO MEDIO — BioGuard"
        val body = buildString {
            append("$pico detectado")
            if (glucosaMgDl > 0f) append(" · Glucosa ~${glucosaMgDl.toInt()} mg/dL")
            append("\nBPM: ${bpm.toInt()}")
            if (temperatura > 0f) append(" | Temp: ${"%.1f".format(temperatura)}°C")
        }

        val canPost = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED

        if (!canPost) return

        val notifId = if (isCritical) NOTIFICATION_CRITICAL else NOTIFICATION_ALERT
        val channel = if (isCritical) CHANNEL_CRITICAL else CHANNEL_ALERT

        val notification = NotificationCompat.Builder(context, channel)
            .setContentTitle(title)
            .setContentText(body)
            .setSmallIcon(R.drawable.ic_notification)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(if (isCritical) NotificationCompat.CATEGORY_ALARM else NotificationCompat.CATEGORY_RECOMMENDATION)
            .setAutoCancel(!isCritical)
            .setOngoing(isCritical)  // Crítico persiste hasta ser atendido
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .build()

        try {
            manager.notify(notifId, notification)
            if (isCritical) triggerHapticAlert()
        } catch (_: SecurityException) { /* permiso revocado */ }
    }

    private fun triggerHapticAlert() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vm.defaultVibrator.vibrate(
                    VibrationEffect.createWaveform(longArrayOf(0, 500, 250, 500, 250, 500), -1)
                )
            } else {
                @Suppress("DEPRECATION")
                val v = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                @Suppress("DEPRECATION")
                v.vibrate(longArrayOf(0, 500, 250, 500, 250, 500), -1)
            }
        } catch (_: Exception) { /* vibración no disponible */ }
    }

    fun showHelpRequestedNotification() {
        val canPost = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        if (!canPost) return

        triggerHapticAlert()

        val notification = NotificationCompat.Builder(context, CHANNEL_CRITICAL)
            .setContentTitle("AYUDA SOLICITADA")
            .setContentText("Protocolo de emergencia activado. Enviando alerta a red familiar...")
            .setSmallIcon(R.drawable.ic_notification)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setOngoing(true)
            .setAutoCancel(false)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()

        try {
            manager.notify(NOTIFICATION_CRITICAL + 10, notification)
        } catch (_: SecurityException) { /* permiso revocado */ }
    }
}
