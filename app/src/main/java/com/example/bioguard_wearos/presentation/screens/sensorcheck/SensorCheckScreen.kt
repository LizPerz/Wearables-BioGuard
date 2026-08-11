package com.example.bioguard_wearos.presentation.screens.sensorcheck

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.Text
import com.example.bioguard_wearos.R
import com.example.bioguard_wearos.presentation.screens.components.DottedProgressIndicator
import com.example.bioguard_wearos.presentation.theme.BioGuardTealBase
import com.example.bioguard_wearos.presentation.theme.BioGuardBackground
import com.example.bioguard_wearos.presentation.theme.BioGuardOnSurface
import com.example.bioguard_wearos.presentation.theme.BioGuardOnSurfaceVariant
import com.example.bioguard_wearos.presentation.theme.BioGuardPrimary
import com.example.bioguard_wearos.presentation.theme.BioGuard_WearOsTheme
import androidx.compose.ui.tooling.preview.Preview
import androidx.wear.compose.ui.tooling.preview.WearPreviewDevices
import androidx.wear.compose.ui.tooling.preview.WearPreviewFontScales
import kotlinx.coroutines.delay

@Composable
fun SensorCheckScreen(onComplete: () -> Unit) {
    LaunchedEffect(Unit) {
        delay(3000L)
        onComplete()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        BioGuardTealBase,
                        BioGuardBackground
                    ),
                    radius = 600f
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 24.dp)
        ) {
            Image(
                painter = painterResource(id = R.drawable.bioguard),
                contentDescription = "BioGuard Logo",
                modifier = Modifier.size(80.dp)
            )
            Spacer(modifier = Modifier.height(6.dp))
            DottedProgressIndicator(
                modifier = Modifier.size(36.dp),
                dotCount = 10,
                color = BioGuardPrimary
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Verificando\nsensores...",
                color = BioGuardOnSurface,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                lineHeight = 16.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Mantenga el reloj\nen su mu\u00f1eca",
                color = BioGuardOnSurfaceVariant,
                fontSize = 9.sp,
                textAlign = TextAlign.Center,
                lineHeight = 13.sp
            )
        }
    }
}

@WearPreviewDevices
@WearPreviewFontScales
@Composable
private fun SensorCheckScreenPreview() {
    BioGuard_WearOsTheme {
        SensorCheckScreen(onComplete = {})
    }
}

@Preview(
    device = "spec:width=454.0dp,height=454.0dp,dpi=326,isRound=true",
    showBackground = true,
    backgroundColor = 0xFF000000
)
@Composable
private fun SensorCheckScreenRoundPreview() {
    BioGuard_WearOsTheme {
        SensorCheckScreen(onComplete = {})
    }
}
