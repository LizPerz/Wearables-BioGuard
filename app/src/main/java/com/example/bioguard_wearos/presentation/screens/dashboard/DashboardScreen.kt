package com.example.bioguard_wearos.presentation.screens.dashboard

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.Text
import androidx.wear.compose.ui.tooling.preview.WearPreviewDevices
import androidx.wear.compose.ui.tooling.preview.WearPreviewFontScales
import com.example.bioguard_wearos.R
import com.example.bioguard_wearos.presentation.theme.BioGuardOnSurface
import com.example.bioguard_wearos.presentation.theme.BioGuardOnSurfaceVariant
import com.example.bioguard_wearos.presentation.theme.BioGuardPrimary
import com.example.bioguard_wearos.presentation.theme.BioGuardError
import com.example.bioguard_wearos.presentation.theme.BioGuardMetricWarning
import com.example.bioguard_wearos.presentation.theme.BioGuardSurface
import com.example.bioguard_wearos.presentation.theme.BioGuardBackground
import com.example.bioguard_wearos.presentation.theme.BioGuardTealBase
import com.example.bioguard_wearos.presentation.theme.BioGuardStressHigh
import com.example.bioguard_wearos.presentation.theme.BioGuardStressModerate
import com.example.bioguard_wearos.presentation.theme.BioGuardStressLow
import com.example.bioguard_wearos.presentation.theme.BioGuard_WearOsTheme
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.tasks.await

private enum class MetricStatus { OPTIMAL, MODERATE, CRITICAL }

private fun resolveBpmStatus(bpm: Float): MetricStatus = when {
    bpm <= 0f -> MetricStatus.OPTIMAL
    bpm in 60f..100f -> MetricStatus.OPTIMAL
    bpm in 100f..120f -> MetricStatus.MODERATE
    else -> MetricStatus.CRITICAL
}

private fun resolveTempStatus(temp: Float): MetricStatus = when {
    temp <= 0f -> MetricStatus.OPTIMAL
    temp in 36f..37f -> MetricStatus.OPTIMAL
    temp in 35f..<36f || temp in 37f..37.4f -> MetricStatus.MODERATE
    else -> MetricStatus.CRITICAL
}

private fun resolveGsrStatus(gsr: Float): MetricStatus = when {
    gsr <= 0f -> MetricStatus.OPTIMAL
    gsr < 40f -> MetricStatus.OPTIMAL
    gsr in 40f..65f -> MetricStatus.MODERATE
    else -> MetricStatus.CRITICAL
}

private fun metricColors(status: MetricStatus): List<Color> = when (status) {
    MetricStatus.OPTIMAL -> listOf(BioGuardPrimary, BioGuardPrimary.copy(alpha = 0.7f))
    MetricStatus.MODERATE -> listOf(BioGuardMetricWarning, BioGuardMetricWarning.copy(alpha = 0.7f))
    MetricStatus.CRITICAL -> listOf(BioGuardError, BioGuardError.copy(alpha = 0.7f))
}

private fun metricIconTint(status: MetricStatus): Color = when (status) {
    MetricStatus.OPTIMAL -> BioGuardOnSurface
    MetricStatus.MODERATE -> BioGuardBackground
    MetricStatus.CRITICAL -> BioGuardOnSurface
}

