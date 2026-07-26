package com.example.bioguard_wearos.data.sensor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TemperatureCorrelationTest {

    private fun computeTemperature(bpm: Float, simTime: Float): Float {
        val hrFactor = ((bpm - 50f) / 150f).coerceIn(0f, 1f)
        val baseTemp = 36.2f + hrFactor * 1.3f
        val oscillation = kotlin.math.sin(simTime * 0.5f) * 0.15f
        val temp = (baseTemp + oscillation).coerceIn(35.5f, 37.8f)
        return temp
    }

    @Test
    fun `temperature stays within physiological range for all BPM values`() {
        val extremeBpms = listOf(30f, 40f, 60f, 80f, 100f, 120f, 150f, 180f, 200f, 250f)
        for (bpm in extremeBpms) {
            val temp = computeTemperature(bpm, 0f)
            assertTrue(
                "Temp for BPM=$bpm should be in [35.5, 37.8], was $temp",
                temp in 35.5f..37.8f
            )
        }
    }

    @Test
    fun `low BPM produces lower temperature`() {
        val tempLow = computeTemperature(50f, 0f)
        val tempHigh = computeTemperature(150f, 0f)
        assertTrue("Low BPM ($tempLow) should produce lower temp than high BPM ($tempHigh)", tempLow < tempHigh)
    }

    @Test
    fun `BPM 50 maps to base temperature 36_2`() {
        val temp = computeTemperature(50f, 0f)
        assertTrue("BPM=50 should produce ~36.2C (before oscillation), was $temp", temp in 36.0f..36.4f)
    }

    @Test
    fun `BPM 200 maps to max HR factor temperature`() {
        val temp = computeTemperature(200f, 0f)
        assertTrue("BPM=200 should produce ~37.5C, was $temp", temp in 37.0f..37.8f)
    }

    @Test
    fun `BPM below 50 clamps HR factor to 0`() {
        val tempLow = computeTemperature(50f, 0f)
        val tempBelow = computeTemperature(30f, 0f)
        assertEquals("BPM below 50 should clamp to same as 50", tempLow, tempBelow, 0.01f)
    }

    @Test
    fun `BPM above 200 clamps HR factor to 1`() {
        val temp200 = computeTemperature(200f, 0f)
        val temp250 = computeTemperature(250f, 0f)
        assertEquals("BPM above 200 should clamp to same as 200", temp200, temp250, 0.01f)
    }

    @Test
    fun `oscillation produces small temperature variation`() {
        val temps = (0..20).map { computeTemperature(70f, it * 0.4f) }
        val maxTemp = temps.max()
        val minTemp = temps.min()
        val variation = maxTemp - minTemp
        assertTrue("Oscillation should produce variation < 0.5C, was $variation", variation < 0.5f)
    }

    @Test
    fun `temperature does not jump abruptly between readings`() {
        val bpmSequence = listOf(60f, 65f, 70f, 120f, 125f, 60f)
        var prevTemp = computeTemperature(bpmSequence[0], 0f)
        for (i in 1 until bpmSequence.size) {
            val currentTemp = computeTemperature(bpmSequence[i], i * 0.4f)
            val delta = kotlin.math.abs(currentTemp - prevTemp)
            assertTrue(
                "Temp jump from ${bpmSequence[i - 1]} to ${bpmSequence[i]} BPM should be < 2C, was $delta",
                delta < 2f
            )
            prevTemp = currentTemp
        }
    }

    @Test
    fun `temperature range is exactly 35_5 to 37_8`() {
        for (t in 0..100) {
            val temp = computeTemperature(120f, t * 0.1f)
            assertTrue("Temp at t=$t should be in [35.5, 37.8], was $temp", temp in 35.5f..37.8f)
        }
    }

    @Test
    fun `steady BPM produces stable temperature`() {
        val temps = (0..20).map { computeTemperature(70f, 0f) }
        val allSame = temps.all { it == temps[0] }
        assertTrue("Same BPM and time should produce same temperature", allSame)
    }
}
