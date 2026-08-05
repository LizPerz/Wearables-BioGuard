package com.example.bioguard_wearos.data.hrv

import kotlin.math.sqrt

class HrvCalculator(private val maxBufferSize: Int = 60) {

    private val ibiBuffer = mutableListOf<Float>()

    fun addIbi(bpm: Float) {
        if (bpm <= 0f) return
        val ibi = 60000f / bpm
        ibiBuffer.add(ibi)
        if (ibiBuffer.size > maxBufferSize) {
            ibiBuffer.removeAt(0)
        }
    }

    fun computeRmssd(): Float {
        if (ibiBuffer.size < 2) return 0f
        var sumSquaredDiff = 0f
        for (i in 1 until ibiBuffer.size) {
            val diff = ibiBuffer[i] - ibiBuffer[i - 1]
            sumSquaredDiff += diff * diff
        }
        return sqrt(sumSquaredDiff / (ibiBuffer.size - 1))
    }

    fun computeSdnn(): Float {
        if (ibiBuffer.size < 2) return 0f
        val mean = ibiBuffer.average().toFloat()
        var sumSquaredDiff = 0f
        for (ibi in ibiBuffer) {
            val diff = ibi - mean
            sumSquaredDiff += diff * diff
        }
        return sqrt(sumSquaredDiff / ibiBuffer.size)
    }

    fun getIbiCount(): Int = ibiBuffer.size

    fun clear() {
        ibiBuffer.clear()
    }
}
