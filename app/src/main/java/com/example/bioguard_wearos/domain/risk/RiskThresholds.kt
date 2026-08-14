package com.example.bioguard_wearos.domain.risk

data class RiskThresholds(
    val criticalBpmHigh: Float = 135f,
    val criticalBpmLow: Float = 39f,
    val criticalTemp: Float = 39.0f,
    val moderateBpm: Float = 105f,
    val moderateTemp: Float = 37.8f,
    val moderateStress: Float = 60f
) {
    companion object {
        val DEFAULT = RiskThresholds()
    }
}
