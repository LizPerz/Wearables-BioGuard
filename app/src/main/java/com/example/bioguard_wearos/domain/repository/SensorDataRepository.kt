package com.example.bioguard_wearos.domain.repository

import com.example.bioguard_wearos.domain.model.SensorAvailability
import com.example.bioguard_wearos.domain.model.SensorData
import kotlinx.coroutines.flow.Flow

interface SensorDataRepository {
    val sensorData: Flow<SensorData>
    val sensorAvailability: Flow<SensorAvailability>
    fun startSensors()
    fun stopSensors()
}