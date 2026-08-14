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
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
    viewModel: CriticalAlertViewModel,
    onDismissed: () -> Unit = {}
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

    val context = androidx.compose.ui.platform.LocalContext.current

    CriticalAlertContent(
        bpm = uiState.bpm,
        temperature = uiState.temperature,
        estresPct = uiState.estresPct,
        probability = uiState.probability,
        countdownSeconds = uiState.countdownSeconds,
        isCountdownActive = uiState.isCountdownActive,
        pulseAlpha = pulseAlpha,
        helpRequested = uiState.helpRequested,
        helpSent = uiState.helpSent,
        onDismiss = {
            viewModel.acknowledgeAlert(context)
            onDismissed()
        },
        onHelp = { viewModel.requestHelp(context) }
    )
}

@Composable
private fun CriticalAlertContent(
    bpm: Float,
    temperature: Float,
    estresPct: Float,
    probability: Float,
    countdownSeconds: Int,
    isCountdownActive: Boolean,
    pulseAlpha: Float,
    helpRequested: Boolean,
    helpSent: Boolean,
    onDismiss: () -> Unit,
    onHelp: () -> Unit
) {
    BoxWithConstraints(
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
        val contentWidth = maxWidth * 0.85f
        val iconSize = if (maxWidth < 200.dp) 34.dp else 40.dp
        val countdownSize = if (maxWidth < 200.dp) 34.dp else 40.dp
        val buttonHeight = if (maxWidth < 200.dp) 28.dp else 32.dp
        val titleSize = if (maxWidth < 200.dp) 12.sp else 14.sp
        val valueSize = if (maxWidth < 200.dp) 10.sp else 12.sp
        val labelSize = if (maxWidth < 200.dp) 7.sp else 8.sp
        val countdownTextSize = if (maxWidth < 200.dp) 14.sp else 16.sp
        val helpTextSize = if (maxWidth < 200.dp) 9.sp else 10.sp

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(iconSize)
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
                    fontSize = titleSize,
                    fontWeight = FontWeight.ExtraBold
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "ALERTA CR\u00cdTICA",
                color = BioGuardError,
                fontSize = titleSize,
                fontWeight = FontWeight.ExtraBold
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = "Pico de riesgo detectado",
                color = BioGuardOnSurfaceVariant,
                fontSize = labelSize
            )

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                VitalColumn("BPM", String.format(Locale.US, "%.0f", bpm), labelSize, valueSize)
                VitalColumn("Temp", String.format(Locale.US, "%.1f", temperature), labelSize, valueSize)
                VitalColumn("Estrés %", String.format(Locale.US, "%.0f", estresPct), labelSize, valueSize)
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "P(Pico): ${String.format(Locale.US, "%.1f", probability * 100)}%",
                color = BioGuardPrimary,
                fontSize = labelSize,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(3.dp))

            if (isCountdownActive) {
                Box(
                    modifier = Modifier
                        .size(countdownSize)
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
                        fontSize = countdownTextSize,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(3.dp))

                Text(
                    text = "Auto-env\u00edo a red familiar",
                    color = BioGuardOnSurfaceVariant,
                    fontSize = labelSize,
                    textAlign = TextAlign.Center,
                    maxLines = 1
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (helpRequested) {
                Text(
                    text = if (helpSent) "AYUDA ENVIADA" else "SOLICITANDO AYUDA...",
                    color = if (helpSent) BioGuardPrimary else BioGuardError,
                    fontSize = helpTextSize,
                    fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(4.dp))
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Button(
                    onClick = onDismiss,
                    modifier = Modifier
                        .weight(1f)
                        .height(buttonHeight),
                    shape = RoundedCornerShape(buttonHeight / 2),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = BioGuardSurface
                    )
                ) {
                    Text(
                        text = "ATENDIDO",
                        color = BioGuardOnSurface,
                        fontWeight = FontWeight.Bold,
                        fontSize = labelSize,
                        textAlign = TextAlign.Center,
                        maxLines = 1
                    )
                }

                Button(
                    onClick = onHelp,
                    modifier = Modifier
                        .weight(1f)
                        .height(buttonHeight),
                    shape = RoundedCornerShape(buttonHeight / 2),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = BioGuardError
                    )
                ) {
                    Text(
                        text = "AYUDA",
                        color = BioGuardOnSurface,
                        fontWeight = FontWeight.Bold,
                        fontSize = labelSize,
                        textAlign = TextAlign.Center,
                        maxLines = 1
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
            bpm = 125f, temperature = 34.5f, estresPct = 92f, probability = 0.92f,
            countdownSeconds = 45, isCountdownActive = true, pulseAlpha = pulseAlpha,
            helpRequested = false, helpSent = false,
            onDismiss = {}, onHelp = {}
        )
    }
}

@Composable
private fun RowScope.VitalColumn(label: String, value: String, labelSize: androidx.compose.ui.unit.TextUnit, valueSize: androidx.compose.ui.unit.TextUnit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.weight(1f)
    ) {
        Text(
            text = label,
            color = BioGuardOnSurfaceVariant,
            fontSize = labelSize
        )
        Text(
            text = value,
            color = BioGuardOnSurface,
            fontSize = valueSize,
            fontWeight = FontWeight.Bold
        )
    }
}
