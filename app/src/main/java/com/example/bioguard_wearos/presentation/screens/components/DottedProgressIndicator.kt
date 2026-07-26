package com.example.bioguard_wearos.presentation.screens.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.wear.compose.material3.LocalContentColor
import androidx.wear.compose.ui.tooling.preview.WearPreviewDevices
import androidx.wear.compose.ui.tooling.preview.WearPreviewFontScales
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

@Composable
fun DottedProgressIndicator(
    modifier: Modifier = Modifier,
    dotCount: Int = 10,
    dotRadiusFraction: Float = 0.1f,
    ringRadiusFraction: Float = 0.4f,
    color: Color = LocalContentColor.current
) {
    val infiniteTransition = rememberInfiniteTransition(label = "dots")
    val animationProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "progress"
    )

    Canvas(modifier = modifier) {
        val canvasW = size.width
        val canvasH = size.height
        val centerX = canvasW / 2f
        val centerY = canvasH / 2f
        val ringR = minOf(canvasW, canvasH) * ringRadiusFraction
        val dotR = minOf(canvasW, canvasH) * dotRadiusFraction

        for (i in 0 until dotCount) {
            val phase = i.toFloat() / dotCount
            val raw = (animationProgress + phase) % 1f
            val alpha = if (raw < 0.5f) raw * 2f else (1f - raw) * 2f

            val angle = i.toFloat() / dotCount * 2f * PI.toFloat()
            val dx = centerX + ringR * cos(angle)
            val dy = centerY + ringR * sin(angle)

            drawCircle(
                color = color.copy(alpha = alpha.coerceIn(0.05f, 1f)),
                radius = dotR,
                center = Offset(dx, dy)
            )
        }
    }
}

@WearPreviewDevices
@WearPreviewFontScales
@Composable
private fun DottedProgressIndicatorPreview() {
    DottedProgressIndicator(
        modifier = Modifier,
        dotCount = 12,
        color = Color(0xFF2DD4BF)
    )
}

@Preview(
    device = "spec:width=454.0dp,height=454.0dp,dpi=326,isRound=true",
    showBackground = true,
    backgroundColor = 0xFF000000
)
@Composable
private fun DottedProgressIndicatorRoundPreview() {
    DottedProgressIndicator(
        modifier = Modifier,
        dotCount = 12,
        color = Color(0xFF2DD4BF)
    )
}