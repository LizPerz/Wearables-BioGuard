package com.example.bioguard_wearos.domain.risk

data class RiskAssessment(
    val level: RiskLevel = RiskLevel.OPTIMAL,
    val probability: Float = 0f,
    val triggerSource: TriggerSource = TriggerSource.NONE,
    val input: BiometricInput = BiometricInput()
)

enum class TriggerSource(val label: String) {
    NONE("Sin evaluación"),
    MATRIX("Matriz de Riesgo Directa"),
    SIGMOID("Modelo Probabilístico (Sigmoide)"),
    OVERRIDE("Sobreescribir por Sigmoide > 0.85")
}
