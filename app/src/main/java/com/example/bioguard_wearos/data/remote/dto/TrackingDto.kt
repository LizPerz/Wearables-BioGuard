package com.example.bioguard_wearos.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TrackingDto(
    @SerialName("latitud")
    val latitud: Double,
    @SerialName("longitud")
    val longitud: Double,
    @SerialName("precision")
    val precision: Float,
    @SerialName("timestamp")
    val timestamp: Long
)
