package com.example.bioguard_wearos.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SensorDataTest {

    @Test
    fun `hrFraction calculates correctly for normal BPM`() {
        val data = SensorData(bpm = 120f)
        val expected = (120f - 40f) / (200f - 40f)
        assertEquals(expected, data.hrFraction, 0.001f)
    }

    @Test
    fun `hrFraction is 0_05 when BPM is 0`() {
        val data = SensorData(bpm = 0f)
        assertEquals(0.05f, data.hrFraction, 0.001f)
    }

    @Test
    fun `hrFraction is clamped between 0 and 1`() {
        val dataHigh = SensorData(bpm = 300f)
        assertEquals(1f, dataHigh.hrFraction, 0.001f)

        val dataLow = SensorData(bpm = 10f)
        assertEquals(0f, dataLow.hrFraction, 0.001f)
    }

    @Test
    fun `tempFraction calculates correctly`() {
        val data = SensorData(temperature = 36.5f)
        val expected = (36.5f - 35f) / (38f - 35f)
        assertEquals(expected, data.tempFraction, 0.001f)
    }

    @Test
    fun `tempFraction is 0_05 when temperature is 0`() {
        val data = SensorData(temperature = 0f)
        assertEquals(0.05f, data.tempFraction, 0.001f)
    }

    @Test
    fun `estresFraction calculates correctly`() {
        val data = SensorData(estresPct = 50f)
        assertEquals(0.5f, data.estresFraction, 0.001f)
    }

    @Test
    fun `estresFraction is 0_05 when estres is 0`() {
        val data = SensorData(estresPct = 0f)
        assertEquals(0.05f, data.estresFraction, 0.001f)
    }

    @Test
    fun `default values are all zero`() {
        val data = SensorData()
        assertEquals(0f, data.bpm)
        assertEquals(0f, data.temperature)
        assertEquals(0f, data.estresPct)
        assertEquals(0f, data.rmssd)
        assertEquals(0f, data.sdnn)
        assertEquals("", data.stressLabel)
    }

    @Test
    fun `new fields can be set`() {
        val data = SensorData(
            bpm = 72f,
            temperature = 36.5f,
            estresPct = 35f,
            rmssd = 28.5f,
            sdnn = 32.1f,
            stressLabel = "Estr\u00e9s Moderado"
        )
        assertEquals(28.5f, data.rmssd, 0.001f)
        assertEquals(32.1f, data.sdnn, 0.001f)
        assertEquals("Estr\u00e9s Moderado", data.stressLabel)
    }

    @Test
    fun `copy preserves new fields`() {
        val original = SensorData(rmssd = 15f, sdnn = 20f, stressLabel = "Estr\u00e9s Alto")
        val copied = original.copy(bpm = 100f)
        assertEquals(15f, copied.rmssd, 0.001f)
        assertEquals(20f, copied.sdnn, 0.001f)
        assertEquals("Estr\u00e9s Alto", copied.stressLabel)
    }

    @Test
    fun `estresFraction is clamped between 0 and 1`() {
        val dataHigh = SensorData(estresPct = 150f)
        assertEquals(1f, dataHigh.estresFraction, 0.001f)

        val dataLow = SensorData(estresPct = -5f)
        assertEquals(0.05f, dataLow.estresFraction, 0.001f)
    }

    @Test
    fun `all fraction helpers return 0_05 for zero input`() {
        val data = SensorData()
        assertEquals(0.05f, data.hrFraction, 0.001f)
        assertEquals(0.05f, data.tempFraction, 0.001f)
        assertEquals(0.05f, data.estresFraction, 0.001f)
    }

    @Test
    fun `biological ranges constants are correct`() {
        assertEquals(40f, SensorData.HR_MIN)
        assertEquals(200f, SensorData.HR_MAX)
        assertEquals(35f, SensorData.TEMP_MIN)
        assertEquals(38f, SensorData.TEMP_MAX)
        assertEquals(100f, SensorData.ESTRES_MAX)
    }
}
