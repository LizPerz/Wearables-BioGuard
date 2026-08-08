package com.example.bioguard_wearos.data.energy

import android.content.Context
import android.os.PowerManager
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DutyCycleManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "BIOGUARD_DUTY_CYCLE"
        private const val DEFAULT_MICRO_WAKELOCK_MS = 500L
    }

    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager

    /**
     * Adquiere un micro WakeLock por una duración máxima corta (ej. 500ms)
     * para ejecutar tareas críticas de procesamiento y liberar el CPU inmediatamente.
     */
    fun runWithMicroWakeLock(timeoutMs: Long = DEFAULT_MICRO_WAKELOCK_MS, action: () -> Unit) {
        val wakeLock = try {
            powerManager?.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "BioGuard::DutyCycleMicroLock"
            )?.apply {
                acquire(timeoutMs)
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "No se pudo adquirir micro WakeLock: ${e.message}")
            null
        }

        try {
            action()
        } finally {
            wakeLock?.let {
                if (it.isHeld) {
                    try {
                        it.release()
                    } catch (e: Exception) {
                        Log.w(TAG, "Error liberando micro WakeLock", e)
                    }
                }
            }
        }
    }
}
