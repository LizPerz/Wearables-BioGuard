package com.example.bioguard_wearos.data.repository

import android.content.Context
import android.util.Log
import com.example.bioguard_wearos.data.bluetooth.MessagePriority
import com.example.bioguard_wearos.data.bluetooth.PriorityMessageQueue
import com.example.bioguard_wearos.data.local.BioGuardPreferences
import com.example.bioguard_wearos.domain.repository.BiometricReadingRepository
import com.example.bioguard_wearos.domain.repository.SyncRepository
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
private data class PhoneReadingRequest(
    val pulsoBpm: Double,
    val temperaturaC: Double,
    val sudoracionGsr: Double,
    val hrv: Double? = null,
    val spo2: Double? = null,
    val timestamp: String
)

@Serializable
private data class WatchEventDto(
    val bpm: Float,
    val temperatura: Float,
    val sudoracionGsr: Float,
    val nivelRiesgo: String,
    val timestamp: Long,
    val tipoEvento: String,
    val descripcion: String
)

@Serializable
private data class WatchAlertDto(
    val tipoAlerta: String,
    val mensaje: String,
    val nivelRiesgo: String,
    val timestamp: Long,
    val bpm: Float,
    val temperatura: Float,
    val sudoracionGsr: Float
)

@Serializable
private data class PhoneHeartbeatRequest(
    val pacienteId: String? = null,
    val bateria: Int? = null,
    val sensoresActivos: List<String>? = null
)

