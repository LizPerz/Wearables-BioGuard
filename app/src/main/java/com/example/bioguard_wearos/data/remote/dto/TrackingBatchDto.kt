package com.example.bioguard_wearos.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TrackingBatchDto(
    @SerialName("posiciones")
    val posiciones: List<TrackingDto>
)
