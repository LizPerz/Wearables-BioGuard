package com.example.bioguard_wearos.presentation

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.navigation.compose.rememberNavController
import com.example.bioguard_wearos.data.local.BioGuardPreferences
import com.example.bioguard_wearos.domain.risk.AlertManager
import com.example.bioguard_wearos.presentation.navigation.BioGuardNavHost
import com.example.bioguard_wearos.presentation.service.BioGuardSensorService
import com.example.bioguard_wearos.presentation.theme.BioGuard_WearOsTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var preferences: BioGuardPreferences

    @Inject
    lateinit var alertManager: AlertManager

    var sensorsEnabled = false
        private set

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        // The result map only contains permissions requested in this invocation.
        // Preserve previously granted permissions when just one health permission is requested.
        val bodySensorsGranted = permissions[Manifest.permission.BODY_SENSORS]
            ?: (ContextCompat.checkSelfPermission(this, Manifest.permission.BODY_SENSORS) == PackageManager.PERMISSION_GRANTED)
        val healthHeartRate = if (requiresHealthHeartRatePermission()) {
            permissions[READ_HEART_RATE_PERMISSION] ?: hasHealthHeartRatePermission()
        } else {
            true
        }

        Log.d("BIOGUARD", "Permiso BODY_SENSORS: $bodySensorsGranted")
        Log.d("BIOGUARD", "Permiso READ_HEART_RATE: $healthHeartRate")

        if (bodySensorsGranted || healthHeartRate) {
            sensorsEnabled = true
            Log.d("BIOGUARD", "Permisos concedidos (BODY_SENSORS=$bodySensorsGranted, HR=$healthHeartRate). Iniciando motor...")
            startSensorService()
        } else {
            sensorsEnabled = false
            Log.e("BIOGUARD", "Sin permisos de sensores. BODY_SENSORS=$bodySensorsGranted, HR=$healthHeartRate")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        sensorsEnabled = hasRequiredSensorPermissions()
        requestAppPermissions()

        setContent {
            BioGuard_WearOsTheme {
                val navController = rememberNavController()

                BioGuardNavHost(
                    navController = navController,
                    startSensors = { startSensorService() },
                    sensorsEnabled = sensorsEnabled,
                    preferences = preferences,
                    alertManager = alertManager
                )
            }
        }
    }

    private fun hasRequiredSensorPermissions(): Boolean {
        val bodySensors = ContextCompat.checkSelfPermission(
            this, Manifest.permission.BODY_SENSORS
        ) == PackageManager.PERMISSION_GRANTED
        return bodySensors || hasHealthHeartRatePermission()
    }

    private fun hasHealthHeartRatePermission(): Boolean {
        if (!requiresHealthHeartRatePermission()) return true
        return try {
            ContextCompat.checkSelfPermission(
                this, READ_HEART_RATE_PERMISSION
            ) == PackageManager.PERMISSION_GRANTED
        } catch (_: Exception) {
            false
        }
    }

    private fun requiresHealthHeartRatePermission(): Boolean = Build.VERSION.SDK_INT >= 36

    private fun requestAppPermissions() {
        val permissionsToRequest = mutableListOf(Manifest.permission.BODY_SENSORS)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            permissionsToRequest.add(Manifest.permission.ACTIVITY_RECOGNITION)
        }

        if (requiresHealthHeartRatePermission()) {
            permissionsToRequest.add(READ_HEART_RATE_PERMISSION)
            permissionsToRequest.add(READ_BODY_TEMPERATURE_PERMISSION)
        }

        // Permiso de Health Connect para la temperatura de piel del Samsung Health Sensor SDK.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            permissionsToRequest.add(READ_SKIN_TEMPERATURE_PERMISSION)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val missingPermissions = permissionsToRequest.filter {
            try {
                ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
            } catch (e: Exception) {
                false
            }
        }

        if (missingPermissions.isNotEmpty()) {
            Log.d("BIOGUARD", "Solicitando permisos: $missingPermissions")
            permissionLauncher.launch(missingPermissions.toTypedArray())
        } else {
            Log.d("BIOGUARD", "Todos los permisos ya concedidos")
            sensorsEnabled = true
            startSensorService()
        }
    }

    fun startSensorService() {
        if (!hasRequiredSensorPermissions()) {
            Log.w("BIOGUARD", "Faltan permisos requeridos de sensores, no se inicia el servicio")
            requestAppPermissions()
            return
        }

        try {
            val serviceIntent = Intent(this, BioGuardSensorService::class.java)
            ContextCompat.startForegroundService(this, serviceIntent)
            Log.d("BIOGUARD", "Servicio iniciado correctamente")
        } catch (e: Exception) {
            Log.e("BIOGUARD", "Error al iniciar servicio: ${e.message}", e)
        }
    }

    companion object {
        private const val READ_HEART_RATE_PERMISSION = "android.permission.health.READ_HEART_RATE"
        private const val READ_BODY_TEMPERATURE_PERMISSION = "android.permission.health.READ_BODY_TEMPERATURE"
        private const val READ_SKIN_TEMPERATURE_PERMISSION = "android.permission.health.READ_SKIN_TEMPERATURE"
    }
}
