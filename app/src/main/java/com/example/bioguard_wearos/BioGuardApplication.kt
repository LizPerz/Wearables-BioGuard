package com.example.bioguard_wearos

import android.app.Application
import android.util.Log
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.example.bioguard_wearos.worker.SyncScheduler
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class BioGuardApplication : Application(), Configuration.Provider {

    companion object {
        private const val TAG = "BIOGUARD_APP"
    }

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var syncScheduler: SyncScheduler

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Application onCreate - inicializando WorkManager con HiltWorkerFactory")
        try {
            syncScheduler.schedulePeriodicSync()
            Log.d(TAG, "SyncScheduler programado correctamente")
        } catch (e: Exception) {
            Log.e(TAG, "Error al programar SyncScheduler [${e::class.simpleName}]: ${e.message}", e)
        }
    }
}
