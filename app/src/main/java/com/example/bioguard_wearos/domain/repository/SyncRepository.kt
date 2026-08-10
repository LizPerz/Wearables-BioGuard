package com.example.bioguard_wearos.domain.repository

interface SyncRepository {

    suspend fun loginCodigo(codigo: String): Result<String>

    suspend fun refresh(refreshToken: String): Result<String>

    suspend fun vincularDispositivo(
        direccionMac: String,
        uuidDispositivo: String,
        modelo: String,
        sistemaOperativo: String
    ): Result<Pair<String, String>>

    suspend fun obtenerPaciente(): Result<Triple<String, Float, Float>>

    suspend fun enviarLecturaLive(
        bpm: Float,
        temperatura: Float,
        gsr: Float,
        hrv: Float,
        steps: Int?,
        nivelRiesgo: String
    ): Result<Unit>

    suspend fun enviarTracking(
        latitud: Double,
        longitud: Double,
        precision: Float
    ): Result<Unit>

    suspend fun enviarHeartbeat(
        bateria: Int,
        sensoresActivos: List<String>
    ): Result<Unit>

    suspend fun syncPendingReadings(): Result<Int>

    suspend fun enviarEvento(
        bpm: Float,
        temperatura: Float,
        gsr: Float,
        nivelRiesgo: String,
        tipoEvento: String,
        descripcion: String
    ): Result<Unit>

    suspend fun enviarAlerta(
        tipoAlerta: String,
        mensaje: String,
        nivelRiesgo: String,
        bpm: Float,
        temperatura: Float,
        gsr: Float
    ): Result<Unit>

    suspend fun sendDismissCommandToPhone(): Result<Unit>
}
