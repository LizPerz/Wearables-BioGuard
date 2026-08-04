package com.example.bioguard_wearos.domain.model

data class SensorData(
    val bpm: Float = 0f,
    val temperature: Float = 0f,
    val gsr: Float = 0f,
    val rmssd: Float = 0f,
    val sdnn: Float = 0f,
    val stressLabel: String = "",
    val isSimulated: Boolean = false,
    val nivelRiesgo: String = ""
) {
    val hrFraction: Float
        get() = if (bpm > 0f) ((bpm - HR_MIN) / (HR_MAX - HR_MIN)).coerceIn(0f, 1f) else 0.05f

    val tempFraction: Float
        get() = if (temperature > 0f) ((temperature - TEMP_MIN) / (TEMP_MAX - TEMP_MIN)).coerceIn(0f, 1f) else 0.05f

    val gsrFraction: Float
        get() = if (gsr > 0f) (gsr / GSR_MAX).coerceIn(0f, 1f) else 0.05f

    companion object {
        const val HR_MIN = 40f
        const val HR_MAX = 200f
        const val TEMP_MIN = 35f
        const val TEMP_MAX = 38f
        const val GSR_MAX = 100f
    }
}