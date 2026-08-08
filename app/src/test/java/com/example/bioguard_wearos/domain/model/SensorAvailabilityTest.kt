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
        assertEquals(false, availability.heartRateOffBody)
        assertEquals(null, availability.statusMessage)
    }

    @Test
    fun `can create with specific values`() {
        val availability = SensorAvailability(
            heartRateAvailable = true,
            temperatureAvailable = false,
            gsrAvailable = true,
            heartRateOffBody = true,
            statusMessage = "Coloca el reloj"
        )
        assertEquals(true, availability.heartRateAvailable)
        assertEquals(false, availability.temperatureAvailable)
        assertEquals(true, availability.gsrAvailable)
        assertEquals(true, availability.heartRateOffBody)
        assertEquals("Coloca el reloj", availability.statusMessage)
    }
}
