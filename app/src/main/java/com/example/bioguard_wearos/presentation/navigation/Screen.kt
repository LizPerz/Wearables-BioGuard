package com.example.bioguard_wearos.presentation.navigation

import kotlinx.serialization.Serializable

sealed class Screen {
    @Serializable
    data object Splash : Screen()

    @Serializable
    data object SensorCheck : Screen()

    @Serializable
    data object Ready : Screen()

    @Serializable
    data object Dashboard : Screen()

    @Serializable
    data object Error : Screen()

    @Serializable
    data object Login : Screen()

    @Serializable
    data object CriticalAlert : Screen()
}