package com.example.bioguard_wearos.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PacienteResponseDto(
    @SerialName("pacienteId")
    val pacienteId: String,
    @SerialName("nombre")
    val nombre: String,
    @SerialName("peso")
    val peso: Float,
    @SerialName("estatura")
    val estatura: Float,
    @SerialName("imc")
    val imc: Float,
    @SerialName("edad")
    val edad: Int
)
