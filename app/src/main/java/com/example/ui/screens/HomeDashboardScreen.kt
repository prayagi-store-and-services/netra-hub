package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Waves
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.engine.IntelligentSafetyStatusEngine
import com.example.data.model.SensorFusionState
import com.example.ui.theme.BentoBorder
import com.example.ui.theme.BentoCardBg
import com.example.ui.theme.BentoGreenVibrant
import com.example.ui.theme.BentoHeroCardBg
import com.example.ui.theme.BentoRed
import com.example.ui.theme.BentoTextMuted
import com.example.ui.theme.BentoTextPrimary
import com.example.ui.theme.BentoTextSecondary
import java.util.Calendar

/** Time-of-day greeting for the Home header. Pure so it can be unit tested. */
fun greetingForHour(hour: Int): String = when (hour) {
    in 5..11 -> "Good Morning"
    in 12..16 -> "Good Afternoon"
    in 17..20 -> "Good Evening"
    else -> "Good Night"
}

/** Hero line from the real aggregated safety status. Pure so it can be unit tested. */
fun heroStatusLine(status: IntelligentSafetyStatusEngine.SystemSafetyStatus): Pair<String, Boolean> = when (status) {
    IntelligentSafetyStatusEngine.SystemSafetyStatus.CRITICAL -> "Attention needed" to false
    IntelligentSafetyStatusEngine.SystemSafetyStatus.WARNING -> "Monitoring with warnings" to true
    IntelligentSafetyStatusEngine.SystemSafetyStatus.NORMAL -> "All Systems Normal" to true
}

data class HomeDestinations(
    val onDriving: () -> Unit,
    val onWeather: () -> Unit,
    val onEarthquake: () -> Unit,
    val onSos: () -> Unit,
    val onDeviceSafety: () -> Unit,
    val onSystemHealth: () -> Unit,
    val onSensors: () -> Unit,
    val onGraph: () -> Unit,
    val onService: () -> Unit,
    val onStatus: () -> Unit
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeDashboardScreen(
    fusionState: SensorFusionState,
    viewModel: com.example.ui.MainViewModel,
    destinations: HomeDestinations,
    modifier: Modifier = Modifier,
    headerContent: @Composable () -> Unit = {}
) {
    val systemStatus by viewModel.systemSafetyStatus.collectAsStateWithLifecycle()
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    val (heroLine, heroOk) = heroStatusLine(systemStatus)

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .testTag("home_dashboard")
    ) {
        headerContent()
        Text(text = greetingForHour(hour), fontSize = 22.sp, fontWeight = FontWeight.Bold, color = BentoTextPrimary)
        Text(text = "Your safety companion is active", fontSize = 13.sp, color = BentoTextSecondary)
        Spacer(modifier = Modifier.height(12.dp))

        Card(
            colors = CardDefaults.cardColors(containerColor = if (heroOk) BentoHeroCardBg else BentoCardBg),
            modifier = Modifier.fillMaxWidth().testTag("home_hero_status")
        ) {
            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (heroOk) Icons.Default.CheckCircle else Icons.Default.Warning,
                    contentDescription = null,
                    tint = if (heroOk) BentoGreenVibrant else BentoRed
                )
                Column(modifier = Modifier.padding(start = 12.dp)) {
                    Text(text = heroLine, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = BentoTextPrimary)
                    Text(
                        text = if (heroOk) "Monitoring for your safety" else "Open Alerts for the active issue",
                        fontSize = 12.sp,
                        color = BentoTextSecondary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        val battery = fusionState.batteryLevelPercent
        val temp = fusionState.batteryTempC
        GridCard("Driving Detection", Icons.Default.DirectionsCar, null, destinations.onDriving, "home_card_driving")
        Spacer(modifier = Modifier.height(8.dp))
        GridCard("Weather Intelligence", Icons.Default.Cloud, null, destinations.onWeather, "home_card_weather")
        Spacer(modifier = Modifier.height(8.dp))
        GridCard("Earthquake Network", Icons.Default.Waves, null, destinations.onEarthquake, "home_card_earthquake")
        Spacer(modifier = Modifier.height(8.dp))
        GridCard("SOS & Emergency", Icons.Default.Warning, null, destinations.onSos, "home_card_sos")
        Spacer(modifier = Modifier.height(8.dp))
        GridCard(
            "Device Safety (Thermal & Battery)", Icons.Default.PhoneAndroid,
            "Battery $battery% - ${"%.0f".format(temp)}°C",
            destinations.onDeviceSafety, "home_card_device_safety"
        )
        Spacer(modifier = Modifier.height(8.dp))
        GridCard("System Health & Audit", Icons.Default.HealthAndSafety, null, destinations.onSystemHealth, "home_card_system_health")

        Spacer(modifier = Modifier.height(16.dp))
        Text(text = "Advanced tools", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = BentoTextSecondary)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = destinations.onSensors, modifier = Modifier.testTag("home_adv_sensors")) {
                Icon(Icons.Default.Memory, contentDescription = null, tint = BentoTextMuted)
                Text("Sensors", color = BentoTextSecondary, fontSize = 12.sp)
            }
            TextButton(onClick = destinations.onGraph, modifier = Modifier.testTag("home_adv_graph")) {
                Icon(Icons.Default.Hub, contentDescription = null, tint = BentoTextMuted)
                Text("Graph", color = BentoTextSecondary, fontSize = 12.sp)
            }
            TextButton(onClick = destinations.onService, modifier = Modifier.testTag("home_adv_service")) {
                Icon(Icons.Default.Hub, contentDescription = null, tint = BentoTextMuted)
                Text("Service", color = BentoTextSecondary, fontSize = 12.sp)
            }
            TextButton(onClick = destinations.onStatus, modifier = Modifier.testTag("home_adv_status")) {
                Icon(Icons.Default.HealthAndSafety, contentDescription = null, tint = BentoTextMuted)
                Text("Status", color = BentoTextSecondary, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun GridCard(title: String, icon: ImageVector, subtitle: String?, onClick: () -> Unit, tag: String) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = BentoCardBg),
        border = androidx.compose.foundation.BorderStroke(1.dp, BentoBorder),
        modifier = Modifier.fillMaxWidth().testTag(tag)
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column(modifier = Modifier.padding(start = 12.dp)) {
                Text(text = title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = BentoTextPrimary)
                if (subtitle != null) Text(text = subtitle, fontSize = 12.sp, color = BentoTextSecondary)
            }
        }
    }
}
