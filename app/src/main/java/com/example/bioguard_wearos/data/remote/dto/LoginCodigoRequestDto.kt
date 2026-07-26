package com.example.bioguard_wearos.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class LoginCodigoRequestDto(
    @SerialName("CodigoAcceso")
    val codigo: String
)
