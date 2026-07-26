# BioGuard WearOS

Aplicación de monitoreo biométrico para WearOS que registra frecuencia cardíaca (BPM), temperatura y conductancia de la piel (GSR) en tiempo real, incluso cuando la app no está en primer plano.

---

## Requisitos

- **Android Studio Ladybug** o superior
- **Wear OS 5+** (API 36) — probado en Samsung Galaxy Watch 7
- **JDK 11**
- Dispositivo con Google Play Services para WearOS

## Arrancar el proyecto

```bash
# Clonar el repositorio
git clone <url-del-repositorio>
cd BioGuard_WearOs

# Compilar la APK debug
./gradlew assembleDebug

# Instalar en el watch (conectado por ADB)
adb install -r app/build/outputs/apk/debug/app-debug.apk

# O instalar directamente desde Android Studio
# Run > Run 'app' (seleccionando el emulador o dispositivo Wear)
```

## Estructura del proyecto

```
app/src/main/java/com/example/bioguard_wearos/
├── BioGuardApplication.kt                  # Entry point Hilt
├── data/
│   ├── local/BioGuardPreferences.kt        # DataStore (isFirstRun)
│   └── repository/SensorDataRepositoryImpl.kt  # Core: sensores + GSR simulator
├── domain/
│   ├── model/SensorData.kt                 # Modelo de datos
│   ├── repository/SensorDataRepository.kt  # Interfaz
│   └── usecase/StartSensorsUseCase.kt      # Caso de uso
└── presentation/
    ├── MainActivity.kt                     # Activity principal + permisos
    ├── di/AppModule.kt                     # Módulo Hilt
    ├── navigation/
    │   ├── Screen.kt                       # Rutas serializables
    │   └── BioGuardNavHost.kt              # Navegación Compose
    ├── screens/
    │   ├── splash/                         # Splash con animación (3s)
    │   ├── sensorcheck/                    # Verificación de sensores (3s)
    │   ├── ready/                          # Confirmación de setup
    │   ├── dashboard/                      # Pantalla principal con métricas
    │   ├── error/                          # Pantalla de error
    │   └── components/                     # Componentes reutilizables
    └── service/BioGuardSensorService.kt    # Foreground service
```

---

## Módulo de Frecuencia Cardíaca (BPM)

### Cómo funciona

La app utiliza una **estrategia dual** para obtener datos reales del pulso:

1. **Google Health Services (preferida)** — API oficial de WearOS para datos de salud
2. **SensorManager (fallback)** — Acceso directo al hardware del sensor de frecuencia cardíaca

### Flujo de datos

```
Hardware del sensor → Health Services / SensorManager
    ↓
SensorDataRepositoryImpl._sensorData (StateFlow)
    ↓
    ├──→ DashboardViewModel → DashboardScreen (UI)
    └──→ BioGuardSensorService → Notificación foreground
```

### Código clave

| Archivo | Línea | Función |
|---------|-------|---------|
| `SensorDataRepositoryImpl.kt:93` | `startSensors()` | Punto de entrada para iniciar todos los sensores |
| `SensorDataRepositoryImpl.kt:112` | `startHeartRateMeasure()` | Intenta Health Services, si falla usa SensorManager |
| `SensorDataRepositoryImpl.kt:155` | `startHeartRateSensorManager()` | Fallback directo al hardware |
| `SensorDataRepositoryImpl.kt:62` | `heartRateCallback` | Callback que recibe BPM desde Health Services |
| `SensorDataRepositoryImpl.kt:239` | `onSensorChanged()` | Listener que recibe BPM desde SensorManager |

### Verificar que el BPM funciona

Al ejecutar la app, los logs `BIOGUARD` muestran el camino exacto que tomó:

```
adb logcat -s BIOGUARD:D
```

