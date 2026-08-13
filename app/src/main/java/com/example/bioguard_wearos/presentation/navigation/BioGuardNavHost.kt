package com.example.bioguard_wearos.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.example.bioguard_wearos.data.local.BioGuardPreferences
import com.example.bioguard_wearos.domain.risk.AlertManager
import com.example.bioguard_wearos.presentation.screens.alert.CriticalAlertScreen
import com.example.bioguard_wearos.presentation.screens.alert.CriticalAlertViewModel
import com.example.bioguard_wearos.presentation.screens.dashboard.DashboardScreen
import com.example.bioguard_wearos.presentation.screens.dashboard.DashboardViewModel
import com.example.bioguard_wearos.presentation.screens.error.ErrorScreen
import com.example.bioguard_wearos.presentation.screens.error.ErrorViewModel
import com.example.bioguard_wearos.presentation.screens.ready.ReadyScreen
import com.example.bioguard_wearos.presentation.screens.ready.ReadyViewModel
import com.example.bioguard_wearos.presentation.screens.sensorcheck.SensorCheckScreen
import com.example.bioguard_wearos.presentation.screens.splash.SplashScreen
import com.example.bioguard_wearos.presentation.screens.splash.SplashViewModel

@Composable
fun BioGuardNavHost(
    navController: NavHostController,
    startSensors: () -> Unit,
    sensorsEnabled: Boolean = false,
    preferences: BioGuardPreferences,
    alertManager: AlertManager
) {
    val alertState by alertManager.alertState.collectAsState()
    var previousAlertActive by remember { mutableStateOf(false) }

    LaunchedEffect(alertState) {
        val isActive = alertState.isActive
        if (!previousAlertActive && isActive) {
            navController.navigate(Screen.CriticalAlert) {
                launchSingleTop = true
            }
        } else if (previousAlertActive && !isActive) {
            runCatching {
                navController.popBackStack(Screen.Dashboard, inclusive = false)
            }
        }
        previousAlertActive = isActive
    }

    NavHost(
        navController = navController,
        startDestination = Screen.Splash
    ) {
        composable<Screen.Splash> {
            val viewModel: SplashViewModel = hiltViewModel()
            val uiState by viewModel.uiState.collectAsState()

            SplashScreen(
                onTimeout = {
                    if (uiState.isFirstRun) {
                        navController.navigate(Screen.SensorCheck) {
                            popUpTo(Screen.Splash) { inclusive = true }
                        }
                    } else {
                        startSensors()
                        navController.navigate(Screen.Dashboard) {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                }
            )
        }

        composable<Screen.SensorCheck> {
            SensorCheckScreen(
                onComplete = {
                    navController.navigate(Screen.Ready) {
                        popUpTo(Screen.SensorCheck) { inclusive = true }
                    }
                }
            )
        }

        composable<Screen.Ready> {
            val viewModel: ReadyViewModel = hiltViewModel()

            ReadyScreen(
                onDismiss = {},
                onConfirmed = {
                    viewModel.confirmAndStart()
                    startSensors()
                    navController.navigate(Screen.Dashboard) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }

        composable<Screen.Dashboard> {
            val viewModel: DashboardViewModel = hiltViewModel()
            DashboardScreen(viewModel = viewModel)
        }

        composable<Screen.Error> {
            val viewModel: ErrorViewModel = hiltViewModel()

            ErrorScreen(
                onRetry = {
                    navController.navigate(Screen.Dashboard) {
                        popUpTo(Screen.Error) { inclusive = true }
                    }
                }
            )
        }

        composable<Screen.CriticalAlert> {
            val viewModel: CriticalAlertViewModel = hiltViewModel()

            CriticalAlertScreen(
                viewModel = viewModel,
                onDismissed = {
                    runCatching {
                        navController.navigate(Screen.Dashboard) {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                }
            )
        }
    }
}
