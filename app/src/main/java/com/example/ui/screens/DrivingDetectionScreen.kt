package com.example.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.sensor.DrivingState
import com.example.ui.theme.BentoBorder
import com.example.ui.theme.BentoCardBg
import com.example.ui.theme.BentoGreenVibrant
import com.example.ui.theme.BentoHeroCardBg
import com.example.ui.theme.BentoTextMuted
import com.example.ui.theme.BentoTextPrimary
import com.example.ui.theme.BentoTextSecondary

@Composable
fun DrivingDetectionScreen(onOpenSos: () -> Unit, modifier: Modifier = Modifier) {
    val driving by DrivingState.driving.collectAsStateWithLifecycle()
    val speed by DrivingState.speed.collectAsStateWithLifecycle()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .testTag("driving_detection_screen")
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = if (driving) BentoHeroCardBg else BentoCardBg),
            border = androidx.compose.foundation.BorderStroke(1.dp, BentoBorder),
            modifier = Modifier.fillMaxWidth().testTag("driving_hero")
        ) {
            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.DirectionsCar, contentDescription = null, tint = BentoGreenVibrant)
                Column(modifier = Modifier.padding(start = 12.dp)) {
                    Text(
                        text = if (driving) "Driving: ON" else "Driving: OFF",
                        fontSize = 17.sp, fontWeight = FontWeight.Bold, color = BentoTextPrimary
                    )
                    Text(
                        text = if (driving) "Vehicle movement detected" else "No vehicle movement detected",
                        fontSize = 12.sp, color = BentoTextSecondary
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Card(
            colors = CardDefaults.cardColors(containerColor = BentoCardBg),
            border = androidx.compose.foundation.BorderStroke(1.dp, BentoBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Live GPS", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = BentoTextPrimary)
                drivingStatusLines(driving, speed, System.currentTimeMillis()).forEach { line ->
                    Text(text = line, fontSize = 12.sp, color = BentoTextSecondary, modifier = Modifier.padding(top = 4.dp))
                }
                if (speed.speedAt == 0L) {
                    Text(text = "Accuracy: Unavailable (no GPS fix yet)", fontSize = 12.sp, color = BentoTextMuted, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        // Existing full driving controls and log (announce toggle, test, trip log) - real data only.
        DrivingStatusCard(onOpenSos = onOpenSos)
    }
}
