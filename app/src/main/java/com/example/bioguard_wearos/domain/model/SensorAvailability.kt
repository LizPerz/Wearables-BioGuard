package com.example.bioguard_wearos.domain.model

data class SensorAvailability(
    val heartRateAvailable: Boolean = false,
    val temperatureAvailable: Boolean = false,

    /**
     * NOTA DE PRODUCCIÓN: El sensor GSR (Galvanic Skin Response / sudoración)
     * NO está disponible en la mayoría de smartwatches con Wear OS.
     * El valor de `estresPct` que se reporta es una ESTIMACIÓN derivada
     * del nivel de estrés (HRV) capturado por el sensor BioActive del
     * Galaxy Watch 7, NO una lectura directa de hardware GSR.
     *
     * Para otros dispositivos, `estresEsEstimado = true` y el valor se estima.
     */
    val estresDisponible: Boolean = false,
    val estresEsEstimado: Boolean = !estresDisponible,

    val heartRateOffBody: Boolean = false,
    val statusMessage: String? = null
)
