package com.example.bioguard_wearos.domain.model

data class SensorAvailability(
    val heartRateAvailable: Boolean = false,
    val temperatureAvailable: Boolean = false,
    
    /**
     * NOTA DE PRODUCCIÓN: El sensor GSR (Galvanic Skin Response / sudoración)
     * NO está disponible en la mayoría de smartwatches con Wear OS.
     * El valor de `sudoracionGsr` que se reporta es una ESTIMACIÓN derivada
     * de otros sensores (frecuencia cardíaca + temperatura) usando el modelo
     * de riesgo local, NO una lectura directa de hardware.
     *
     * Dispositivos con GSR real: Samsung Galaxy Watch 4/5/6 (con Health Platform).
     * Para otros dispositivos, `gsrDisponible = false` y el valor se estima.
     */
    val gsrDisponible: Boolean = false,
    val gsrEsEstimado: Boolean = !gsrDisponible,
    
    val heartRateOffBody: Boolean = false,
    val statusMessage: String? = null
)
