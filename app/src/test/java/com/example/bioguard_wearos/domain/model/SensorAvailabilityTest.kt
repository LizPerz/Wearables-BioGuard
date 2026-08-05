package com.example.bioguard_wearos.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class SensorAvailabilityTest {

    @Test
    fun `default values are all false`() {
        val availability = SensorAvailability()
        assertEquals(false, availability.heartRateAvailable)
        assertEquals(false, availability.temperatureAvailable)
        assertEquals(false, availability.gsrAvailable)
    }

    @Test
    fun `can create with specific values`() {
        val availability = SensorAvailability(
            heartRateAvailable = true,
            temperatureAvailable = false,
            gsrAvailable = true
        )
        assertEquals(true, availability.heartRateAvailable)
        assertEquals(false, availability.temperatureAvailable)
        assertEquals(true, availability.gsrAvailable)
    }
}