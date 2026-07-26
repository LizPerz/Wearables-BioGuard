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

interface BioGuardApi {

    suspend fun loginCodigo(codigo: String): Result<LoginResponseDto>

    suspend fun refresh(refreshToken: String): Result<RefreshTokenResponseDto>

    suspend fun vincularDispositivo(dispositivo: VincularDispositivoDto): Result<VincularDispositivoResponseDto>

    suspend fun obtenerPaciente(): Result<PacienteResponseDto>

    suspend fun enviarLectura(lectura: LecturaSensoresDto): Result<Unit>

    suspend fun enviarTracking(tracking: TrackingDto): Result<Unit>

    suspend fun enviarHeartbeat(heartbeat: HeartbeatDto): Result<Unit>

    suspend fun enviarLecturaBatch(batch: LecturaBatchDto): Result<Unit>

    suspend fun enviarTrackingBatch(batch: TrackingBatchDto): Result<Unit>

    suspend fun enviarEvento(evento: EventoMetabolicoDto): Result<Unit>

    suspend fun enviarAlerta(alerta: AlertaDto): Result<Unit>
}
