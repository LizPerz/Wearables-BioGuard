package com.example.bioguard_wearos.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class VincularDispositivoDto(
    @SerialName("direccionMac")
    val direccionMac: String,
    @SerialName("uuidDispositivo")
    val uuidDispositivo: String,
    @SerialName("modelo")
    val modelo: String,
    @SerialName("sistemaOperativo")
    val sistemaOperativo: String
)