Logs esperados si Health Services funciona:
```
BIOGUARD: === startSensors() llamado ===
BIOGUARD: measureClient: disponible
BIOGUARD: Capacidades Health Services: [...HEART_RATE_BPM...]
BIOGUARD: HEART_RATE_BPM soportado: true
BIOGUARD: registerMeasureCallback invocado OK
BIOGUARD: Health Services callback registrado correctamente
BIOGUARD: Health Services BPM: 72.0
```

Logs esperados si cae a SensorManager:
```
BIOGUARD: MeasureClient null, usando SensorManager fallback
BIOGUARD: Intentando SensorManager fallback...
BIOGUARD: Sensor HR encontrado: Heart Rate Sensor, vendor: Samsung
BIOGUARD: SensorManager TYPE_HEART_RATE registrado OK
BIOGUARD: SensorManager BPM: 72.0 (accuracy=3)
```

---

## Servicio en Segundo Plano

### Por qué existe

WearOS puede matar apps que no están en primer plano para ahorrar batería. El servicio foreground garantiza que la recolección de datos biométricos continúe sin interrupciones.

### Cómo funciona

**BioGuardSensorService** es un `Service` con las siguientes características:

| Componente | Descripción |
|------------|-------------|
| **Foreground** | Muestra notificación permanente con BPM/Temp/GSR |
| **WakeLock** | `PARTIAL_WAKE_LOCK` por 1 hora para evitar sleep del CPU |
| **START_STICKY** | El sistema relanza el servicio si se destruye |
| **Inyección** | Usa Hilt (`@AndroidEntryPoint`) para acceder a `SensorDataRepository` |

### Ciclo de vida del servicio

```
App arranca → MainActivity.onCreate()
  └─ requestAppPermissions()
       └─ Permisos concedidos → startSensorService()
            └─ ContextCompat.startForegroundService()
                 └─ BioGuardSensorService.onCreate()
                      ├─ createNotificationChannel()
                      ├─ acquireWakeLock()
                      └─ observeSensorData()
                 └─ BioGuardSensorService.onStartCommand()
                      ├─ startForeground(NOTIFICATION_ID, notification)
                      └─ sensorDataRepository.startSensors()

App en background → Servicio sigue corriendo (START_STICKY)

App destruida → onDestroy()
  ├─ sensorDataRepository.stopSensors()
  ├─ releaseWakeLock()
  └─ serviceScope.cancel()
```

### Notificación foreground

Se actualiza en tiempo real mostrando:
```
BioGuard Activo
BPM: 72 | Temp: 24.0°C | GSR: 18µS (est.)
```

---

## Permisos

Los permisos se solicitan en `MainActivity` y se verifican en tres puntos:

1. **MainActivity.hasAnySensorPermission()** — Para decidir si `sensorsEnabled = true`
2. **permissionLauncher** — Dialogo de sistema para permisos faltantes
3. **BioGuardSensorService.onStartCommand()** — Verificación final antes de leer sensores

```kotlin
// La app acepta CUALQUIERA de estos dos permisos:
BODY_SENSORS || health.READ_HEART_RATE
```

> **Nota Samsung:** Galaxy Watch 7 bloquea `BODY_SENSORS` pero concede `READ_HEART_RATE`. La app funciona con cualquiera de los dos.

---

## Construcción y Testing

```bash
# Build debug
./gradlew assembleDebug

# Build release (requiere configuración de signing)
./gradlew assembleRelease

# Tests unitarios
./gradlew testDebugUnitTest

# Lint check
./gradlew lintDebug
```

---

## Tecnologías

- **Kotlin** + **Jetpack Compose** (UI declarativa)
- **Wear OS Compose** (Material 3 para Wear)
- **Hilt** (inyección de dependencias)
- **Navigation Compose** (navegación type-safe con Kotlin Serialization)
- **Google Health Services** (sensores de salud)
- **Google Play Services Wearable** (Data Layer)
- **DataStore Preferences** (almacenamiento persistente)
- **Kotlin Coroutines** + **StateFlow** (reactividad)
