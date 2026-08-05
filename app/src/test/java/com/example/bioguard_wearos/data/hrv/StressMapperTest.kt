package com.example.bioguard_wearos.data.hrv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class StressMapperTest {

    private lateinit var mapper: StressMapper

    @Before
    fun setUp() {
        mapper = StressMapper()
    }

    @Test
    fun `low RMSSD maps to high stress`() {
        val stress = mapper.mapToStressUs(5f)
        assertTrue("RMSSD=5ms should map to high stress (>60), was $stress", stress >= 60f)
    }

    @Test
    fun `high RMSSD maps to low stress`() {
        for (i in 1..15) mapper.mapToStressUs(80f)
        val stress = mapper.mapToStressUs(80f)
        assertTrue("RMSSD=80ms should map to low stress (<25), was $stress", stress < 25f)
    }

    @Test
    fun `moderate RMSSD maps to moderate stress`() {
        for (i in 1..15) mapper.mapToStressUs(30f)
        val stress = mapper.mapToStressUs(30f)
        assertTrue("RMSSD=30ms should map to moderate stress (25-60), was $stress", stress in 25f..60f)
    }

    @Test
    fun `stress output is always within valid range for positive RMSSD`() {
        val testRmssds = listOf(5f, 10f, 15f, 20f, 25f, 30f, 35f, 40f, 50f, 60f, 80f, 100f)
        for (rmssd in testRmssds) {
            val stress = StressMapper().mapToStressUs(rmssd)
            assertTrue("Stress for RMSSD=$rmssd should be in [1, 100], was $stress", stress in 1f..100f)
        }
    }

    @Test
    fun `smoothing reduces jitter between consecutive readings`() {
        val m1 = StressMapper()
        val values = mutableListOf<Float>()
        for (i in 1..20) {
            values.add(m1.mapToStressUs(30f))
        }
        val last5 = values.takeLast(5)
        val range = last5.max() - last5.min()
        assertTrue("Smoothed values should stabilize, range was $range", range < 5f)
    }

    @Test
    fun `first reading initializes smoothing without delay`() {
        val stress = mapper.mapToStressUs(30f)
        assertTrue("First reading should produce valid output, was $stress", stress in 1f..100f)
    }

    @Test
    fun `zero RMSSD returns previous smoothed value or zero`() {
        val stress = mapper.mapToStressUs(0f)
        assertTrue("Zero RMSSD should return >= 0, was $stress", stress >= 0f)
    }

    @Test
    fun `getStressLabel returns correct label for high stress`() {
        val label = mapper.getStressLabel(75f)
        assertEquals("Estr\u00e9s Alto", label)
    }

    @Test
    fun `getStressLabel returns correct label for moderate stress`() {
        val label = mapper.getStressLabel(40f)
        assertEquals("Estr\u00e9s Moderado", label)
    }

    @Test
    fun `getStressLabel returns correct label for relaxed state`() {
        val label = mapper.getStressLabel(15f)
        assertEquals("Relajado", label)
    }

    @Test
    fun `getStressLabel boundaries are correct`() {
        assertEquals("Relajado", mapper.getStressLabel(24f))
        assertEquals("Estr\u00e9s Moderado", mapper.getStressLabel(25f))
        assertEquals("Estr\u00e9s Moderado", mapper.getStressLabel(59f))
        assertEquals("Estr\u00e9s Alto", mapper.getStressLabel(60f))
    }

    @Test
    fun `reset clears smoothed state`() {
        for (i in 1..10) mapper.mapToStressUs(20f)
        mapper.reset()
        val stress = mapper.mapToStressUs(80f)
        assertTrue("After reset, mapper should produce fresh output, was $stress", stress in 1f..100f)
    }

    @Test
    fun `stress output is monotonically related to RMSSD`() {
        val mapper2 = StressMapper()
        val lowStress = mapper2.mapToStressUs(60f)
        val mapper3 = StressMapper()
        val highStress = mapper3.mapToStressUs(10f)
        assertTrue("Low RMSSD should produce higher stress than high RMSSD", highStress > lowStress)
    }

    @Test
    fun `extreme RMSSD values do not crash`() {
        val stress1 = StressMapper().mapToStressUs(500f)
        assertTrue("Very high RMSSD should still produce valid output", stress1 in 1f..100f)

        val stress2 = StressMapper().mapToStressUs(-5f)
        assertTrue("Negative RMSSD should still produce valid output", stress2 >= 0f)
    }
}
