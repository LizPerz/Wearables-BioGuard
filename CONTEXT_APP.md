# CONTEXT_APP.md - BioGuard WearOS

## Arquitectura General

BioGuard WearOS es una aplicación de Wear OS que recopila métricas biométricas en tiempo real desde el Galaxy Watch 7. Utiliza **Clean Architecture** con tres capas (Data / Domain / Presentation), **Hilt** para inyección de dependencias, y **Kotlin Coroutines + StateFlow** para el flujo reactivo de datos.

```
app/src/main/java/com/example/bioguard_wearos/
├── BioGuardApplication.kt                  # Entry point Hilt + WorkManager config
├── data/
│   ├── hrv/                                # Cálculo de HRV y estrés
│   │   ├── HrvCalculator.kt               # Buffer IBI + RMSSD + SDNN
│   │   └── StressMapper.kt                # Mapeo HRV → µS con suavizado
│   ├── sensor/
│   │   └── TemperatureProvider.kt          # Temperatura HW o correlacionada
│   ├── repository/
│   │   ├── SensorDataRepositoryImpl.kt     # Orquestador central + guardado periódico
│   │   └── SyncRepositoryImpl.kt           # Sincronización con backend (11 endpoints)
│   ├── local/
│   │   ├── BioGuardPreferences.kt          # DataStore: tokens JWT, dispositivos, firstRun
│   │   ├── BiometricReadingRepositoryImpl.kt
│   │   ├── TelemetrySaveScheduler.kt       # Guardado periódico cada 10s
│   │   └── db/
│   │       ├── BiometricReadingEntity.kt
│   │       ├── BiometricReadingDao.kt
│   │       ├── BioGuardDatabase.kt
│   │       └── RiskLevelConverter.kt
│   └── remote/
│       ├── NetworkModule.kt                # Hilt: HttpClient(OkHttp) + JWT interceptor + API
│       ├── api/
│       │   ├── BioGuardApi.kt              # Interfaz API REST (11 métodos)
│       │   └── BioGuardApiImpl.kt          # Implementación Ktor Client
│       └── dto/                            # 14 DTOs @Serializable
│           ├── LoginCodigoRequestDto.kt     # POST /api/Auth/login-codigo
│           ├── LoginResponseDto.kt          # Token JWT + refreshToken + expiración
│           ├── RefreshTokenRequestDto.kt    # POST /api/Auth/refresh
│           ├── RefreshTokenResponseDto.kt   # Nuevo token + expiración
│           ├── VincularDispositivoDto.kt    # POST /api/Dispositivos/vincular
│           ├── VincularDispositivoResponseDto.kt
│           ├── PacienteResponseDto.kt       # GET /api/Pacientes/mi-paciente
│           ├── LecturaSensoresDto.kt        # POST /api/Sensores/lectura
│           ├── LecturaBatchDto.kt           # POST /api/Sensores/lectura-batch
│           ├── TrackingDto.kt               # POST /api/Sensores/tracking
│           ├── TrackingBatchDto.kt          # POST /api/Sensores/tracking-batch
│           ├── HeartbeatDto.kt              # POST /api/Dispositivos/heartbeat
│           ├── EventoMetabolicoDto.kt       # POST /api/Sensores/evento
│           └── AlertaDto.kt                 # POST /api/Alertas
├── domain/
│   ├── model/
│   │   ├── SensorData.kt
│   │   └── SensorAvailability.kt
│   ├── repository/
│   │   ├── SensorDataRepository.kt
│   │   ├── BiometricReadingRepository.kt
│   │   └── SyncRepository.kt              # Interfaz: 11 métodos de red
│   └── usecase/
├── worker/
│   ├── SyncWorker.kt                      # WorkManager: sync batch cada 15min
│   └── SyncScheduler.kt                   # Programador con backoff exponencial
├── presentation/
│   ├── MainActivity.kt                    # Permisos + startForegroundService()
│   ├── screens/
│   │   ├── dashboard/
│   │   │   ├── DashboardViewModel.kt      # Solo collect sensorData (no inicia sensores)
│   │   │   └── DashboardScreen.kt
│   │   ├── splash/SplashScreen.kt
│   │   ├── sensorcheck/SensorCheckScreen.kt
│   │   ├── ready/ReadyViewModel.kt        # Solo setFirstRunComplete()
│   │   └── error/ErrorViewModel.kt        # Solo navegar (sin startSensors)
│   ├── service/
│   │   └── BioGuardSensorService.kt       # Foreground Service (único que inicia sensores)
│   ├── navigation/
│   │   ├── Screen.kt
│   │   └── BioGuardNavHost.kt
│   └── di/
│       └── AppModule.kt
```

