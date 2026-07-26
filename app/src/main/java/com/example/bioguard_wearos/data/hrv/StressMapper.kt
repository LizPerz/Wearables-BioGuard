package com.example.bioguard_wearos.data.hrv

class StressMapper {

    private var smoothedStress = -1f
    private val alpha = 0.3f

    fun mapToStressUs(rmssd: Float): Float {
        if (rmssd <= 0f) return smoothedStress.coerceAtLeast(0f)

        val raw = when {
            rmssd < 20f -> {
                val t = rmssd / 20f
                60f + (1f - t) * 40f
            }
            rmssd < 40f -> {
                val t = (rmssd - 20f) / 20f
                25f + (1f - t) * 35f
            }
            else -> {
                val t = ((rmssd - 40f) / 60f).coerceAtMost(1f)
                5f + (1f - t) * 20f
            }
        }.coerceIn(1f, 100f)

        smoothedStress = if (smoothedStress < 0f) {
            raw
        } else {
            alpha * raw + (1f - alpha) * smoothedStress
        }

        return smoothedStress
    }

    fun getStressLabel(stressUs: Float): String = when {
        stressUs >= 60f -> "Estrés Alto"
        stressUs >= 25f -> "Estrés Moderado"
        else -> "Relajado"
    }

    fun reset() {
        smoothedStress = -1f
    }
}
