package com.example.bioguard_wearos.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class HeartbeatDto(
    @SerialName("bateria")
    val bateria: Int,
    @SerialName("sensoresActivos")
    val sensoresActivos: List<String>,
    @SerialName("timestamp")
    val timestamp: Long
)