---

## Permisos del Manifest

| Permiso | Propósito |
|---------|-----------|
| `BODY_SENSORS` | Sensor cardíaco via SensorManager |
| `health.READ_HEART_RATE` | Sensor cardíaco via Health Services |
| `health.READ_BODY_TEMPERATURE` | Temperatura corporal |
| `HEALTH_SENSORS` | Acceso genérico a sensores de salud |
| `WAKE_LOCK` | CPU activa durante muestreo |
| `FOREGROUND_SERVICE` | Servicio en primer plano |
| `FOREGROUND_SERVICE_HEALTH` | **Tipo health** del foreground service (REQUERIDO en API 36+) |
| `POST_NOTIFICATIONS` | Notificaciones (API 33+) |
| `ACTIVITY_RECOGNITION` | Reconocimiento de actividad |
| `ACCESS_FINE_LOCATION` / `ACCESS_COARSE_LOCATION` | GPS para tracking |

---

## Foreground Service — Flujo Corregido

**Regla crítica:** Solo `BioGuardSensorService` puede llamar `startSensors()`. Los ViewModels solo *leen* el StateFlow.

```
MainActivity.onCreate()
  → requestAppPermissions()
    → permisos OK → startSensorService()
      → ContextCompat.startForegroundService(intent)
        → BioGuardSensorService.onStartCommand()
          → startForeground(ID, notification, FOREGROUND_SERVICE_TYPE_HEALTH)  ← REQUERIDO
          → sensorDataRepository.startSensors()  ← ÚNICO punto de inicio
          → observeSensorData() → collect → updateNotification()
        → return START_STICKY (se relanza automáticamente)
```

**Flujo de datos en segundo plano:**
```
Sensor PPG → SensorDataRepositoryImpl._sensorData (StateFlow)
  ├─→ DashboardViewModel → DashboardScreen (solo lee)
  ├─→ BioGuardSensorService → Notificación foreground (actualiza cada 5s o cambio significativo)
  └─→ TelemetrySaveScheduler → Room DB cada 10s
```

**NOTA:** Los ViewModels NO inician sensores. Solo `BioGuardSensorService` lo hace. Esto garantiza que los sensores persisten en background.

---

## Capa de Red — 11 Endpoints REST

**Base URL:** `https://bioguard-api-lkvnq.ondigitalocean.app`

### Fase 1 — Setup y Vinculación

| Método | Endpoint | DTO Request | DTO Response | Función |
|--------|----------|-------------|--------------|---------|
| `POST` | `/api/Auth/login-codigo` | `LoginCodigoRequestDto` | `LoginResponseDto` | Autenticación por PIN/QR → JWT |
| `POST` | `/api/Dispositivos/vincular` | `VincularDispositivoDto` | `VincularDispositivoResponseDto` | Registro del reloj (MAC/UUID) |
| `GET` | `/api/Pacientes/mi-paciente` | — | `PacienteResponseDto` | Datos basales (peso, estatura, IMC) |

### Fase 2 — Monitoreo Continuo

| Método | Endpoint | DTO Request | DTO Response | Función |
|--------|----------|-------------|--------------|---------|
| `POST` | `/api/Sensores/lectura` | `LecturaSensoresDto` | Unit | Telemetría individual |
| `POST` | `/api/Sensores/tracking` | `TrackingDto` | Unit | Coordenadas GPS |
| `POST` | `/api/Dispositivos/heartbeat` | `HeartbeatDto` | Unit | Estado del reloj (batería, sensores) |

