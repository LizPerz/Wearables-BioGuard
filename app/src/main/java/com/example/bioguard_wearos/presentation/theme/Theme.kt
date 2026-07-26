package com.example.bioguard_wearos.presentation.theme

import androidx.compose.runtime.Composable
import androidx.wear.compose.material3.MaterialTheme

@Composable
fun BioGuard_WearOsTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        typography = BioGuardTypography,
        content = content
    )
}