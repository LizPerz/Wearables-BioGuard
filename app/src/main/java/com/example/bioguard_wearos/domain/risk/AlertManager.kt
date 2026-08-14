package com.example.bioguard_wearos.domain.risk

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

data class AlertState(
    val level: RiskLevel = RiskLevel.OPTIMAL,
    val assessment: RiskAssessment = RiskAssessment(),
    val timestamp: Long = 0L,
    val dismissed: Boolean = false,
    val helpRequested: Boolean = false
) {
    val isActive: Boolean get() = level.isCritical && !dismissed && !helpRequested
}

@Singleton
class AlertManager @Inject constructor() {

    private val _alertState = MutableStateFlow(AlertState())
    val alertState: StateFlow<AlertState> = _alertState.asStateFlow()

    @Volatile
    private var lastAlertLevel: RiskLevel? = null
    @Volatile
    private var lastAlertTimestamp = 0L

    /**
     * Política de cooldown/escalada para alertas del wearable, espejo de la del móvil
     * (LocalNotificationPolicy): una alerta nueva del mismo nivel o inferior solo se
     * vuelve a disparar tras el cooldown; una escalada (o la primera alerta) siempre pasa.
     */
    fun shouldNotify(level: RiskLevel, nowMillis: Long = System.currentTimeMillis()): Boolean {
        if (!level.isElevated) return false
        val last = lastAlertLevel
        if (last == null || level.severity > last.severity) return true
        val cooldown = if (level.isCritical) CRITICAL_COOLDOWN_MILLIS else HIGH_COOLDOWN_MILLIS
        return nowMillis - lastAlertTimestamp >= cooldown
    }

    fun recordNotification(level: RiskLevel, nowMillis: Long = System.currentTimeMillis()) {
        lastAlertLevel = level
        lastAlertTimestamp = nowMillis
    }

    fun triggerAlert(assessment: RiskAssessment, force: Boolean = false): Boolean {
        val now = System.currentTimeMillis()
        if (!force && !shouldNotify(assessment.level, now)) {
            return false
        }
        lastAlertLevel = assessment.level
        lastAlertTimestamp = now
        _alertState.value = AlertState(
            level = assessment.level,
            assessment = assessment,
            timestamp = now
        )
        return true
    }

    fun dismissAlert() {
        _alertState.value = _alertState.value.copy(dismissed = true)
    }

    fun requestHelp() {
        _alertState.value = _alertState.value.copy(helpRequested = true)
    }

    fun clear() {
        _alertState.value = AlertState()
    }

    companion object {
        const val HIGH_COOLDOWN_MILLIS = 15 * 60 * 1000L
        const val CRITICAL_COOLDOWN_MILLIS = 5 * 60 * 1000L
    }
}
