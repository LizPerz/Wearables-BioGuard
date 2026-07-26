package com.example.bioguard_wearos.worker

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.bioguard_wearos.domain.repository.SyncRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val syncRepository: SyncRepository
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        private const val TAG = "BIOGUARD_SYNC_WORKER"
        const val UNIQUE_WORK_NAME = "bioguard_sync_work"
    }

    override suspend fun doWork(): Result {
        Log.d(TAG, "=== SyncWorker iniciado (attempt $runAttemptCount) ===")
        return try {
            val result = syncRepository.syncPendingReadings()
            result.fold(
                onSuccess = { count ->
                    Log.d(TAG, "Sync completado OK: $count lecturas enviadas al servidor bioguard-api-lkvnq.ondigitalocean.app")
                    Result.success()
                },
                onFailure = { error ->
                    Log.w(TAG, "Sync falló [${error::class.simpleName}]: ${error.message}")
                    Log.d(TAG, "Reintentando en el próximo ciclo programado ( WorkManager backoff )")
                    Result.retry()
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "SyncWorker excepción inesperada [${e::class.simpleName}]: ${e.message}", e)
            Log.d(TAG, "Reintentando en el próximo ciclo programado ( WorkManager backoff )")
            Result.retry()
        }
    }
}
