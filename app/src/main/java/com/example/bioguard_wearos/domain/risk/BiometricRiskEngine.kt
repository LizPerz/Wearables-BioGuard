package com.example.bioguard_wearos.domain.risk

import kotlin.math.exp

class BiometricRiskEngine(
    private val weights: Weights = Weights()
) {

    data class Weights(
        val bias: Float = -2.0f,
        val bpm: Float = 1.5f,
        val gsr: Float = 1.0f,
        val temperature: Float = 1.5f,
        val bmi: Float = 0.5f
    ) {
        fun toArray(): FloatArray = floatArrayOf(bias, bpm, gsr, temperature, bmi)
    }

    fun evaluate(input: BiometricInput): RiskAssessment {
        if (!input.isValid) {
            return RiskAssessment(
                level = RiskLevel.OPTIMAL,
                probability = 0f,
                triggerSource = TriggerSource.NONE,
                input = input
            )
        }

        val sigmoidProb = computeSigmoid(input)
        if (sigmoidProb > OVERRIDE_THRESHOLD) {
            return RiskAssessment(
                level = RiskLevel.CRITICAL_HIGH,
                probability = sigmoidProb,
                triggerSource = TriggerSource.OVERRIDE,
                input = input
            )
        }

        val matrixLevel = evaluateMatrix(input)
        val source = when (matrixLevel) {
            RiskLevel.OPTIMAL -> TriggerSource.NONE
            else -> TriggerSource.MATRIX
        }

        return RiskAssessment(
            level = matrixLevel,
            probability = sigmoidProb,
            triggerSource = source,
            input = input
        )
    }

    fun evaluateMatrix(input: BiometricInput): RiskLevel {
        val (bpm, temp, gsr) = input

        if (bpm > CRIT_BPM_MIN && temp < CRIT_TEMP_MAX && gsr > CRIT_GSR_MIN) {
            return RiskLevel.CRITICAL_HIGH
        }

        if (bpm in MOD_BPM_MIN..MOD_BPM_MAX && temp > MOD_TEMP_MIN && gsr < MOD_GSR_MAX) {
            return RiskLevel.MODERATE_HIGH
        }

        if (bpm in OPT_BPM_MIN..OPT_BPM_MAX && temp in OPT_TEMP_MIN..OPT_TEMP_MAX && gsr in OPT_GSR_MIN..OPT_GSR_MAX) {
            return RiskLevel.OPTIMAL
        }

        return RiskLevel.MODERATE_HIGH
    }

    fun computeSigmoid(input: BiometricInput): Float {
        val normBpm = normalize(input.bpm, BPM_CENTER, BPM_SCALE)
        val normGsr = normalize(input.gsr, GSR_CENTER, GSR_SCALE)
        val normTemp = normalizeDeviation(input.temperature, TEMP_CENTER, TEMP_SCALE)
        val normBmi = normalize(input.bmi, BMI_CENTER, BMI_SCALE)

        val z = weights.bias +
                weights.bpm * normBpm +
                weights.gsr * normGsr +
                weights.temperature * normTemp +
                weights.bmi * normBmi

        return sigmoid(z)
    }

    private fun normalize(value: Float, center: Float, scale: Float): Float {
        return ((value - center) / scale).coerceIn(-1f, 2f)
    }

    private fun normalizeDeviation(value: Float, center: Float, scale: Float): Float {
        return (kotlin.math.abs(value - center) / scale).coerceIn(0f, 2f)
    }

    private fun sigmoid(z: Float): Float {
        return (1f / (1f + exp(-z))).coerceIn(0f, 1f)
    }

    companion object {
        const val OVERRIDE_THRESHOLD = 0.85f

        // Matrix thresholds
        const val CRIT_BPM_MIN = 110f
        const val CRIT_TEMP_MAX = 35.0f
        const val CRIT_GSR_MIN = 80f

        const val MOD_BPM_MIN = 95f
        const val MOD_BPM_MAX = 110f
        const val MOD_TEMP_MIN = 37.2f
        const val MOD_GSR_MAX = 20f

        const val OPT_BPM_MIN = 60f
        const val OPT_BPM_MAX = 80f
        const val OPT_TEMP_MIN = 36.0f
        const val OPT_TEMP_MAX = 36.7f
        const val OPT_GSR_MIN = 15f
        const val OPT_GSR_MAX = 35f

        // Sigmoid normalization centers and scales
        const val BPM_CENTER = 70f
        const val BPM_SCALE = 50f
        const val GSR_CENTER = 25f
        const val GSR_SCALE = 50f
        const val TEMP_CENTER = 36.5f
        const val TEMP_SCALE = 2f
        const val BMI_CENTER = 22f
        const val BMI_SCALE = 15f
    }
}
