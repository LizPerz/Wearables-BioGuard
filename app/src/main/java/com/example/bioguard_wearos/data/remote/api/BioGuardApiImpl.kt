package com.example.bioguard_wearos.data.remote.api

import com.example.bioguard_wearos.data.remote.dto.AlertaDto
import com.example.bioguard_wearos.data.remote.dto.EventoMetabolicoDto
import com.example.bioguard_wearos.data.remote.dto.HeartbeatDto
import com.example.bioguard_wearos.data.remote.dto.LecturaBatchDto
import com.example.bioguard_wearos.data.remote.dto.LecturaSensoresDto
import com.example.bioguard_wearos.data.remote.dto.LoginCodigoRequestDto
import com.example.bioguard_wearos.data.remote.dto.LoginResponseDto
import com.example.bioguard_wearos.data.remote.dto.PacienteResponseDto
import com.example.bioguard_wearos.data.remote.dto.RefreshTokenRequestDto
import com.example.bioguard_wearos.data.remote.dto.RefreshTokenResponseDto
import com.example.bioguard_wearos.data.remote.dto.TrackingBatchDto
import com.example.bioguard_wearos.data.remote.dto.TrackingDto
import com.example.bioguard_wearos.data.remote.dto.VincularDispositivoDto
import com.example.bioguard_wearos.data.remote.dto.VincularDispositivoResponseDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BioGuardApiImpl @Inject constructor(
    private val client: HttpClient
) : BioGuardApi {

    companion object {
        private val BASE_URL = com.example.bioguard_wearos.BuildConfig.BASE_URL
    }

    override suspend fun loginCodigo(codigo: String): Result<LoginResponseDto> {
        return runCatching {
            val response = client.post("$BASE_URL/api/Auth/login-codigo") {
                contentType(ContentType.Application.Json)
                setBody(LoginCodigoRequestDto(codigo = codigo))
            }
            if (!response.status.isSuccess()) {
                throw Exception("Error HTTP ${response.status}")
            }
            response.body()
        }
    }

    override suspend fun refresh(refreshToken: String): Result<RefreshTokenResponseDto> {
        return runCatching {
            val response = client.post("$BASE_URL/api/Auth/refresh") {
                contentType(ContentType.Application.Json)
                setBody(RefreshTokenRequestDto(refreshToken = refreshToken))
            }
            if (!response.status.isSuccess()) {
                throw Exception("Error HTTP ${response.status}")
            }
            response.body()
        }
    }

    override suspend fun vincularDispositivo(dispositivo: VincularDispositivoDto): Result<VincularDispositivoResponseDto> {
        return runCatching {
            val response = client.post("$BASE_URL/api/Dispositivos/vincular") {
                contentType(ContentType.Application.Json)
                setBody(dispositivo)
            }
            if (!response.status.isSuccess()) {
                throw Exception("Error HTTP ${response.status}")
            }
            response.body()
        }
    }

    override suspend fun obtenerPaciente(): Result<PacienteResponseDto> {
        return runCatching {
            val response = client.get("$BASE_URL/api/Pacientes/mi-paciente")
            if (!response.status.isSuccess()) {
                throw Exception("Error HTTP ${response.status}")
            }
            response.body()
        }
    }

    override suspend fun enviarLectura(lectura: LecturaSensoresDto): Result<Unit> {
        return runCatching {
            val response = client.post("$BASE_URL/api/Sensores/lectura") {
                contentType(ContentType.Application.Json)
                setBody(lectura)
            }
            if (!response.status.isSuccess()) {
                throw Exception("Error HTTP ${response.status}")
            }
        }
    }

    override suspend fun enviarTracking(tracking: TrackingDto): Result<Unit> {
        return runCatching {
            val response = client.post("$BASE_URL/api/Sensores/tracking") {
                contentType(ContentType.Application.Json)
                setBody(tracking)
            }
            if (!response.status.isSuccess()) {
                throw Exception("Error HTTP ${response.status}")
            }
        }
    }

    override suspend fun enviarHeartbeat(heartbeat: HeartbeatDto): Result<Unit> {
        return runCatching {
            val response = client.post("$BASE_URL/api/Dispositivos/heartbeat") {
                contentType(ContentType.Application.Json)
                setBody(heartbeat)
            }
            if (!response.status.isSuccess()) {
                throw Exception("Error HTTP ${response.status}")
            }
        }
    }

    override suspend fun enviarLecturaBatch(batch: LecturaBatchDto): Result<Unit> {
        return runCatching {
            val response = client.post("$BASE_URL/api/Sensores/lectura-batch") {
                contentType(ContentType.Application.Json)
                setBody(batch)
            }
            if (!response.status.isSuccess()) {
                throw Exception("Error HTTP ${response.status}")
            }
        }
    }

    override suspend fun enviarTrackingBatch(batch: TrackingBatchDto): Result<Unit> {
        return runCatching {
            val response = client.post("$BASE_URL/api/Sensores/tracking-batch") {
                contentType(ContentType.Application.Json)
                setBody(batch)
            }
            if (!response.status.isSuccess()) {
                throw Exception("Error HTTP ${response.status}")
            }
        }
    }

    override suspend fun enviarEvento(evento: EventoMetabolicoDto): Result<Unit> {
        return runCatching {
            val response = client.post("$BASE_URL/api/Sensores/evento") {
                contentType(ContentType.Application.Json)
                setBody(evento)
            }
            if (!response.status.isSuccess()) {
                throw Exception("Error HTTP ${response.status}")
            }
        }
    }

    override suspend fun enviarAlerta(alerta: AlertaDto): Result<Unit> {
        return runCatching {
            val response = client.post("$BASE_URL/api/Alertas") {
                contentType(ContentType.Application.Json)
                setBody(alerta)
            }
            if (!response.status.isSuccess()) {
                throw Exception("Error HTTP ${response.status}")
            }
        }
    }
}
