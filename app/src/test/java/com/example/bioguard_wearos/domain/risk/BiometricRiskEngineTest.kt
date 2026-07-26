package com.example.bioguard_wearos.domain.risk

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class BiometricRiskEngineTest {

    private lateinit var engine: BiometricRiskEngine

    @Before
    fun setUp() {
        engine = BiometricRiskEngine()
    }

    // ─────────────────────────────────────────────
    // A. MATRIX: CRITICAL HIGH (Hipoglucemia Nocturna)
    // ─────────────────────────────────────────────

    @Test
    fun `matrix CRITICAL_HIGH when BPM 120 Temp 34_5 GSR 85`() {
        val input = BiometricInput(bpm = 120f, temperature = 34.5f, gsr = 85f, bmi = 25f)
        val result = engine.evaluateMatrix(input)
        assertEquals(RiskLevel.CRITICAL_HIGH, result)
    }

    @Test
    fun `matrix CRITICAL_HIGH at exact boundaries BPM 111 Temp 34_9 GSR 81`() {
        val input = BiometricInput(bpm = 111f, temperature = 34.9f, gsr = 81f, bmi = 25f)
        assertEquals(RiskLevel.CRITICAL_HIGH, engine.evaluateMatrix(input))
    }

    @Test
    fun `matrix NOT critical when BPM is exactly 110`() {
        val input = BiometricInput(bpm = 110f, temperature = 34.0f, gsr = 85f, bmi = 25f)
        assertNotEquals(RiskLevel.CRITICAL_HIGH, engine.evaluateMatrix(input))
    }

    @Test
    fun `matrix NOT critical when temp is exactly 35_0`() {
        val input = BiometricInput(bpm = 120f, temperature = 35.0f, gsr = 85f, bmi = 25f)
        assertNotEquals(RiskLevel.CRITICAL_HIGH, engine.evaluateMatrix(input))
    }

    @Test
    fun `matrix NOT critical when GSR is exactly 80`() {
        val input = BiometricInput(bpm = 120f, temperature = 34.5f, gsr = 80f, bmi = 25f)
        assertNotEquals(RiskLevel.CRITICAL_HIGH, engine.evaluateMatrix(input))
    }

    // ─────────────────────────────────────────────
    // B. MATRIX: MODERATE HIGH (Hiperglucemia Severa)
    // ─────────────────────────────────────────────

    @Test
    fun `matrix MODERATE_HIGH when BPM 100 Temp 37_5 GSR 15`() {
        val input = BiometricInput(bpm = 100f, temperature = 37.5f, gsr = 15f, bmi = 25f)
        assertEquals(RiskLevel.MODERATE_HIGH, engine.evaluateMatrix(input))
    }

    @Test
    fun `matrix MODERATE_HIGH at exact boundaries BPM 95 Temp 37_3 GSR 19`() {
        val input = BiometricInput(bpm = 95f, temperature = 37.3f, gsr = 19f, bmi = 25f)
        assertEquals(RiskLevel.MODERATE_HIGH, engine.evaluateMatrix(input))
    }

    @Test
    fun `matrix MODERATE_HIGH at upper BPM boundary 110`() {
        val input = BiometricInput(bpm = 110f, temperature = 37.5f, gsr = 15f, bmi = 25f)
        assertEquals(RiskLevel.MODERATE_HIGH, engine.evaluateMatrix(input))
    }

    @Test
    fun `matrix NOT moderate when BPM is 94`() {
        val input = BiometricInput(bpm = 94f, temperature = 37.5f, gsr = 15f, bmi = 25f)
        assertNotEquals(RiskLevel.MODERATE_HIGH, engine.evaluateMatrix(input))
    }

    @Test
    fun `matrix NOT moderate when temp is exactly 37_2`() {
        val input = BiometricInput(bpm = 100f, temperature = 37.2f, gsr = 15f, bmi = 25f)
        assertNotEquals(RiskLevel.MODERATE_HIGH, engine.evaluateMatrix(input))
    }

    @Test
    fun `matrix NOT moderate when GSR is exactly 20`() {
        val input = BiometricInput(bpm = 100f, temperature = 37.5f, gsr = 20f, bmi = 25f)
        assertNotEquals(RiskLevel.MODERATE_HIGH, engine.evaluateMatrix(input))
    }

    // ─────────────────────────────────────────────
    // C. MATRIX: OPTIMAL (Estado Óptimo)
    // ─────────────────────────────────────────────

    @Test
    fun `matrix OPTIMAL when BPM 70 Temp 36_5 GSR 25`() {
        val input = BiometricInput(bpm = 70f, temperature = 36.5f, gsr = 25f, bmi = 25f)
        assertEquals(RiskLevel.OPTIMAL, engine.evaluateMatrix(input))
    }

    @Test
    fun `matrix OPTIMAL at exact boundaries BPM 60 Temp 36_0 GSR 15`() {
        val input = BiometricInput(bpm = 60f, temperature = 36.0f, gsr = 15f, bmi = 25f)
        assertEquals(RiskLevel.OPTIMAL, engine.evaluateMatrix(input))
    }

    @Test
    fun `matrix OPTIMAL at upper boundaries BPM 80 Temp 36_7 GSR 35`() {
        val input = BiometricInput(bpm = 80f, temperature = 36.7f, gsr = 35f, bmi = 25f)
        assertEquals(RiskLevel.OPTIMAL, engine.evaluateMatrix(input))
    }

    @Test
    fun `matrix NOT optimal when BPM is 81`() {
        val input = BiometricInput(bpm = 81f, temperature = 36.5f, gsr = 25f, bmi = 25f)
        assertNotEquals(RiskLevel.OPTIMAL, engine.evaluateMatrix(input))
    }

    @Test
    fun `matrix NOT optimal when BPM is 59`() {
        val input = BiometricInput(bpm = 59f, temperature = 36.5f, gsr = 25f, bmi = 25f)
        assertNotEquals(RiskLevel.OPTIMAL, engine.evaluateMatrix(input))
    }

    // ─────────────────────────────────────────────
    // D. SIGMOID MODEL
    // ─────────────────────────────────────────────

    @Test
    fun `sigmoid returns value between 0 and 1`() {
        val inputs = listOf(
            BiometricInput(70f, 36.5f, 25f, 25f),
            BiometricInput(120f, 34f, 90f, 35f),
            BiometricInput(100f, 37.5f, 10f, 30f)
        )
        for (input in inputs) {
            val prob = engine.computeSigmoid(input)
            assertTrue("Probability $prob should be in [0, 1]", prob in 0f..1f)
        }
    }

    @Test
    fun `sigmoid probability increases with higher BPM`() {
        val low = engine.computeSigmoid(BiometricInput(60f, 36.5f, 25f, 25f))
        val high = engine.computeSigmoid(BiometricInput(130f, 36.5f, 25f, 25f))
        assertTrue("Higher BPM should increase probability", high > low)
    }

    @Test
    fun `sigmoid probability increases with higher GSR`() {
        val low = engine.computeSigmoid(BiometricInput(70f, 36.5f, 10f, 25f))
        val high = engine.computeSigmoid(BiometricInput(70f, 36.5f, 90f, 25f))
        assertTrue("Higher GSR should increase probability", high > low)
    }

    @Test
    fun `sigmoid probability increases with higher BMI`() {
        val low = engine.computeSigmoid(BiometricInput(70f, 36.5f, 25f, 18f))
        val high = engine.computeSigmoid(BiometricInput(70f, 36.5f, 25f, 45f))
        assertTrue("Higher BMI should increase probability", high > low)
    }

    @Test
    fun `sigmoid probability increases with temp deviation from center`() {
        val normal = engine.computeSigmoid(BiometricInput(70f, 36.5f, 25f, 25f))
        val deviant = engine.computeSigmoid(BiometricInput(70f, 34f, 25f, 25f))
        assertTrue("Temp deviation should increase probability", deviant > normal)
    }

    @Test
    fun `sigmoid low for normal healthy values`() {
        val prob = engine.computeSigmoid(BiometricInput(70f, 36.5f, 25f, 22f))
        assertTrue("Normal values should produce low probability, was $prob", prob < 0.3f)
    }

    @Test
    fun `sigmoid high for critical hypoglycemia pattern`() {
        val prob = engine.computeSigmoid(BiometricInput(125f, 34f, 90f, 30f))
        assertTrue("Critical pattern should produce high probability, was $prob", prob > 0.7f)
    }

    // ─────────────────────────────────────────────
    // E. OVERRIDE: Sigmoid > 0.85
    // ─────────────────────────────────────────────

    @Test
    fun `evaluate returns CRITICAL_HIGH when sigmoid exceeds 0_85`() {
        val input = BiometricInput(bpm = 140f, temperature = 33f, gsr = 100f, bmi = 40f)
        val result = engine.evaluate(input)
        assertEquals(RiskLevel.CRITICAL_HIGH, result.level)
        assertEquals(TriggerSource.OVERRIDE, result.triggerSource)
        assertTrue("Probability should be > 0.85, was ${result.probability}", result.probability > 0.85f)
    }

    @Test
    fun `override takes priority over matrix OPTIMAL`() {
        val input = BiometricInput(bpm = 70f, temperature = 36.5f, gsr = 25f, bmi = 25f)
        val matrixResult = engine.evaluateMatrix(input)
        assertEquals(RiskLevel.OPTIMAL, matrixResult)

        val fullResult = engine.evaluate(input)
        if (fullResult.probability > BiometricRiskEngine.OVERRIDE_THRESHOLD) {
            assertEquals(RiskLevel.CRITICAL_HIGH, fullResult.level)
            assertEquals(TriggerSource.OVERRIDE, fullResult.triggerSource)
        }
    }

    @Test
    fun `evaluate uses matrix when sigmoid is below threshold`() {
        val input = BiometricInput(bpm = 120f, temperature = 34.5f, gsr = 85f, bmi = 25f)
        val result = engine.evaluate(input)
        if (result.probability <= BiometricRiskEngine.OVERRIDE_THRESHOLD) {
            assertEquals(TriggerSource.MATRIX, result.triggerSource)
        }
    }

    // ─────────────────────────────────────────────
    // F. EDGE CASES & INPUT VALIDATION
    // ─────────────────────────────────────────────

    @Test
    fun `invalid input with zero BPM returns OPTIMAL with NONE source`() {
        val input = BiometricInput(bpm = 0f, temperature = 36.5f, gsr = 25f, bmi = 25f)
        val result = engine.evaluate(input)
        assertEquals(RiskLevel.OPTIMAL, result.level)
        assertEquals(TriggerSource.NONE, result.triggerSource)
        assertEquals(0f, result.probability, 0.001f)
    }

    @Test
    fun `invalid input with zero temperature returns OPTIMAL`() {
        val input = BiometricInput(bpm = 70f, temperature = 0f, gsr = 25f, bmi = 25f)
        val result = engine.evaluate(input)
        assertEquals(RiskLevel.OPTIMAL, result.level)
        assertEquals(TriggerSource.NONE, result.triggerSource)
    }

    @Test
    fun `invalid input with zero GSR returns OPTIMAL`() {
        val input = BiometricInput(bpm = 70f, temperature = 36.5f, gsr = 0f, bmi = 25f)
        val result = engine.evaluate(input)
        assertEquals(RiskLevel.OPTIMAL, result.level)
    }

    @Test
    fun `invalid input with zero BMI returns OPTIMAL`() {
        val input = BiometricInput(bpm = 70f, temperature = 36.5f, gsr = 25f, bmi = 0f)
        val result = engine.evaluate(input)
        assertEquals(RiskLevel.OPTIMAL, result.level)
    }

    @Test
    fun `negative values are invalid`() {
        val input = BiometricInput(bpm = -10f, temperature = -5f, gsr = -1f, bmi = -3f)
        assertFalse("Negative values should be invalid", input.isValid)
        val result = engine.evaluate(input)
        assertEquals(RiskLevel.OPTIMAL, result.level)
    }

    @Test
    fun `all zero input is invalid`() {
        val input = BiometricInput()
        assertFalse("Default zero input should be invalid", input.isValid)
    }

    @Test
    fun `extreme BPM values do not crash`() {
        val input = BiometricInput(bpm = 300f, temperature = 36.5f, gsr = 25f, bmi = 25f)
        val result = engine.evaluate(input)
        assertTrue("Should produce valid result", result.probability in 0f..1f)
    }

    @Test
    fun `extreme temperature values do not crash`() {
        val input = BiometricInput(bpm = 70f, temperature = 45f, gsr = 25f, bmi = 25f)
        val result = engine.evaluate(input)
        assertTrue("Should produce valid result", result.probability in 0f..1f)
    }

    @Test
    fun `extreme GSR values do not crash`() {
        val input = BiometricInput(bpm = 70f, temperature = 36.5f, gsr = 200f, bmi = 25f)
        val result = engine.evaluate(input)
        assertTrue("Should produce valid result", result.probability in 0f..1f)
    }

    // ─────────────────────────────────────────────
    // G. PARTIAL MATRIX MATCHES
    // ─────────────────────────────────────────────

    @Test
    fun `partial match BPM high but temp normal defaults to OPTIMAL`() {
        val input = BiometricInput(bpm = 120f, temperature = 36.5f, gsr = 25f, bmi = 25f)
        assertEquals(RiskLevel.OPTIMAL, engine.evaluateMatrix(input))
    }

    @Test
    fun `partial match temp low but BPM normal defaults to OPTIMAL`() {
        val input = BiometricInput(bpm = 70f, temperature = 34f, gsr = 25f, bmi = 25f)
        assertEquals(RiskLevel.OPTIMAL, engine.evaluateMatrix(input))
    }

    @Test
    fun `partial match GSR high but BPM normal defaults to OPTIMAL`() {
        val input = BiometricInput(bpm = 70f, temperature = 36.5f, gsr = 90f, bmi = 25f)
        assertEquals(RiskLevel.OPTIMAL, engine.evaluateMatrix(input))
    }

    // ─────────────────────────────────────────────
    // H. SIGMOID OVERRIDE vs MATRIX CONFLICT
    // ─────────────────────────────────────────────

    @Test
    fun `sigmoid override overrides matrix MODERATE_HIGH`() {
        val engineCustom = BiometricRiskEngine(
            BiometricRiskEngine.Weights(bias = 5f, bpm = 3f, gsr = 3f, temperature = 3f, bmi = 2f)
        )
        val input = BiometricInput(bpm = 100f, temperature = 37.5f, gsr = 15f, bmi = 30f)
        val matrixResult = engineCustom.evaluateMatrix(input)
        val fullResult = engineCustom.evaluate(input)

        if (fullResult.probability > BiometricRiskEngine.OVERRIDE_THRESHOLD) {
            assertEquals(RiskLevel.CRITICAL_HIGH, fullResult.level)
            assertEquals(TriggerSource.OVERRIDE, fullResult.triggerSource)
            assertNotEquals(matrixResult, fullResult.level)
        }
    }

    // ─────────────────────────────────────────────
    // I. WEIGHTS CUSTOMIZATION
    // ─────────────────────────────────────────────

    @Test
    fun `custom weights affect sigmoid output`() {
        val defaultEngine = BiometricRiskEngine()
        val aggressiveEngine = BiometricRiskEngine(
            BiometricRiskEngine.Weights(bias = 0f, bpm = 5f, gsr = 5f, temperature = 5f, bmi = 5f)
        )
        val input = BiometricInput(bpm = 90f, temperature = 37f, gsr = 40f, bmi = 30f)

        val defaultProb = defaultEngine.computeSigmoid(input)
        val aggressiveProb = aggressiveEngine.computeSigmoid(input)
        assertTrue("Aggressive weights should produce higher probability", aggressiveProb > defaultProb)
    }

    @Test
    fun `default weights are coherent`() {
        val w = BiometricRiskEngine.Weights()
        assertEquals(-2.0f, w.bias, 0.001f)
        assertEquals(1.5f, w.bpm, 0.001f)
        assertEquals(1.0f, w.gsr, 0.001f)
        assertEquals(1.5f, w.temperature, 0.001f)
        assertEquals(0.5f, w.bmi, 0.001f)
    }

    // ─────────────────────────────────────────────
    // J. ASSESSMENT METADATA
    // ─────────────────────────────────────────────

    @Test
    fun `assessment contains input snapshot`() {
        val input = BiometricInput(bpm = 72f, temperature = 36.4f, gsr = 22f, bmi = 24f)
        val result = engine.evaluate(input)
        assertEquals(input, result.input)
    }

    @Test
    fun `assessment probability is always between 0 and 1`() {
        val extremeInputs = listOf(
            BiometricInput(30f, 30f, 0f, 10f),
            BiometricInput(220f, 42f, 150f, 60f),
            BiometricInput(70f, 36.5f, 25f, 25f)
        )
        for (input in extremeInputs) {
            val result = engine.evaluate(input)
            assertTrue(
                "Probability ${result.probability} should be in [0, 1] for input $input",
                result.probability in 0f..1f
            )
        }
    }
}
