package com.example.bioguard_wearos.domain.risk

data class BiometricInput(
    val bpm: Float = 0f,
    val temperature: Float = 0f,
    val estresPct: Float = 0f,
    val bmi: Float = 0f
) {
    val isValid: Boolean
        get() = bpm > 0f && temperature > 0f && estresPct > 0f && bmi > 0f

    companion object {
        const val BPM_MIN = 30f
        const val BPM_MAX = 220f
        const val TEMP_MIN = 30f
        const val TEMP_MAX = 42f
        const val ESTRES_MIN = 0f
        const val ESTRES_MAX = 100f
        const val BMI_MIN = 10f
        const val BMI_MAX = 60f
    }
}
