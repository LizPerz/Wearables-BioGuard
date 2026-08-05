package com.example.bioguard_wearos.presentation.screens.login

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.Text
import androidx.wear.compose.ui.tooling.preview.WearPreviewDevices
import androidx.wear.compose.ui.tooling.preview.WearPreviewFontScales
import com.example.bioguard_wearos.presentation.theme.BioGuardError
import com.example.bioguard_wearos.presentation.theme.BioGuardOnSurface
import com.example.bioguard_wearos.presentation.theme.BioGuardOnSurfaceVariant
import com.example.bioguard_wearos.presentation.theme.BioGuardTealBase
import com.example.bioguard_wearos.presentation.theme.BioGuardBackground
import com.example.bioguard_wearos.presentation.theme.BioGuard_WearOsTheme

@Composable
fun LoginScreen(
    viewModel: LoginViewModel,
    onLoginSuccess: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(uiState.hasToken) {
        if (uiState.hasToken) {
            onLoginSuccess()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(BioGuardTealBase, BioGuardBackground),
                    radius = 600f
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Sin",
                color = BioGuardOnSurface,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Text(
                text = "Vincular",
                color = BioGuardError,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "Abre BioGuard en tu tel\u00e9fono para vincular el reloj",
                color = BioGuardOnSurfaceVariant,
                fontSize = 10.sp,
                textAlign = TextAlign.Center,
                lineHeight = 14.sp
            )

            Spacer(modifier = Modifier.height(14.dp))

            if (uiState.isChecking) {
                CircularProgressIndicator(
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Buscando vinculaci\u00f3n...",
                    color = BioGuardOnSurfaceVariant,
                    fontSize = 9.sp
                )
            } else {
                Button(
                    onClick = { viewModel.checkToken() },
                    modifier = Modifier
                        .height(36.dp)
                        .width(130.dp),
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = BioGuardError
                    )
                ) {
                    Text(
                        text = "REINTENTAR",
                        color = BioGuardOnSurface,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@WearPreviewDevices
@WearPreviewFontScales
@Composable
private fun LoginScreenPreview() {
    BioGuard_WearOsTheme {
        LoginScreenStatic(showChecking = false)
    }
}

@Preview(
    device = "spec:width=454.0dp,height=454.0dp,dpi=326,isRound=true",
    showBackground = true,
    backgroundColor = 0xFF000000
)
@Composable
private fun LoginScreenRoundPreview() {
    BioGuard_WearOsTheme {
        LoginScreenStatic(showChecking = true)
    }
}

@Composable
private fun LoginScreenStatic(showChecking: Boolean) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(BioGuardTealBase, BioGuardBackground),
                    radius = 600f
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Sin",
                color = BioGuardOnSurface,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Text(
                text = "Vincular",
                color = BioGuardError,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "Abre BioGuard en tu tel\u00e9fono para vincular el reloj",
                color = BioGuardOnSurfaceVariant,
                fontSize = 10.sp,
                textAlign = TextAlign.Center,
                lineHeight = 14.sp
            )
            Spacer(modifier = Modifier.height(14.dp))
            if (showChecking) {
                CircularProgressIndicator(modifier = Modifier.size(28.dp))
                Spacer(modifier = Modifier.height(6.dp))
                Text(text = "Buscando vinculaci\u00f3n...", color = BioGuardOnSurfaceVariant, fontSize = 9.sp)
            } else {
                Button(
                    onClick = {},
                    modifier = Modifier.height(36.dp).width(130.dp),
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = BioGuardError)
                ) {
                    Text(
                        text = "REINTENTAR",
                        color = BioGuardOnSurface,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}
