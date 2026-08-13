package com.example.bioguard_wearos.presentation.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.bioguard_wearos.presentation.service.BioGuardSensorService

/**
 * Reinicia el monitoreo biométrico tras el arranque del dispositivo para que el reloj
 * siga capturando y enviando lecturas sin necesidad de abrir la app manualmente.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED &&
            intent?.action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            return
        }
        try {
            val serviceIntent = Intent(context, BioGuardSensorService::class.java)
            ContextCompat.startForegroundService(context, serviceIntent)
            Log.d(TAG, "BioGuardSensorService reiniciado tras ${intent.action}")
        } catch (e: Exception) {
            Log.e(TAG, "No se pudo reiniciar el servicio tras el arranque: ${e.message}")
        }
    }

    companion object {
        private const val TAG = "BIOGUARD_BOOT"
    }
}
