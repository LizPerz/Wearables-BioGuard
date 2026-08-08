package com.example.bioguard_wearos.domain.repository

import com.example.bioguard_wearos.domain.model.SensorAvailability
import com.example.bioguard_wearos.domain.model.SensorData
import kotlinx.coroutines.flow.StateFlow

interface SensorDataRepository {
    val sensorData: StateFlow<SensorData>
    val sensorAvailability: StateFlow<SensorAvailability>
    fun startSensors()
    fun stopSensors()
}