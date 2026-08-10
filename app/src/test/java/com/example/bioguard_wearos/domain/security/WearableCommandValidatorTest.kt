package com.example.bioguard_wearos.domain.security

import com.example.bioguard_wearos.domain.risk.RiskThresholds
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WearableCommandValidatorTest {
    @Test
    fun `accepts bounded alert and rejects non finite values`() {
        assertTrue(WearableCommandValidator.isValidAlert(72f, 36.5f, 1f, 0.7f))
        assertFalse(WearableCommandValidator.isValidAlert(Float.NaN, 36.5f, 1f, 0.7f))
        assertFalse(WearableCommandValidator.isValidAlert(72f, 36.5f, 1f, 1.1f))
    }

    @Test
    fun `requires coherent risk thresholds`() {
        assertTrue(WearableCommandValidator.isValidThresholds(RiskThresholds.DEFAULT))
        assertFalse(WearableCommandValidator.isValidThresholds(RiskThresholds(criticalBpmHigh = 90f, moderateBpm = 100f)))
    }

    @Test
    fun `accepts only durable telemetry acknowledgements`() {
        assertTrue(WearableCommandValidator.isValidAckPath("/bioguard/telemetry/42"))
        assertFalse(WearableCommandValidator.isValidAckPath("/commands/dismiss"))
        assertFalse(WearableCommandValidator.isValidAckPath("/bioguard/telemetry/../../42"))
    }
}
