package com.example.bioguard_wearos.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AlertaDto(
    @SerialName("tipoAlerta")
    val tipoAlerta: String,
    @SerialName("mensaje")
    val mensaje: String,
    @SerialName("nivelRiesgo")
    val nivelRiesgo: String,
    @SerialName("timestamp")
    val timestamp: Long,
    @SerialName("bpm")
    val bpm: Float,
    @SerialName("temperatura")
    val temperatura: Float,
    @SerialName("sudoracionGsr")
    val sudoracionGsr: Float
)
