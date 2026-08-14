package com.example.bioguard_wearos.presentation.screens.dashboard

import org.junit.Assert.assertEquals
import org.junit.Test

class DashboardStatusTest {

    @Test
    fun bpmDistingueAusenteOptimoModeradoYCritico() {
        assertEquals(MetricStatus.UNAVAILABLE, resolveBpmStatus(0f))
        assertEquals(MetricStatus.OPTIMAL, resolveBpmStatus(80f))
        assertEquals(MetricStatus.MODERATE, resolveBpmStatus(110f))
        assertEquals(MetricStatus.CRITICAL, resolveBpmStatus(180f))
    }

    @Test
    fun temperaturaDistingueAusenteOptimaModeradaYCritica() {
        assertEquals(MetricStatus.UNAVAILABLE, resolveTempStatus(0f))
        assertEquals(MetricStatus.OPTIMAL, resolveTempStatus(36.5f))
        assertEquals(MetricStatus.MODERATE, resolveTempStatus(37.2f))
        assertEquals(MetricStatus.CRITICAL, resolveTempStatus(39f))
    }

    @Test
    fun estresDistingueAusenteOptimoModeradoYCritico() {
        assertEquals(MetricStatus.UNAVAILABLE, resolveStressStatus(0f))
        assertEquals(MetricStatus.OPTIMAL, resolveStressStatus(20f))
        assertEquals(MetricStatus.MODERATE, resolveStressStatus(60f))
        assertEquals(MetricStatus.CRITICAL, resolveStressStatus(90f))
    }
}
