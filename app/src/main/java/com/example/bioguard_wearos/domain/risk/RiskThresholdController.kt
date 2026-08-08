package com.example.bioguard_wearos.domain.risk

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RiskThresholdController @Inject constructor() {

    private val _thresholds = MutableStateFlow(RiskThresholds.DEFAULT)
    val thresholds: StateFlow<RiskThresholds> = _thresholds.asStateFlow()

    val current: RiskThresholds
        get() = _thresholds.value

    fun update(thresholds: RiskThresholds) {
        if (thresholds != _thresholds.value) {
            _thresholds.value = thresholds
        }
    }

    fun reset() {
        _thresholds.value = RiskThresholds.DEFAULT
    }
}
