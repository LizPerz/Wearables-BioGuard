package com.example.bioguard_wearos.domain.model

data class SensorAvailability(
    val heartRateAvailable: Boolean = false,
    val temperatureAvailable: Boolean = false,
    val gsrAvailable: Boolean = false
)