package com.example.bioguard_wearos.presentation.screens.dashboard

import android.graphics.Bitmap
import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.Text
import java.util.Locale
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
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.tasks.await

internal enum class MetricStatus { UNAVAILABLE, OPTIMAL, MODERATE, CRITICAL }

internal fun resolveBpmStatus(bpm: Float): MetricStatus = when {
    bpm <= 0f -> MetricStatus.UNAVAILABLE
    bpm in 60f..100f -> MetricStatus.OPTIMAL
    bpm in 100f..120f -> MetricStatus.MODERATE
    else -> MetricStatus.CRITICAL
}

internal fun resolveTempStatus(temp: Float): MetricStatus = when {
    temp <= 0f -> MetricStatus.UNAVAILABLE
    temp in 36f..37f -> MetricStatus.OPTIMAL
    temp in 35f..<36f || temp in 37f..37.4f -> MetricStatus.MODERATE
    else -> MetricStatus.CRITICAL
}

internal fun resolveStressStatus(estresPct: Float): MetricStatus = when {
    estresPct <= 0f -> MetricStatus.UNAVAILABLE
    estresPct < 50f -> MetricStatus.OPTIMAL
    estresPct in 50f..80f -> MetricStatus.MODERATE
    else -> MetricStatus.CRITICAL
}

private fun metricColors(status: MetricStatus): List<Color> = when (status) {
    MetricStatus.UNAVAILABLE -> listOf(BioGuardOnSurfaceVariant.copy(alpha = 0.45f), BioGuardSurface)
    MetricStatus.OPTIMAL -> listOf(BioGuardPrimary, BioGuardPrimary.copy(alpha = 0.7f))
    MetricStatus.MODERATE -> listOf(BioGuardMetricWarning, BioGuardMetricWarning.copy(alpha = 0.7f))
    MetricStatus.CRITICAL -> listOf(BioGuardError, BioGuardError.copy(alpha = 0.7f))
}

private fun metricIconTint(status: MetricStatus): Color = when (status) {
    MetricStatus.UNAVAILABLE -> BioGuardOnSurfaceVariant
    MetricStatus.OPTIMAL -> BioGuardOnSurface
    MetricStatus.MODERATE -> BioGuardBackground
    MetricStatus.CRITICAL -> BioGuardOnSurface
}

private const val TAG = "DashboardScreen"

