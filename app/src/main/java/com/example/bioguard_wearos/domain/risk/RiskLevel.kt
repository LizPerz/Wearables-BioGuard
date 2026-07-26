package com.example.bioguard_wearos.domain.risk

enum class RiskLevel(val label: String, val severity: Int) {
    OPTIMAL("Estado Óptimo", 0),
    MODERATE_HIGH("Riesgo Moderado Alto", 1),
    CRITICAL_HIGH("Riesgo Crítico Alto", 2);

    val isCritical get() = this == CRITICAL_HIGH
    val isElevated get() = severity >= 1
}
