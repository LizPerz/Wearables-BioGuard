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

    fun triggerAlert(assessment: RiskAssessment) {
        _alertState.value = AlertState(
            level = assessment.level,
            assessment = assessment,
            timestamp = System.currentTimeMillis()
        )
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
}
