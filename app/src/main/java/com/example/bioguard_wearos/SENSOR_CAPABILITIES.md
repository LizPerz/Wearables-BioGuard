# BioGuard Wearable — Capacidades de Sensores

## Sensores Disponibles (Hardware Real)
- ✅ Frecuencia cardíaca (BPM) — Health Services API, PassiveMonitoring
- ✅ Temperatura cutánea — TemperatureProvider (según disponibilidad del hardware)
- ✅ Pasos y actividad — ACTIVITY_RECOGNITION
- ✅ HRV (Heart Rate Variability) — Calculado desde datos de HR
- ⚠️ SpO2 — Disponible en algunos modelos (Google Pixel Watch, Galaxy Watch)

## Sensores Estimados (NO lectura de hardware)
- ⚠️ GSR/Sudoración — ESTIMADO desde FC + temperatura. NO es una lectura directa.
  * Dispositivos con GSR real: Samsung Galaxy Watch 4/5/6
  * Para todos los demás: el valor es una estimación del modelo de riesgo local

## Dispositivos Compatibles Verificados
- Google Pixel Watch 2 (WearOS 4)
- Samsung Galaxy Watch 6 (WearOS 4 + Health Platform)
- TicWatch Pro 5 (WearOS 3)

## Advertencia para Producción
Los valores de sudoración/GSR enviados al backend SON ESTIMACIONES cuando
el dispositivo no tiene sensor GSR real. El modelo de ML fue entrenado con
datos reales de GSR — la precisión puede ser menor con valores estimados.
Se recomienda documentar esto claramente en los términos de servicio.
