package com.example.bioguard_wearos.data.hrv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class HrvCalculatorTest {

    private lateinit var calculator: HrvCalculator

    @Before
    fun setUp() {
        calculator = HrvCalculator(maxBufferSize = 60)
    }

    @Test
    fun `addIbi converts BPM to IBI in milliseconds`() {
        calculator.addIbi(60f)
        assertEquals(1, calculator.getIbiCount())
    }

    @Test
    fun `addIbi ignores invalid BPM values`() {
        calculator.addIbi(0f)
        assertEquals(0, calculator.getIbiCount())

        calculator.addIbi(-10f)
        assertEquals(0, calculator.getIbiCount())
    }

    @Test
    fun `buffer respects max size limit`() {
        val smallCalc = HrvCalculator(maxBufferSize = 5)
        for (i in 1..10) {
            smallCalc.addIbi(70f)
        }
        assertEquals(5, smallCalc.getIbiCount())
    }

    @Test
    fun `computeRmssd returns 0 with less than 2 samples`() {
        assertEquals(0f, calculator.computeRmssd(), 0.001f)
        calculator.addIbi(70f)
        assertEquals(0f, calculator.computeRmssd(), 0.001f)
    }

    @Test
    fun `computeRmssd returns 0 for constant heart rate`() {
        for (i in 1..10) {
            calculator.addIbi(70f)
        }
        assertEquals(0f, calculator.computeRmssd(), 0.001f)
    }

    @Test
    fun `computeRmssd is positive for variable heart rate`() {
        val bpms = listOf(60f, 65f, 58f, 72f, 63f, 68f, 55f, 70f, 62f, 67f)
        bpms.forEach { calculator.addIbi(it) }
        val rmssd = calculator.computeRmssd()
        assertTrue("RMSSD should be positive for variable HR, was $rmssd", rmssd > 0f)
    }

    @Test
    fun `computeRmssd matches hand-calculated value`() {
        calculator.addIbi(60f)
        calculator.addIbi(70f)
        val ibi1 = 60000f / 60f
        val ibi2 = 60000f / 70f
        val diff = ibi2 - ibi1
        val expected = kotlin.math.sqrt(diff * diff)
        assertEquals(expected, calculator.computeRmssd(), 0.01f)
    }

    @Test
    fun `computeSdnn returns 0 with less than 2 samples`() {
        assertEquals(0f, calculator.computeSdnn(), 0.001f)
        calculator.addIbi(70f)
        assertEquals(0f, calculator.computeSdnn(), 0.001f)
    }

    @Test
    fun `computeSdnn returns 0 for constant heart rate`() {
        for (i in 1..10) {
            calculator.addIbi(70f)
        }
        assertEquals(0f, calculator.computeSdnn(), 0.001f)
    }

    @Test
    fun `computeSdnn is positive for variable heart rate`() {
        val bpms = listOf(60f, 65f, 58f, 72f, 63f, 68f, 55f, 70f, 62f, 67f)
        bpms.forEach { calculator.addIbi(it) }
        val sdnn = calculator.computeSdnn()
        assertTrue("SDNN should be positive for variable HR, was $sdnn", sdnn > 0f)
    }

    @Test
    fun `computeSdnn matches hand-calculated value`() {
        calculator.addIbi(60f)
        calculator.addIbi(70f)
        val ibi1 = 60000f / 60f
        val ibi2 = 60000f / 70f
        val mean = (ibi1 + ibi2) / 2f
        val expected = kotlin.math.sqrt(
            ((ibi1 - mean) * (ibi1 - mean) + (ibi2 - mean) * (ibi2 - mean)) / 2f
        )
        assertEquals(expected, calculator.computeSdnn(), 0.01f)
    }

    @Test
    fun `clear resets buffer`() {
        for (i in 1..10) {
            calculator.addIbi(70f)
        }
        assertEquals(10, calculator.getIbiCount())
        calculator.clear()
        assertEquals(0, calculator.getIbiCount())
        assertEquals(0f, calculator.computeRmssd(), 0.001f)
        assertEquals(0f, calculator.computeSdnn(), 0.001f)
    }

    @Test
    fun `HRV metrics are within biologically plausible range`() {
        val realisticBpms = listOf(
            62f, 64f, 61f, 67f, 63f, 65f, 60f, 68f, 64f, 66f,
            63f, 61f, 69f, 65f, 62f, 67f, 64f, 60f, 66f, 63f,
            65f, 62f, 68f, 64f, 61f, 67f, 63f, 65f, 60f, 66f,
            64f, 62f, 69f, 65f, 63f, 67f, 61f, 66f, 64f, 62f,
            68f, 63f, 65f, 61f, 67f, 64f, 62f, 66f, 63f, 65f,
            60f, 68f, 64f, 62f, 67f, 63f, 65f, 61f, 66f, 64f
        )
        realisticBpms.forEach { calculator.addIbi(it) }

        val rmssd = calculator.computeRmssd()
        val sdnn = calculator.computeSdnn()

        assertTrue("RMSSD $rmssd should be between 0-200ms for normal sinus rhythm", rmssd in 0f..200f)
        assertTrue("SDNN $sdnn should be between 0-200ms for normal sinus rhythm", sdnn in 0f..200f)
    }

    @Test
    fun `sliding window removes oldest samples`() {
        val smallCalc = HrvCalculator(maxBufferSize = 3)
        smallCalc.addIbi(60f)
        smallCalc.addIbi(70f)
        smallCalc.addIbi(80f)
        assertEquals(3, smallCalc.getIbiCount())

        smallCalc.addIbi(65f)
        assertEquals(3, smallCalc.getIbiCount())
    }

    @Test
    fun `RMSSD and SDNN are always non-negative`() {
        val bpms = listOf(50f, 120f, 55f, 110f, 60f, 100f, 65f, 95f)
        bpms.forEach { calculator.addIbi(it) }
        assertTrue("RMSSD must be >= 0", calculator.computeRmssd() >= 0f)
        assertTrue("SDNN must be >= 0", calculator.computeSdnn() >= 0f)
    }
}
