package com.example.bioguard_wearos.presentation.screens.splash

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.wear.compose.ui.tooling.preview.WearPreviewDevices
import androidx.wear.compose.ui.tooling.preview.WearPreviewFontScales
import com.example.bioguard_wearos.R
import com.example.bioguard_wearos.presentation.theme.BioGuardTealBase
import com.example.bioguard_wearos.presentation.theme.BioGuardTealDark
import com.example.bioguard_wearos.presentation.theme.BioGuardTealGlow
import com.example.bioguard_wearos.presentation.theme.BioGuardTealLight
import com.example.bioguard_wearos.presentation.theme.BioGuardProgressTrack
import com.example.bioguard_wearos.presentation.theme.BioGuardProgressFill
import com.example.bioguard_wearos.presentation.theme.BioGuard_WearOsTheme
import kotlinx.coroutines.delay
import kotlin.math.min

@Composable
fun SplashScreen(onTimeout: () -> Unit) {
    LaunchedEffect(Unit) {
        delay(3000L)
        onTimeout()
    }

    val infiniteTransition = rememberInfiniteTransition(label = "splash")

    val ripplePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ripplePhase"
    )

    val bgBrightness by infiniteTransition.animateFloat(
        initialValue = 0.65f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bgBrightness"
    )

    val sweepAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "sweepAngle"
    )

    val trailLength by infiniteTransition.animateFloat(
        initialValue = 60f,
        targetValue = 120f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "trailLength"
    )

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        val screenDp = minOf(maxWidth, maxHeight)
        val logoSize = 220.dp
        val progressRingSize = 252.dp
        val strokeDp = screenDp * 0.012f

        Canvas(modifier = Modifier.fillMaxSize()) {
            val canvasW = size.width
            val canvasH = size.height
            val centerX = canvasW / 2f
            val centerY = canvasH / 2f
            val maxRadius = min(canvasW, canvasH) / 2f

            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(
                        BioGuardTealBase.copy(alpha = bgBrightness),
                        BioGuardTealDark.copy(alpha = 0.9f),
                        Color.Black
                    ),
                    center = Offset(centerX, centerY),
                    radius = maxRadius * 0.85f
                )
            )

            for (i in 0 until 4) {
                val phase = (ripplePhase + i * 0.25f) % 1f
                val rippleRadius = phase * maxRadius * 1.1f
                val rippleAlpha = ((1f - phase) * 0.35f * bgBrightness).coerceIn(0f, 1f)

                if (rippleRadius > 1f) {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                BioGuardTealGlow.copy(alpha = rippleAlpha),
                                BioGuardTealLight.copy(alpha = rippleAlpha * 0.4f),
                                Color.Transparent
                            ),
                            center = Offset(centerX, centerY),
                            radius = rippleRadius
                        ),
                        radius = rippleRadius * 0.55f,
                        center = Offset(centerX, centerY)
                    )
                }
            }

            for (ring in 1..10) {
                val ringRadius = maxRadius * ring / 10f
                drawCircle(
                    color = Color.White.copy(alpha = 0.015f * bgBrightness),
                    radius = ringRadius,
                    center = Offset(centerX, centerY),
                    style = Stroke(width = 0.8f)
                )
            }

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        BioGuardTealGlow.copy(alpha = 0.08f * bgBrightness),
                        Color.Transparent
                    ),
                    center = Offset(centerX, centerY),
                    radius = maxRadius * 0.35f
                ),
                radius = maxRadius * 0.35f,
                center = Offset(centerX, centerY)
            )
        }

        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(progressRingSize)
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val strokeWidth = strokeDp.toPx()
                val diameter = min(size.width, size.height)
                val radius = diameter / 2f - strokeWidth / 2f
                val center = Offset(size.width / 2f, size.height / 2f)

                drawCircle(
                    color = BioGuardProgressTrack,
                    radius = radius,
                    center = center,
                    style = Stroke(width = strokeWidth)
                )

                val activeArc = trailLength.coerceAtMost(340f)
                drawArc(
                    color = BioGuardProgressFill,
                    startAngle = sweepAngle - 90f,
                    sweepAngle = activeArc,
                    useCenter = false,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                    topLeft = Offset(center.x - radius, center.y - radius),
                    size = Size(radius * 2f, radius * 2f)
                )
            }

            Image(
                painter = painterResource(id = R.drawable.bioguard),
                contentDescription = "BioGuard Logo",
                modifier = Modifier.size(logoSize)
            )
        }
    }
}

@WearPreviewDevices
@WearPreviewFontScales
@Composable
private fun SplashScreenPreview() {
    BioGuard_WearOsTheme {
        SplashScreen(onTimeout = {})
    }
}

@Preview(
    device = "spec:width=454.0dp,height=454.0dp,dpi=326,isRound=true",
    showBackground = true,
    backgroundColor = 0xFF000000
)
@Composable
private fun SplashScreenRoundPreview() {
    BioGuard_WearOsTheme {
        SplashScreen(onTimeout = {})
    }
}