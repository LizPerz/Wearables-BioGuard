package com.example.bioguard_wearos.domain.risk

data class RiskAssessment(
    val level: RiskLevel = RiskLevel.OPTIMAL,
    val probability: Float = 0f,
    val triggerSource: TriggerSource = TriggerSource.NONE,
    val input: BiometricInput = BiometricInput()
) {
    companion object {
        fun fromBiometrics(
            bpm: Float,
            temp: Float,
            estresPct: Float,
            thresholds: RiskThresholds = RiskThresholds.DEFAULT
        ): RiskAssessment {
            val input = BiometricInput(bpm = bpm, temperature = temp, estresPct = estresPct)
            val moderateSignals = listOf(
                bpm >= thresholds.moderateBpm,
                temp >= thresholds.moderateTemp,
                estresPct >= thresholds.moderateStress
            ).count { it }

            return when {
                bpm >= thresholds.criticalBpmHigh ||
                    (bpm in 1f..thresholds.criticalBpmLow) ||
                    temp >= thresholds.criticalTemp ||
                    moderateSignals >= 2 -> {
                    RiskAssessment(
                        level = RiskLevel.CRITICAL_HIGH,
                        probability = 0.92f,
                        triggerSource = TriggerSource.OVERRIDE,
                        input = input
                    )
                }
                moderateSignals >= 1 -> {
                    RiskAssessment(
                        level = RiskLevel.MODERATE_HIGH,
                        probability = 0.65f,
                        triggerSource = TriggerSource.MATRIX,
                        input = input
                    )
                }
                else -> {
                    RiskAssessment(
                        level = RiskLevel.OPTIMAL,
                        probability = 0.10f,
                        triggerSource = TriggerSource.NONE,
                        input = input
                    )
                }
            }
        }
    }
}

enum class TriggerSource(val label: String) {
    NONE("Sin evaluación"),
    MATRIX("Matriz de Riesgo Directa"),
    SIGMOID("Modelo Probabilístico (Sigmoide)"),
    OVERRIDE("Sobreescribir por Sigmoide > 0.85")
}

