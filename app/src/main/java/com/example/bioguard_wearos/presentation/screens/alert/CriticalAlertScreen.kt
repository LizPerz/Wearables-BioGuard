package com.example.bioguard_wearos.presentation.screens.alert

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.Text
import androidx.wear.compose.ui.tooling.preview.WearPreviewDevices
import androidx.wear.compose.ui.tooling.preview.WearPreviewFontScales
import com.example.bioguard_wearos.presentation.theme.BioGuardError
import com.example.bioguard_wearos.presentation.theme.BioGuardErrorDark
import com.example.bioguard_wearos.presentation.theme.BioGuardOnSurface
import com.example.bioguard_wearos.presentation.theme.BioGuardOnSurfaceVariant
import com.example.bioguard_wearos.presentation.theme.BioGuardPrimary
import com.example.bioguard_wearos.presentation.theme.BioGuardSurface
import com.example.bioguard_wearos.presentation.theme.BioGuardBackground
import com.example.bioguard_wearos.presentation.theme.BioGuard_WearOsTheme
import java.util.Locale

@Composable
fun CriticalAlertScreen(
    viewModel: CriticalAlertViewModel
) {
    val uiState by viewModel.uiState.collectAsState()

    val pulseAlpha by rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    CriticalAlertContent(
        bpm = uiState.bpm,
        temperature = uiState.temperature,
        gsr = uiState.gsr,
        probability = uiState.probability,
        countdownSeconds = uiState.countdownSeconds,
        isCountdownActive = uiState.isCountdownActive,
        pulseAlpha = pulseAlpha,
        onDismiss = { viewModel.dismissAlert() },
        onHelp = { viewModel.requestHelp() }
    )
}

@Composable
private fun CriticalAlertContent(
    bpm: Float,
    temperature: Float,
    gsr: Float,
    probability: Float,
    countdownSeconds: Int,
    isCountdownActive: Boolean,
    pulseAlpha: Float,
    onDismiss: () -> Unit,
    onHelp: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        BioGuardError.copy(alpha = pulseAlpha * 0.5f),
                        BioGuardErrorDark.copy(alpha = pulseAlpha * 0.15f),
                        BioGuardBackground
                    ),
                    radius = 700f
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .alpha(pulseAlpha)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(BioGuardError, BioGuardErrorDark)
                        ),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "!",
                    color = BioGuardOnSurface,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "ALERTA CR\u00cdTICA",
                color = BioGuardError,
                fontSize = 14.sp,
                fontWeight = FontWeight.ExtraBold
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = "Pico de riesgo detectado",
                color = BioGuardOnSurfaceVariant,
                fontSize = 9.sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                VitalColumn("BPM", String.format(Locale.US, "%.0f", bpm))
                VitalColumn("Temp", String.format(Locale.US, "%.1f", temperature))
                VitalColumn("GSR", String.format(Locale.US, "%.0f", gsr))
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "P(Pico): ${String.format(Locale.US, "%.1f", probability * 100)}%",
                color = BioGuardPrimary,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(4.dp))

            if (isCountdownActive) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(BioGuardSurface, BioGuardBackground)
                            ),
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "$countdownSeconds",
                        color = BioGuardError,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "Auto-env\u00edo a red familiar",
                    color = BioGuardOnSurfaceVariant,
                    fontSize = 8.sp,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Button(
                    onClick = onDismiss,
                    modifier = Modifier
                        .weight(1f)
                        .height(34.dp),
                    shape = RoundedCornerShape(17.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = BioGuardSurface
                    )
                ) {
                    Text(
                        text = "ESTOY BIEN",
                        color = BioGuardOnSurface,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Button(
                    onClick = onHelp,
                    modifier = Modifier
                        .weight(1f)
                        .height(34.dp),
                    shape = RoundedCornerShape(17.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = BioGuardError
                    )
                ) {
                    Text(
                        text = "AYUDA",
                        color = BioGuardOnSurface,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
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
private fun CriticalAlertScreenPreview() {
    val pulseAlpha by rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 0.4f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(500, easing = LinearEasing), RepeatMode.Reverse),
        label = "pulseAlpha"
    )
    BioGuard_WearOsTheme {
        CriticalAlertContent(
            bpm = 125f, temperature = 34.5f, gsr = 92f, probability = 0.92f,
            countdownSeconds = 45, isCountdownActive = true, pulseAlpha = pulseAlpha,
            onDismiss = {}, onHelp = {}
        )
    }
}

@Preview(
    device = "spec:width=454.0dp,height=454.0dp,dpi=326,isRound=true",
    showBackground = true,
    backgroundColor = 0xFF000000
)
@Composable
private fun CriticalAlertScreenRoundPreview() {
    val pulseAlpha by rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 0.4f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(500, easing = LinearEasing), RepeatMode.Reverse),
        label = "pulseAlpha"
    )
    BioGuard_WearOsTheme {
        CriticalAlertContent(
            bpm = 125f, temperature = 34.5f, gsr = 92f, probability = 0.92f,
            countdownSeconds = 12, isCountdownActive = true, pulseAlpha = pulseAlpha,
            onDismiss = {}, onHelp = {}
        )
    }
}

@Composable
private fun RowScope.VitalColumn(label: String, value: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.weight(1f)
    ) {
        Text(
            text = label,
            color = BioGuardOnSurfaceVariant,
            fontSize = 8.sp
        )
        Text(
            text = value,
            color = BioGuardOnSurface,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