### Fase 3 — Sincronización Offline

| Método | Endpoint | DTO Request | DTO Response | Función |
|--------|----------|-------------|--------------|---------|
| `POST` | `/api/Sensores/lectura-batch` | `LecturaBatchDto` | Unit | Envío en lote de lecturas |
| `POST` | `/api/Sensores/tracking-batch` | `TrackingBatchDto` | Unit | Envío en lote de posiciones GPS |

### Fase 4 — Protocolo de Emergencia

| Método | Endpoint | DTO Request | DTO Response | Función |
|--------|----------|-------------|--------------|---------|
| `POST` | `/api/Sensores/evento` | `EventoMetabolicoDto` | Unit | Eventos críticos |
| `POST` | `/api/Alertas` | `AlertaDto` | Unit | Alertas de emergencia |

### Fase 5 — Mantenimiento de Sesión

| Método | Endpoint | DTO Request | DTO Response | Función |
|--------|----------|-------------|--------------|---------|
| `POST` | `/api/Auth/refresh` | `RefreshTokenRequestDto` | `RefreshTokenResponseDto` | Renovación JWT |

### Configuración de Red (NetworkModule)

```kotlin
HttpClient(OkHttp) {
    install(ContentNegotiation) { json(json) }

    defaultRequest {
        val token = runBlocking { preferences.getJwtToken() }
        if (!token.isNullOrEmpty()) {
            headers.append("Authorization", "Bearer $token")
        }
    }

    engine {
        config {
            connectTimeout(30, SECONDS)
            readTimeout(30, SECONDS)
            writeTimeout(30, SECONDS)
        }
    }
}
```

### Almacenamiento JWT (BioGuardPreferences)

| Key | Tipo | Descripción |
|-----|------|-------------|
| `jwt_token` | String | Token de acceso JWT |
| `refresh_token` | String | Token de renovación |
| `token_expiry` | Long | Timestamp de expiración |
| `dispositivo_id` | String | ID del reloj vinculado |
| `paciente_id` | String | ID del paciente asociado |

---

## SyncWorker — Resiliencia

- **Worker:** `@HiltWorker` con `CoroutineWorker`
- **Intervalo:** 15 minutos con constraint `NetworkType.CONNECTED`
- **Backoff:** Exponencial (1 min base)
- **Nunca falla definitivamente:** Siempre retorna `Result.retry()` en errores
- **Logging:** Tipo de excepción + stack trace en cada fallo

---

## Métricas Biométricas

### 1. Pulso Cardíaco (BPM)
- Fuente: Sensor PPG (Health Services → SensorManager fallback)
- Rango: 40-200 BPM
- Validación: Solo `bpm > 0f`

### 2. Temperatura Cutánea (°C)
- Fuente: `TYPE_AMBIENT_TEMPERATURE` o correlación BPM→Temp
- Rango: 35.5-37.8 °C (coerceIn)
- Algoritmo: `baseTemp = 36.2 + hrFactor × 1.3 + sin(t×0.5)×0.15`

### 3. Estrés / GSR (µS) — vía HRV
- Pipeline: BPM → IBI → RMSSD → µS (relación inversa)
- Buffer circular: 60 muestras IBI
- Suavizado EMA: α=0.3
- Rango: 1-100 µS

---

## Suite de Pruebas

**71 tests, 0 failures** — `./gradlew testDebugUnitTest`

| Archivo | Tests | Cobertura |
|---------|-------|-----------|
| `HrvCalculatorTest.kt` | 15 | IBI buffer, RMSSD, SDNN |
| `StressMapperTest.kt` | 14 | Mapeo µS, EMA, labels |
| `SensorDataTest.kt` | 13 | Fractions, defaults |
| `TemperatureCorrelationTest.kt` | 10 | Rangos, correlación |
| `SensorAvailabilityTest.kt` | 2 | Defaults |
| `BiometricReadingDaoTest.kt` | 9 | Room CRUD |
| `PeriodicSaveSQLiteTest.kt` | 8 | Guardado periódico |