@Singleton
class SyncRepositoryImpl @Inject constructor(
    @param:dagger.hilt.android.qualifiers.ApplicationContext private val context: Context,
    private val readingRepository: BiometricReadingRepository,
    private val preferences: BioGuardPreferences,
    private val priorityQueue: PriorityMessageQueue
) : SyncRepository {

    companion object {
        private const val TAG = "BIOGUARD_SYNC"
        private const val BIOGUARD_MOBILE_CAPABILITY = "bioguard_mobile"
    }

    /**
     * Utiliza Wearable DataClient (DataItems) para sincronización eficiente de telemetría continua
     * reduciendo el uso de radio Bluetooth y evitando descarte de paquetes.
     */
    private suspend fun sendDataClientItem(path: String, payloadJson: String): Result<Unit> {
        return try {
            val dataClient = Wearable.getDataClient(context)
            val putDataMapReq = PutDataMapRequest.create(path).apply {
                dataMap.putString("payload", payloadJson)
                dataMap.putLong("timestamp", System.currentTimeMillis())
            }
            val putDataReq = putDataMapReq.asPutDataRequest().setUrgent()
            dataClient.putDataItem(putDataReq).await()
            Log.d(TAG, "DataClient Item publicado en $path")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error publicando DataItem en $path: ${e.message}", e)
            Result.failure(e)
        }
    }

    private suspend fun sendMessageToPhoneInternal(path: String, payloadJson: String): Boolean {
        return try {
            val capabilityClient = Wearable.getCapabilityClient(context)
            val messageClient = Wearable.getMessageClient(context)
            val capabilityNodes = runCatching {
                capabilityClient.getCapability(
                    BIOGUARD_MOBILE_CAPABILITY,
                    CapabilityClient.FILTER_REACHABLE
                ).await().nodes
                    .filter { it.isNearby }
            }.onFailure { e ->
                Log.w(TAG, "No se pudo resolver capability movil: ${e.message}")
            }.getOrDefault(emptyList())

            if (capabilityNodes.isEmpty()) {
                Log.w(TAG, "No reachable BioGuard mobile capability for $path")
                return false
            }
            for (node in capabilityNodes) {
                messageClient.sendMessage(node.id, path, payloadJson.toByteArray(Charsets.UTF_8)).await()
            }
            Log.d(TAG, "Mensaje enviado exitosamente a $path")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error enviando mensaje a $path: ${e.message}", e)
            false
        }
    }

    override suspend fun loginCodigo(codigo: String): Result<String> {
        Log.w(TAG, "loginCodigo no soportado de forma directa en Wearable en arquitectura local-first")
        return Result.failure(Exception("Debe autenticarse en la app móvil"))
    }

    override suspend fun refresh(refreshToken: String): Result<String> {
        Log.w(TAG, "refresh no soportado de forma directa en Wearable en arquitectura local-first")
        return Result.failure(Exception("Debe refrescar sesión en la app móvil"))
    }

    override suspend fun vincularDispositivo(
        direccionMac: String,
        uuidDispositivo: String,
        modelo: String,
        sistemaOperativo: String
    ): Result<Pair<String, String>> {
        Log.w(TAG, "vincularDispositivo no soportado de forma directa en Wearable")
        return Result.failure(Exception("Vincular dispositivo a través de la app móvil"))
    }

    override suspend fun obtenerPaciente(): Result<Triple<String, Float, Float>> {
        Log.w(TAG, "obtenerPaciente no soportado de forma directa en Wearable")
        return Result.failure(Exception("Cargando perfil del paciente desde el móvil localmente"))
    }

    override suspend fun enviarLecturaLive(
        bpm: Float,
        temperatura: Float,
        gsr: Float,
        hrv: Float,
        nivelRiesgo: String
    ): Result<Unit> {
        return try {
            val request = PhoneReadingRequest(
                pulsoBpm = bpm.toDouble(),
                temperaturaC = temperatura.toDouble(),
                sudoracionGsr = gsr.toDouble(),
                hrv = hrv.toDouble(),
                timestamp = Instant.now().toString()
            )
            val jsonString = Json.encodeToString(PhoneReadingRequest.serializer(), request)
            if (sendMessageToPhoneInternal("/bioguard/telemetry/live-${System.currentTimeMillis()}", jsonString)) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("No hay telefono cercano para telemetria live"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Excepción en enviarLecturaLive [${e::class.simpleName}]: ${e.message}", e)
            Result.failure(e)
        }
    }

    override suspend fun enviarTracking(
        latitud: Double,
        longitud: Double,
        precision: Float
    ): Result<Unit> {
        Log.w(TAG, "enviarTracking no soportado de forma directa en Wearable")
        return Result.success(Unit)
    }

    override suspend fun enviarHeartbeat(
        bateria: Int,
        sensoresActivos: List<String>
    ): Result<Unit> {
        return try {
            val req = PhoneHeartbeatRequest(
                bateria = bateria,
                sensoresActivos = sensoresActivos
            )
            val jsonString = Json.encodeToString(PhoneHeartbeatRequest.serializer(), req)
            if (sendMessageToPhoneInternal("/bioguard/heartbeat", jsonString)) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("No hay telefono cercano para heartbeat"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Excepción en enviarHeartbeat [${e::class.simpleName}]: ${e.message}", e)
            Result.failure(e)
        }
    }

    override suspend fun syncPendingReadings(): Result<Int> {
        val unsynced = readingRepository.getUnsyncedReadings()
        if (unsynced.isEmpty()) {
            Log.d(TAG, "No hay lecturas pendientes de sincronización")
            return Result.success(0)
        }

        Log.d(TAG, "Sincronizando ${unsynced.size} lecturas locales al telefono cercano")
        var count = 0
        for (reading in unsynced) {
            val req = PhoneReadingRequest(
                pulsoBpm = reading.bpm.toDouble(),
                temperaturaC = reading.temperature.toDouble(),
                sudoracionGsr = reading.gsr.toDouble(),
                timestamp = Instant.ofEpochMilli(reading.timestamp).toString()
            )
            val jsonString = Json.encodeToString(PhoneReadingRequest.serializer(), req)
            val sent = sendMessageToPhoneInternal("/bioguard/telemetry/${reading.id}", jsonString)
            if (sent) {
                count++
            } else {
                Log.w(TAG, "Fallo al enviar lectura ${reading.id} al telefono cercano. Abortando ciclo.")
                break
            }
        }
        return Result.success(count)
    }

    override suspend fun enviarEvento(
        bpm: Float,
        temperatura: Float,
        gsr: Float,
        nivelRiesgo: String,
        tipoEvento: String,
        descripcion: String
    ): Result<Unit> {
        return try {
            val dto = WatchEventDto(
                bpm = bpm,
                temperatura = temperatura,
                sudoracionGsr = gsr,
                nivelRiesgo = nivelRiesgo,
                timestamp = System.currentTimeMillis(),
                tipoEvento = tipoEvento,
                descripcion = descripcion
            )
            val jsonString = Json.encodeToString(WatchEventDto.serializer(), dto)
            priorityQueue.enqueue(
                id = UUID.randomUUID().toString(),
                path = "/sensors/events",
                payloadJson = jsonString,
                priority = MessagePriority.ALERT,
                sendAction = ::sendMessageToPhoneInternal
            )
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Excepción en enviarEvento [${e::class.simpleName}]: ${e.message}", e)
            Result.failure(e)
        }
    }

    override suspend fun enviarAlerta(
        tipoAlerta: String,
        mensaje: String,
        nivelRiesgo: String,
        bpm: Float,
        temperatura: Float,
        gsr: Float
    ): Result<Unit> {
        return try {
            val dto = WatchAlertDto(
                tipoAlerta = tipoAlerta,
                mensaje = mensaje,
                nivelRiesgo = nivelRiesgo,
                timestamp = System.currentTimeMillis(),
                bpm = bpm,
                temperatura = temperatura,
                sudoracionGsr = gsr
            )
            val jsonString = Json.encodeToString(WatchAlertDto.serializer(), dto)
            priorityQueue.enqueue(
                id = UUID.randomUUID().toString(),
                path = "/sensors/alerts",
                payloadJson = jsonString,
                priority = MessagePriority.EMERGENCY,
                sendAction = ::sendMessageToPhoneInternal
            )
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Excepción en enviarAlerta [${e::class.simpleName}]: ${e.message}", e)
            Result.failure(e)
        }
    }
}
