package com.example.bioguard_wearos.domain.usecase

import com.example.bioguard_wearos.domain.repository.SensorDataRepository
import javax.inject.Inject

class StartSensorsUseCase @Inject constructor(
    private val repository: SensorDataRepository
) {
    operator fun invoke() {
        repository.startSensors()
    }
}