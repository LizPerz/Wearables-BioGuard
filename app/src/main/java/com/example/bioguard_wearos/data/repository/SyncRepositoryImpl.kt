package com.example.bioguard_wearos.data.repository

import android.util.Log
import com.example.bioguard_wearos.data.local.BioGuardPreferences
import com.example.bioguard_wearos.data.local.db.BiometricReadingEntity
import com.example.bioguard_wearos.data.remote.api.BioGuardApi
import com.example.bioguard_wearos.data.remote.dto.AlertaDto
import com.example.bioguard_wearos.data.remote.dto.EventoMetabolicoDto
import com.example.bioguard_wearos.data.remote.dto.HeartbeatDto
import com.example.bioguard_wearos.data.remote.dto.LecturaBatchDto
import com.example.bioguard_wearos.data.remote.dto.LecturaSensoresDto
import com.example.bioguard_wearos.data.remote.dto.TrackingBatchDto
import com.example.bioguard_wearos.data.remote.dto.TrackingDto
import com.example.bioguard_wearos.data.remote.dto.VincularDispositivoDto
import com.example.bioguard_wearos.domain.repository.BiometricReadingRepository
import com.example.bioguard_wearos.domain.repository.SyncRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncRepositoryImpl @Inject constructor(
    private val api: BioGuardApi,
    private val readingRepository: BiometricReadingRepository,
    private val preferences: BioGuardPreferences
) : SyncRepository {

    companion object {
        private const val TAG = "BIOGUARD_SYNC"
    }

    override suspend fun loginCodigo(codigo: String): Result<String> {
        return try {
            val result = api.loginCodigo(codigo)
            result.onSuccess { response ->
                preferences.saveAuthToken(response.token, response.refreshToken, response.expiracion)
                Log.d(TAG, "Login OK: token guardado, expira en ${response.expiracion}")
            }.onFailure { ex ->
                Log.w(TAG, "Login falló [${ex::class.simpleName}]: ${ex.message}")
            }
            result.map { it.token }
        } catch (e: Exception) {
            Log.e(TAG, "Excepción en loginCodigo [${e::class.simpleName}]: ${e.message}", e)
            Result.failure(e)
        }
    }

    override suspend fun refresh(refreshToken: String): Result<String> {
        return try {
            val result = api.refresh(refreshToken)
            result.onSuccess { response ->
                preferences.saveAuthToken(response.token, response.refreshToken, response.expiracion)
                Log.d(TAG, "Token refrescado OK, nueva expiración: ${response.expiracion}")
            }.onFailure { ex ->
                Log.w(TAG, "Refresh falló [${ex::class.simpleName}]: ${ex.message}")
            }
            result.map { it.token }
        } catch (e: Exception) {
            Log.e(TAG, "Excepción en refresh [${e::class.simpleName}]: ${e.message}", e)
            Result.failure(e)
        }
    }

    override suspend fun vincularDispositivo(
        direccionMac: String,
        uuidDispositivo: String,
        modelo: String,
        sistemaOperativo: String
    ): Result<Pair<String, String>> {
        return try {
            val dto = VincularDispositivoDto(
                direccionMac = direccionMac,
                uuidDispositivo = uuidDispositivo,
                modelo = modelo,
                sistemaOperativo = sistemaOperativo
            )
            val result = api.vincularDispositivo(dto)
            result.onSuccess { response ->
                preferences.saveDeviceBinding(response.dispositivoId, response.pacienteId)
                Log.d(TAG, "Dispositivo vinculado OK: id=${response.dispositivoId}, paciente=${response.pacienteId}")
            }.onFailure { ex ->
                Log.w(TAG, "Vinculación falló [${ex::class.simpleName}]: ${ex.message}")
            }
            result.map { Pair(it.dispositivoId, it.pacienteId) }
        } catch (e: Exception) {
            Log.e(TAG, "Excepción en vincularDispositivo [${e::class.simpleName}]: ${e.message}", e)
            Result.failure(e)
        }
    }

    override suspend fun obtenerPaciente(): Result<Triple<String, Float, Float>> {
        return try {
            val result = api.obtenerPaciente()
            result.onFailure { ex ->
                Log.w(TAG, "Obtener paciente falló [${ex::class.simpleName}]: ${ex.message}")
            }
            result.map { Triple(it.nombre, it.peso, it.estatura) }
        } catch (e: Exception) {
            Log.e(TAG, "Excepción en obtenerPaciente [${e::class.simpleName}]: ${e.message}", e)
            Result.failure(e)
        }
    }

    override suspend fun enviarLecturaLive(
        bpm: Float,
        temperatura: Float,
        gsr: Float,
        nivelRiesgo: String
    ): Result<Unit> {
        return try {
            val dto = LecturaSensoresDto(
                bpm = bpm,
                temperatura = temperatura,
                sudoracionGsr = gsr,
                nivelRiesgo = nivelRiesgo,
                timestamp = System.currentTimeMillis()
            )
            val result = api.enviarLectura(dto)
            result.onSuccess {
                Log.d(TAG, "Lectura live enviada OK: BPM=$bpm, Temp=$temperatura")
            }.onFailure { ex ->
                Log.w(TAG, "Lectura live falló [${ex::class.simpleName}]: ${ex.message}")
            }
            result
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
        return try {
            val dto = TrackingDto(
                latitud = latitud,
                longitud = longitud,
                precision = precision,
                timestamp = System.currentTimeMillis()
            )
            val result = api.enviarTracking(dto)
            result.onSuccess {
                Log.d(TAG, "Tracking enviado OK: lat=$latitud, lng=$longitud")
            }.onFailure { ex ->
                Log.w(TAG, "Tracking falló [${ex::class.simpleName}]: ${ex.message}")
            }
            result
        } catch (e: Exception) {
            Log.e(TAG, "Excepción en enviarTracking [${e::class.simpleName}]: ${e.message}", e)
            Result.failure(e)
        }
    }

    override suspend fun enviarHeartbeat(
        bateria: Int,
        sensoresActivos: List<String>
    ): Result<Unit> {
        return try {
            val dto = HeartbeatDto(
                bateria = bateria,
                sensoresActivos = sensoresActivos,
                timestamp = System.currentTimeMillis()
            )
            val result = api.enviarHeartbeat(dto)
            result.onSuccess {
                Log.d(TAG, "Heartbeat enviado OK: batería=$bateria%, sensores=${sensoresActivos.size}")
            }.onFailure { ex ->
                Log.w(TAG, "Heartbeat falló [${ex::class.simpleName}]: ${ex.message}")
            }
            result
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

        Log.d(TAG, "Preparando batch de ${unsynced.size} lecturas para sincronizar")

        val batchDto = LecturaBatchDto(
            lecturas = unsynced.map { it.toLecturaSensoresDto() }
        )

        return try {
            val result = api.enviarLecturaBatch(batchDto)
            result.onSuccess {
                val ids = unsynced.map { it.id }
                readingRepository.markAsSynced(ids)
                Log.d(TAG, "Batch sync OK: ${ids.size} lecturas marcadas como synced en DB local")
            }.onFailure { ex ->
                Log.w(TAG, "Batch sync falló [${ex::class.simpleName}]: ${ex.message}")
                Log.d(TAG, "Las ${unsynced.size} lecturas permanecerán como pending para el próximo ciclo")
            }
            result.map { unsynced.size }
        } catch (e: Exception) {
            Log.e(TAG, "Excepción en syncPendingReadings [${e::class.simpleName}]: ${e.message}", e)
            Result.failure(e)
        }
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
            val dto = EventoMetabolicoDto(
                bpm = bpm,
                temperatura = temperatura,
                sudoracionGsr = gsr,
                nivelRiesgo = nivelRiesgo,
                timestamp = System.currentTimeMillis(),
                tipoEvento = tipoEvento,
                descripcion = descripcion
            )
            val result = api.enviarEvento(dto)
            result.onFailure { ex ->
                Log.w(TAG, "Evento metabólico falló [${ex::class.simpleName}]: ${ex.message}")
            }
            result
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
            val dto = AlertaDto(
                tipoAlerta = tipoAlerta,
                mensaje = mensaje,
                nivelRiesgo = nivelRiesgo,
                timestamp = System.currentTimeMillis(),
                bpm = bpm,
                temperatura = temperatura,
                sudoracionGsr = gsr
            )
            val result = api.enviarAlerta(dto)
            result.onFailure { ex ->
                Log.w(TAG, "Alerta falló [${ex::class.simpleName}]: ${ex.message}")
            }
            result
        } catch (e: Exception) {
            Log.e(TAG, "Excepción en enviarAlerta [${e::class.simpleName}]: ${e.message}", e)
            Result.failure(e)
        }
    }

    private fun BiometricReadingEntity.toLecturaSensoresDto(): LecturaSensoresDto {
        return LecturaSensoresDto(
            bpm = bpm,
            temperatura = temperature,
            sudoracionGsr = gsr,
            nivelRiesgo = riskLevel.name,
            timestamp = timestamp
        )
    }
}
