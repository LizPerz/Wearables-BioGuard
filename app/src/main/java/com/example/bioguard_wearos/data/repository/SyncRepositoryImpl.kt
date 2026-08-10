package com.example.bioguard_wearos.data.repository

import android.content.Context
import android.util.Log
import com.example.bioguard_wearos.data.bluetooth.MessagePriority
import com.example.bioguard_wearos.data.bluetooth.PriorityMessageQueue
import com.example.bioguard_wearos.data.local.BioGuardPreferences
import com.example.bioguard_wearos.domain.repository.BiometricReadingRepository
import com.example.bioguard_wearos.domain.repository.SyncRepository
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.wearable.CapabilityClient
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
    val pasos: Int? = null,
    val timestamp: String,
    val sourceMessageId: String = java.util.UUID.randomUUID().toString()
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
        private const val STATUS_API_NOT_CONNECTED = 17
        private const val STATUS_TARGET_NODE_NOT_FOUND = 8
    }

    private suspend fun sendMessageToPhoneInternal(path: String, payloadJson: String): Boolean {
        return try {
            val trustedNodeId = preferences.getTrustedMobileNodeId()
            val capabilityClient = Wearable.getCapabilityClient(context)
            val messageClient = Wearable.getMessageClient(context)
            val allReachableMobileNodes = runCatching {
                capabilityClient.getCapability(
                    BIOGUARD_MOBILE_CAPABILITY,
                    CapabilityClient.FILTER_REACHABLE
                ).await().nodes.filter { it.isNearby }
            }.onFailure { e ->
                val statusCode = (e as? ApiException)?.statusCode
                when (statusCode) {
                    STATUS_API_NOT_CONNECTED -> Log.e(TAG, "Wearable Data Layer API no conectada. Verifica emparejamiento del reloj.")
                    STATUS_TARGET_NODE_NOT_FOUND -> Log.w(TAG, "Movil no encontrado. Posiblemente fuera de rango o app cerrada.")
                    else -> Log.w(TAG, "No se pudo resolver capability movil: ${e.message}")
                }
            }.getOrDefault(emptyList())

            val targetNodes = if (!trustedNodeId.isNullOrBlank()) {
                val matched = allReachableMobileNodes.filter { it.id == trustedNodeId }
                if (matched.isNotEmpty()) matched else allReachableMobileNodes
            } else {
                allReachableMobileNodes
            }

            if (targetNodes.isEmpty()) {
                Log.w(TAG, "No reachable BioGuard mobile capability for $path")
                return false
            }
            for (node in targetNodes) {
                if (trustedNodeId.isNullOrBlank()) {
                    preferences.saveTrustedMobileNodeId(node.id)
                }
                messageClient.sendMessage(node.id, path, payloadJson.toByteArray(Charsets.UTF_8))
                    .addOnSuccessListener {
                        Log.d(TAG, "Mensaje enviado OK a $path -> node ${node.displayName}")
                    }
                    .addOnFailureListener { e ->
                        val statusCode = (e as? ApiException)?.statusCode
                        when (statusCode) {
                            STATUS_API_NOT_CONNECTED -> Log.e(TAG, "API no conectada al enviar a $path")
                            STATUS_TARGET_NODE_NOT_FOUND -> Log.w(TAG, "Nodo destino desconectado: ${node.displayName}")
                            else -> Log.e(TAG, "Error enviando a $path: ${e.message}")
                        }
                    }
                    .await()
            }
            true
        } catch (e: ApiException) {
            when (e.statusCode) {
                STATUS_API_NOT_CONNECTED -> Log.e(TAG, "Wearable Data Layer API no conectada: ${e.message}")
                STATUS_TARGET_NODE_NOT_FOUND -> Log.w(TAG, "Nodo movil no alcanzable: ${e.message}")
                else -> Log.e(TAG, "ApiException enviando a $path [status=${e.statusCode}]: ${e.message}")
            }
            false
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
        steps: Int?,
        nivelRiesgo: String
    ): Result<Unit> {
        return try {
            val request = PhoneReadingRequest(
                pulsoBpm = bpm.toDouble(),
                temperaturaC = temperatura.toDouble(),
                sudoracionGsr = gsr.toDouble(),
                hrv = hrv.toDouble(),
                pasos = steps,
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
        priorityQueue.drain(::sendMessageToPhoneInternal)
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
                hrv = reading.hrvRmssd.toDouble().takeIf { it > 0.0 },
                pasos = reading.steps,
                timestamp = Instant.ofEpochMilli(reading.timestamp).toString()
            )
            val jsonString = Json.encodeToString(PhoneReadingRequest.serializer(), req)
            val sent = sendMessageToPhoneInternal("/bioguard/telemetry/${reading.id}", jsonString)
            if (sent) {
                count++
            } else {
                Log.w(TAG, "Fallo al enviar lectura ${reading.id} al telefono cercano. Abortando ciclo.")
                return Result.failure(
                    IllegalStateException("No se encontro un movil BioGuard alcanzable")
                )
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

    override suspend fun sendDismissCommandToPhone(): Result<Unit> {
        return try {
            val sent = sendMessageToPhoneInternal("/commands/dismiss", "{}")
            if (sent) Result.success(Unit) else Result.failure(Exception("No reachable mobile phone"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
