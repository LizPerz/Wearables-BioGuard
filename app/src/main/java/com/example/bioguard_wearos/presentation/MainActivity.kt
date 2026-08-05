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
        val bodySensorsGranted = permissions[Manifest.permission.BODY_SENSORS] ?: false
        val healthHeartRate = permissions["android.permission.health.READ_HEART_RATE"] ?: false

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

        sensorsEnabled = hasAnySensorPermission()
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

    private fun hasAnySensorPermission(): Boolean {
        val bodySensors = ContextCompat.checkSelfPermission(
            this, Manifest.permission.BODY_SENSORS
        ) == PackageManager.PERMISSION_GRANTED

        val heartRate = try {
            ContextCompat.checkSelfPermission(
                this, "android.permission.health.READ_HEART_RATE"
            ) == PackageManager.PERMISSION_GRANTED
        } catch (e: Exception) { false }

        return bodySensors || heartRate
    }

    private fun requestAppPermissions() {
        val permissionsToRequest = mutableListOf(
            Manifest.permission.BODY_SENSORS,
            "android.permission.health.READ_HEART_RATE",
            "android.permission.health.READ_BODY_TEMPERATURE"
        )

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
        if (!hasAnySensorPermission()) {
            Log.w("BIOGUARD", "Sin permisos de sensores, no se puede iniciar servicio")
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
}
