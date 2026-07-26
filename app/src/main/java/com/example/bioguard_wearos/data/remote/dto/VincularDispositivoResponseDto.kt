package com.example.bioguard_wearos.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class VincularDispositivoResponseDto(
    @SerialName("dispositivoId")
    val dispositivoId: String,
    @SerialName("pacienteId")
    val pacienteId: String,
    @SerialName("vinculado")
    val vinculado: Boolean
)