@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        val nodeClient = Wearable.getNodeClient(context)
        val nodes = nodeClient.connectedNodes.await()
        viewModel.setPhoneConnected(nodes.isNotEmpty())
    }

    val sensorData = uiState.sensorData
    val bpmStatus = resolveBpmStatus(sensorData.bpm)
    val tempStatus = resolveTempStatus(sensorData.temperature)
    val gsrStatus = resolveGsrStatus(sensorData.gsr)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(BioGuardBackground, BioGuardTealBase.copy(alpha = 0.3f), BioGuardBackground)
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Image(
                painter = painterResource(id = R.drawable.bioguard),
                contentDescription = "BioGuard Logo",
                modifier = Modifier.size(60.dp)
            )
            Spacer(modifier = Modifier.height(6.dp))

            MetricPill(
                icon = { Icon(Icons.Rounded.Favorite, null, Modifier.size(16.dp), tint = metricIconTint(bpmStatus)) },
                value = String.format("%.0f", sensorData.bpm),
                label = "BPM",
                fillFraction = sensorData.hrFraction,
                gradientColors = metricColors(bpmStatus),
                onClick = {
                    viewModel.showDetailDialog(
                        "Ritmo Card\u00edaco",
                        String.format("%.0f", sensorData.bpm),
                        "BPM",
                        DetailIcon.HEART
                    )
                }
            )
            Spacer(modifier = Modifier.height(4.dp))

            MetricPill(
                icon = { Icon(Icons.Rounded.Thermostat, null, Modifier.size(16.dp), tint = metricIconTint(tempStatus)) },
                value = String.format("%.1f", sensorData.temperature),
                label = "\u00b0C",
                fillFraction = sensorData.tempFraction,
                gradientColors = metricColors(tempStatus),
                onClick = {
                    viewModel.showDetailDialog(
                        "Temperatura",
                        String.format("%.1f", sensorData.temperature),
                        "\u00b0C",
                        DetailIcon.THERMOMETER
                    )
                }
            )
            Spacer(modifier = Modifier.height(4.dp))

            MetricPill(
                icon = { Icon(Icons.Rounded.WaterDrop, null, Modifier.size(16.dp), tint = metricIconTint(gsrStatus)) },
                value = String.format("%.0f", sensorData.gsr),
                label = "\u00b5S",
                fillFraction = sensorData.gsrFraction,
                gradientColors = metricColors(gsrStatus),
                onClick = {
                    viewModel.showDetailDialog(
                        "Estr\u00e9s (HRV)",
                        String.format("%.0f", sensorData.gsr),
                        "\u00b5S",
                        DetailIcon.DROPS,
                        hrvRmssd = sensorData.rmssd,
                        hrvSdnn = sensorData.sdnn,
                        stressLabel = sensorData.stressLabel
                    )
                }
            )
            Spacer(modifier = Modifier.height(10.dp))

            if (!uiState.isPhoneConnected) {
                Row(
                    modifier = Modifier.padding(bottom = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Box(
                        Modifier
                            .size(14.dp)
                            .shadow(4.dp, RoundedCornerShape(7.dp), ambientColor = BioGuardPrimary.copy(alpha = 0.4f), spotColor = BioGuardPrimary.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Rounded.Bluetooth, null, Modifier.fillMaxSize(), tint = BioGuardPrimary)
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "SINCRONIZANDO",
                        color = BioGuardPrimary,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }

    if (uiState.showDetailDialog) {
        DetailDialog(
            title = uiState.dialogTitle,
            value = uiState.dialogValue,
            unit = uiState.dialogUnit,
            icon = when (uiState.dialogIcon) {
                DetailIcon.HEART -> { @Composable { Icon(Icons.Rounded.Favorite, null, Modifier.size(28.dp), tint = BioGuardPrimary) } }
                DetailIcon.THERMOMETER -> { @Composable { Icon(Icons.Rounded.Thermostat, null, Modifier.size(28.dp), tint = BioGuardPrimary) } }
                DetailIcon.DROPS -> { @Composable { Icon(Icons.Rounded.WaterDrop, null, Modifier.size(28.dp), tint = BioGuardPrimary) } }
            },
            hrvRmssd = uiState.dialogHrvRmssd,
            hrvSdnn = uiState.dialogHrvSdnn,
            stressLabel = uiState.dialogStressLabel,
            onDismiss = { viewModel.dismissDetailDialog() }
        )
    }
}

@Composable
private fun DetailDialog(
    title: String,
    value: String,
    unit: String,
    icon: @Composable () -> Unit,
    hrvRmssd: Float,
    hrvSdnn: Float,
    stressLabel: String,
    onDismiss: () -> Unit
) {
    val showHrv = hrvRmssd > 0f || hrvSdnn > 0f
    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(BioGuardBackground.copy(alpha = 0.94f)),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(
                            Brush.radialGradient(
                                colors = listOf(BioGuardPrimary.copy(alpha = 0.25f), BioGuardPrimary.copy(alpha = 0.05f))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    icon()
                }
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = title,
                    color = BioGuardPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = value,
                        color = BioGuardOnSurface,
                        fontSize = 36.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = unit,
                        color = BioGuardOnSurfaceVariant,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }
                if (showHrv && stressLabel.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stressLabel,
                        color = when {
                            stressLabel.contains("Alto") -> BioGuardStressHigh
                            stressLabel.contains("Moderado") -> BioGuardStressModerate
                            else -> BioGuardStressLow
                        },
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                if (showHrv) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "RMSSD: ${String.format("%.1f", hrvRmssd)} ms",
                            color = BioGuardOnSurfaceVariant,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "SDNN: ${String.format("%.1f", hrvSdnn)} ms",
                            color = BioGuardOnSurfaceVariant,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = onDismiss,
                    modifier = Modifier
                        .width(120.dp)
                        .height(34.dp),
                    shape = RoundedCornerShape(17.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = BioGuardPrimary)
                ) {
                    Text(
                        text = "Cerrar",
                        color = BioGuardBackground,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
private fun MetricPill(
    icon: @Composable () -> Unit,
    value: String,
    label: String,
    fillFraction: Float,
    gradientColors: List<Color>,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .width(160.dp)
            .height(30.dp)
            .shadow(4.dp, RoundedCornerShape(15.dp), ambientColor = gradientColors[0].copy(alpha = 0.35f), spotColor = gradientColors[0].copy(alpha = 0.2f))
            .clip(RoundedCornerShape(15.dp))
            .background(BioGuardSurface)
            .clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fillFraction)
                .fillMaxHeight()
                .clip(RoundedCornerShape(15.dp))
                .background(Brush.horizontalGradient(colors = gradientColors))
        )
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            icon()
            Spacer(modifier = Modifier.width(4.dp))
            Text(text = value, color = BioGuardOnSurface, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.weight(1f))
            Text(text = label, color = BioGuardOnSurfaceVariant.copy(alpha = 0.8f), fontSize = 11.sp)
        }
    }
}

@WearPreviewDevices
@WearPreviewFontScales
@Composable
private fun DashboardScreenPreview() {
    BioGuard_WearOsTheme {
        DashboardScreenStatic(bpm = 78f, temp = 36.4f, gsr = 28f, hrFrac = 0.65f, tempFrac = 0.5f, gsrFrac = 0.35f)
    }
}

@Preview(
    device = "spec:width=454.0dp,height=454.0dp,dpi=326,isRound=true",
    showBackground = true,
    backgroundColor = 0xFF000000
)
@Composable
private fun DashboardScreenRoundPreview() {
    BioGuard_WearOsTheme {
        DashboardScreenStatic(bpm = 112f, temp = 37.5f, gsr = 65f, hrFrac = 0.85f, tempFrac = 0.7f, gsrFrac = 0.72f)
    }
}

@Composable
private fun DashboardScreenStatic(bpm: Float, temp: Float, gsr: Float, hrFrac: Float, tempFrac: Float, gsrFrac: Float) {
    val bpmStatus = resolveBpmStatus(bpm)
    val tempStatus = resolveTempStatus(temp)
    val gsrStatus = resolveGsrStatus(gsr)

    Box(
        modifier = Modifier.fillMaxSize().background(
            Brush.verticalGradient(
                colors = listOf(BioGuardBackground, BioGuardTealBase.copy(alpha = 0.3f), BioGuardBackground)
            )
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Image(
                painter = painterResource(id = R.drawable.bioguard),
                contentDescription = "BioGuard Logo",
                modifier = Modifier.size(60.dp)
            )
            Spacer(modifier = Modifier.height(6.dp))
            MetricPill(
                icon = { Icon(Icons.Rounded.Favorite, null, Modifier.size(16.dp), tint = metricIconTint(bpmStatus)) },
                value = String.format("%.0f", bpm),
                label = "BPM",
                fillFraction = hrFrac,
                gradientColors = metricColors(bpmStatus),
                onClick = {}
            )
            Spacer(modifier = Modifier.height(4.dp))
            MetricPill(
                icon = { Icon(Icons.Rounded.Thermostat, null, Modifier.size(16.dp), tint = metricIconTint(tempStatus)) },
                value = String.format("%.1f", temp),
                label = "\u00b0C",
                fillFraction = tempFrac,
                gradientColors = metricColors(tempStatus),
                onClick = {}
            )
            Spacer(modifier = Modifier.height(4.dp))
            MetricPill(
                icon = { Icon(Icons.Rounded.WaterDrop, null, Modifier.size(16.dp), tint = metricIconTint(gsrStatus)) },
                value = String.format("%.0f", gsr),
                label = "\u00b5S",
                fillFraction = gsrFrac,
                gradientColors = metricColors(gsrStatus),
                onClick = {}
            )
        }
    }
}
