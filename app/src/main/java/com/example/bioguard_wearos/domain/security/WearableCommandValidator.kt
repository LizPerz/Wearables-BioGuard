package com.example.bioguard_wearos.domain.security

import com.example.bioguard_wearos.domain.risk.RiskThresholds

object WearableCommandValidator {
    const val MAX_MESSAGE_BYTES = 8 * 1024
    val allowedPaths = setOf(
        "/commands/alert",
        "/commands/dismiss",
        "/commands/sampling_rate",
        "/commands/sensor_toggle",
        "/bioguard/ack",
        "/risk-thresholds"
    )

    fun isValidAlert(bpm: Float, temperature: Float, estresPct: Float, probability: Float): Boolean =
        bpm.isFinite() && bpm in 20f..250f &&
            temperature.isFinite() && (temperature == 0f || temperature in 25f..45f) &&
            estresPct.isFinite() && estresPct in 0f..100f &&
            probability.isFinite() && probability in 0f..1f

    fun isValidThresholds(value: RiskThresholds): Boolean =
        value.criticalBpmHigh.isFinite() && value.criticalBpmHigh in 100f..220f &&
            value.criticalBpmLow.isFinite() && value.criticalBpmLow in 25f..60f &&
            value.moderateBpm.isFinite() && value.moderateBpm >= 80f && value.moderateBpm < value.criticalBpmHigh &&
            value.criticalTemp.isFinite() && value.criticalTemp in 38f..43f &&
            value.moderateTemp.isFinite() && value.moderateTemp >= 36.5f && value.moderateTemp < value.criticalTemp &&
            value.moderateStress.isFinite() && value.moderateStress in 0f..100f

    fun isValidAckPath(path: String): Boolean =
        Regex("^/bioguard/telemetry/(?:[0-9]{1,18}|live-[0-9]{1,18})$").matches(path)
}