private fun generateQrBitmap(payload: String, sizePx: Int = 512): Bitmap {
    val hints = mapOf(
        EncodeHintType.CHARACTER_SET to "UTF-8",
        EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.L,
        EncodeHintType.MARGIN to 1
    )
    val matrix = QRCodeWriter().encode(payload, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
    return Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888).apply {
        for (x in 0 until sizePx) {
            for (y in 0 until sizePx) {
                setPixel(x, y, if (matrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
            }
        }
    }
}

@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showPairingQr by remember { mutableStateOf(false) }
    var pairingPayload by remember { mutableStateOf("") }
    var showUnlinkConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        while (isActive) {
            val nodeClient = runCatching { Wearable.getNodeClient(context) }.getOrNull()
            val trustedNodeId = runCatching { viewModel.getTrustedMobileNodeId() }
                .onFailure { Log.w(TAG, "No se pudo consultar movil vinculado: ${it.message}") }
                .getOrNull()
            val nodes = runCatching { nodeClient?.connectedNodes?.await().orEmpty() }
                .onFailure { Log.w(TAG, "No se pudo consultar nodos conectados: ${it.message}") }
                .getOrDefault(emptyList())
            val connected = nodes.any { it.isNearby && (trustedNodeId.isNullOrBlank() || it.id == trustedNodeId) }
            viewModel.setPhoneConnectionStatus(
                paired = !trustedNodeId.isNullOrBlank(),
                connected = connected
            )
            delay(15_000)
        }
    }

    val sensorData = uiState.sensorData
    val bpmStatus = resolveBpmStatus(sensorData.bpm)
    val tempStatus = resolveTempStatus(sensorData.temperature)
    val stressAvailable = sensorData.rmssd > 0f || sensorData.estresPct > 0f
    val stressStatus = if (stressAvailable) {
        resolveStressStatus(sensorData.estresPct)
    } else {
        MetricStatus.UNAVAILABLE
    }

    val listState = rememberScalingLazyListState()

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(BioGuardBackground, BioGuardTealBase.copy(alpha = 0.3f), BioGuardBackground)
                )
            )
    ) {
        val screenWidth = maxWidth
        val contentWidth = screenWidth * 0.85f

        ScalingLazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item {
                Image(
                    painter = painterResource(id = R.drawable.bioguard),
                    contentDescription = "BioGuard Logo",
                    modifier = Modifier.size(80.dp)
                )
            }

            item {
                MetricPill(
                    icon = { Icon(Icons.Rounded.Favorite, null, Modifier.size(14.dp), tint = metricIconTint(bpmStatus)) },
                    value = if (sensorData.bpm > 0f) String.format(Locale.ROOT, "%.0f", sensorData.bpm) else "--",
                    label = "BPM",
                    fillFraction = sensorData.hrFraction,
                    gradientColors = metricColors(bpmStatus),
                    contentWidth = contentWidth,
                    onClick = {
                        viewModel.showDetailDialog(
                            "Ritmo Cardíaco",
                            if (sensorData.bpm > 0f) String.format(Locale.ROOT, "%.0f", sensorData.bpm) else "No disponible",
                            "BPM",
                            DetailIcon.HEART
                        )
                    }
                )
            }

            item {
                MetricPill(
                    icon = { Icon(Icons.Rounded.Thermostat, null, Modifier.size(14.dp), tint = metricIconTint(tempStatus)) },
                    value = if (sensorData.temperature > 0f) String.format(Locale.ROOT, "%.1f", sensorData.temperature) else "--",
                    label = "°C",
                    fillFraction = sensorData.tempFraction,
                    gradientColors = metricColors(tempStatus),
                    contentWidth = contentWidth,
                    onClick = {
                        viewModel.showDetailDialog(
                            "Temperatura",
                            if (sensorData.temperature > 0f) String.format(Locale.ROOT, "%.1f", sensorData.temperature) else "No disponible",
                            "°C",
                            DetailIcon.THERMOMETER
                        )
                    }
                )
            }

            item {
                MetricPill(
                    icon = { Icon(Icons.Rounded.WaterDrop, null, Modifier.size(14.dp), tint = metricIconTint(stressStatus)) },
                    value = if (stressAvailable) String.format(Locale.ROOT, "%.0f", sensorData.estresPct) else "--",
                    label = "indice ESTRES",
                    fillFraction = sensorData.estresFraction,
                    gradientColors = metricColors(stressStatus),
                    contentWidth = contentWidth,
                    onClick = {
                        viewModel.showDetailDialog(
                            "Estrés (HRV)",
                            if (stressAvailable) String.format(Locale.ROOT, "%.0f", sensorData.estresPct) else "No disponible",
                            "indice",
                            DetailIcon.DROPS,
                            hrvRmssd = sensorData.rmssd,
                            hrvSdnn = sensorData.sdnn,
                            stressLabel = sensorData.stressLabel
                        )
                    }
                )
            }

            item {
                val glucoseVal = sensorData.estimatedGlucoseMgDl
                val glucoseAvailable = glucoseVal > 0f
                val glucoseStatus = when {
                    !glucoseAvailable -> MetricStatus.UNAVAILABLE
                    glucoseVal > 140f -> MetricStatus.CRITICAL
                    glucoseVal < 70f -> MetricStatus.MODERATE
                    else -> MetricStatus.OPTIMAL
                }
                MetricPill(
                    icon = { Text("🩸", fontSize = 12.sp) },
                    value = if (glucoseAvailable) String.format(Locale.ROOT, "%.0f", glucoseVal) else "--",
                    label = "mg/dL Glucosa",
                    fillFraction = if (glucoseAvailable) ((glucoseVal - 70f) / 150f).coerceIn(0.05f, 1f) else 0.05f,
                    gradientColors = metricColors(glucoseStatus),
                    contentWidth = contentWidth,
                    onClick = {
                        viewModel.showDetailDialog(
                            "Glucosa Estimada",
                            if (glucoseAvailable) String.format(Locale.ROOT, "%.0f", glucoseVal) else "No disponible",
                            "mg/dL",
                            DetailIcon.DROPS
                        )
                    }
                )
            }

            item {
                CompactButton(
                    onClick = { viewModel.triggerPanicButton() },
                    contentWidth = contentWidth,
                    containerColor = BioGuardError,
                    icon = { Icon(Icons.Rounded.Warning, null, Modifier.size(14.dp), tint = BioGuardOnSurface) },
                    text = "S.O.S / PÁNICO"
                )
            }

            item {
                Row(
                    modifier = Modifier.padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    val phoneStatusColor = if (uiState.isPhoneConnected) BioGuardPrimary else BioGuardMetricWarning
                    Box(
                        Modifier
                            .size(12.dp)
                            .shadow(2.dp, RoundedCornerShape(6.dp), ambientColor = phoneStatusColor.copy(alpha = 0.4f), spotColor = phoneStatusColor.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Rounded.Bluetooth, null, Modifier.fillMaxSize(), tint = phoneStatusColor)
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = when {
                            uiState.isPhoneConnected -> "MOVIL CONECTADO"
                            uiState.isPhonePaired -> "VINCULADO SIN SENAL"
                            else -> "SIN MOVIL"
                        },
                        color = phoneStatusColor,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center,
                        maxLines = 1
                    )
                }
            }

            uiState.panicStatusMessage?.let { message ->
                item {
                    Text(
                        text = message,
                        color = BioGuardError,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth(0.85f)
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }

            uiState.sensorStatusMessage?.let { message ->
                item {
                    Text(
                        text = message,
                        color = BioGuardMetricWarning,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth(0.85f)
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }

            item {
                if (uiState.isPhonePaired) {
                    CompactButton(
                        onClick = { showUnlinkConfirm = true },
                        contentWidth = contentWidth,
                        containerColor = BioGuardMetricWarning,
                        icon = { Icon(Icons.Rounded.Bluetooth, null, Modifier.size(14.dp), tint = BioGuardBackground) },
                        text = "DESVINCULAR MOVIL"
                    )
                } else {
                    CompactButton(
                        onClick = {
                            scope.launch {
                                val nodeClient = runCatching { Wearable.getNodeClient(context) }.getOrNull()
                                val installId = viewModel.getInstallId()
                                val localNode = runCatching { nodeClient?.localNode?.await() }
                                    .onFailure { Log.w(TAG, "No se pudo resolver nodo local para QR") }
                                    .getOrNull()
                                pairingPayload = viewModel.createPairingPayload(
                                    deviceName = localNode?.displayName?.ifBlank { "BioGuard Wearable" }
                                        ?: "BioGuard Wearable",
                                    deviceId = installId,
                                    nodeId = localNode?.id ?: installId
                                )
                                showPairingQr = true
                            }
                        },
                        contentWidth = contentWidth,
                        containerColor = BioGuardPrimary,
                        icon = { Icon(Icons.Rounded.Bluetooth, null, Modifier.size(14.dp), tint = BioGuardBackground) },
                        text = "QR VINCULAR"
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
                DetailIcon.HEART -> { @Composable { Icon(Icons.Rounded.Favorite, null, Modifier.size(24.dp), tint = BioGuardPrimary) } }
                DetailIcon.THERMOMETER -> { @Composable { Icon(Icons.Rounded.Thermostat, null, Modifier.size(24.dp), tint = BioGuardPrimary) } }
                DetailIcon.DROPS -> { @Composable { Icon(Icons.Rounded.WaterDrop, null, Modifier.size(24.dp), tint = BioGuardPrimary) } }
            },
            hrvRmssd = uiState.dialogHrvRmssd,
            hrvSdnn = uiState.dialogHrvSdnn,
            stressLabel = uiState.dialogStressLabel,
            onDismiss = { viewModel.dismissDetailDialog() }
        )
    }

    if (showPairingQr && pairingPayload.isNotBlank()) {
        PairingQrDialog(
            payload = pairingPayload,
            onDismiss = { showPairingQr = false }
        )
    }

    if (showUnlinkConfirm) {
        UnlinkConfirmDialog(
            onDismiss = { showUnlinkConfirm = false },
            onConfirm = {
                showUnlinkConfirm = false
                viewModel.unlinkPhone()
            }
        )
    }
}

@Composable
private fun UnlinkConfirmDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
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
                    .padding(horizontal = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "¿Desvincular este móvil?",
                    color = BioGuardOnSurface,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Dejarás de enviar datos al teléfono actual",
                    color = BioGuardOnSurfaceVariant,
                    fontSize = 9.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier
                            .width(80.dp)
                            .height(30.dp),
                        shape = RoundedCornerShape(15.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = BioGuardOnSurfaceVariant)
                    ) {
                        Text(
                            text = "CANCELAR",
                            color = BioGuardBackground,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Button(
                        onClick = onConfirm,
                        modifier = Modifier
                            .width(100.dp)
                            .height(30.dp),
                        shape = RoundedCornerShape(15.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = BioGuardError)
                    ) {
                        Text(
                            text = "SÍ, DESVINCULAR",
                            color = BioGuardOnSurface,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PairingQrDialog(
    payload: String,
    onDismiss: () -> Unit
) {
    val qrBitmap = remember(payload) { generateQrBitmap(payload, 512) }
    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(BioGuardBackground.copy(alpha = 0.96f))
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(horizontal = 10.dp)
            ) {
                Image(
                    bitmap = qrBitmap.asImageBitmap(),
                    contentDescription = "QR de vinculacion Bluetooth BioGuard",
                    modifier = Modifier
                        .size(180.dp)
                        .background(Color.White)
                        .padding(8.dp)
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "ESCANEAR EN APP",
                    color = BioGuardPrimary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            }
        }
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
                    .padding(horizontal = 18.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                            Brush.radialGradient(
                                colors = listOf(BioGuardPrimary.copy(alpha = 0.25f), BioGuardPrimary.copy(alpha = 0.05f))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    icon()
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = title,
                    color = BioGuardPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = value,
                        color = BioGuardOnSurface,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Text(
                        text = unit,
                        color = BioGuardOnSurfaceVariant,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(bottom = 3.dp)
                    )
                }
                if (showHrv && stressLabel.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = stressLabel,
                        color = when {
                            stressLabel.contains("Alto") -> BioGuardStressHigh
                            stressLabel.contains("Moderado") -> BioGuardStressModerate
                            else -> BioGuardStressLow
                        },
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                if (showHrv) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "RMSSD: ${String.format(Locale.ROOT, "%.1f", hrvRmssd)} ms",
                            color = BioGuardOnSurfaceVariant,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "SDNN: ${String.format(Locale.ROOT, "%.1f", hrvSdnn)} ms",
                            color = BioGuardOnSurfaceVariant,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = onDismiss,
                    modifier = Modifier
                        .width(100.dp)
                        .height(30.dp),
                    shape = RoundedCornerShape(15.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = BioGuardPrimary)
                ) {
                    Text(
                        text = "Cerrar",
                        color = BioGuardBackground,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
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
    contentWidth: Dp,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .width(contentWidth)
            .height(28.dp)
            .shadow(3.dp, RoundedCornerShape(14.dp), ambientColor = gradientColors[0].copy(alpha = 0.3f), spotColor = gradientColors[0].copy(alpha = 0.15f))
            .clip(RoundedCornerShape(14.dp))
            .background(BioGuardSurface)
            .clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fillFraction)
                .fillMaxHeight()
                .clip(RoundedCornerShape(14.dp))
                .background(Brush.horizontalGradient(colors = gradientColors))
        )
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            icon()
            Spacer(modifier = Modifier.width(3.dp))
            Text(text = value, color = BioGuardOnSurface, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.weight(1f))
            Text(text = label, color = BioGuardOnSurfaceVariant.copy(alpha = 0.8f), fontSize = 10.sp)
        }
    }
}

@Composable
private fun CompactButton(
    onClick: () -> Unit,
    contentWidth: Dp,
    containerColor: Color,
    icon: @Composable () -> Unit,
    text: String
) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .width(contentWidth)
            .height(30.dp),
        shape = RoundedCornerShape(15.dp),
        colors = ButtonDefaults.buttonColors(containerColor = containerColor)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            icon()
            Spacer(modifier = Modifier.width(3.dp))
            Text(
                text = text,
                color = if (containerColor == BioGuardError) BioGuardOnSurface else BioGuardBackground,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
        }
    }
}
