package com.example.bioguard_wearos.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class EventoMetabolicoDto(
    @SerialName("bpm")
    val bpm: Float,
    @SerialName("temperatura")
    val temperatura: Float,
    @SerialName("sudoracionGsr")
    val sudoracionGsr: Float,
    @SerialName("nivelRiesgo")
    val nivelRiesgo: String,
    @SerialName("timestamp")
    val timestamp: Long,
    @SerialName("tipoEvento")
    val tipoEvento: String,
    @SerialName("descripcion")
    val descripcion: String
)
