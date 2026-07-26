package com.example.bioguard_wearos.domain.risk

data class BiometricInput(
    val bpm: Float = 0f,
    val temperature: Float = 0f,
    val gsr: Float = 0f,
    val bmi: Float = 0f
) {
    val isValid: Boolean
        get() = bpm > 0f && temperature > 0f && gsr > 0f && bmi > 0f

    companion object {
        const val BPM_MIN = 30f
        const val BPM_MAX = 220f
        const val TEMP_MIN = 30f
        const val TEMP_MAX = 42f
        const val GSR_MIN = 0f
        const val GSR_MAX = 150f
        const val BMI_MIN = 10f
        const val BMI_MAX = 60f
    }
}
