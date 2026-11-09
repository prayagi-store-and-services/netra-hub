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
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.DeviceThermostat
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.SensorFusionState
import com.example.ui.theme.BentoAmber
import com.example.ui.theme.BentoBorder
import com.example.ui.theme.BentoCardBg
import com.example.ui.theme.BentoGreenVibrant
import com.example.ui.theme.BentoTextMuted
import com.example.ui.theme.BentoTextPrimary
import com.example.ui.theme.BentoTextSecondary

/** Temperature verdict against the user's own threshold. Pure so it can be unit tested. */
fun thermalVerdict(tempC: Float, thresholdC: Int): String = when {
    tempC <= 0f -> "Unavailable"
    tempC >= thresholdC -> "Above your ${thresholdC}°C alert level"
    else -> "Normal"
}

@Composable
fun DeviceSafetyScreen(
    fusionState: SensorFusionState,
    thermalThresholdC: Int,
    sensorsWorking: Int,
    sensorsTotal: Int,
    modifier: Modifier = Modifier
) {
    val battery = fusionState.batteryLevelPercent
    val temp = fusionState.batteryTempC
    val verdict = thermalVerdict(temp, thermalThresholdC)

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .testTag("device_safety_screen")
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Card(
                colors = CardDefaults.cardColors(containerColor = BentoCardBg),
                border = androidx.compose.foundation.BorderStroke(1.dp, BentoBorder),
                modifier = Modifier.weight(1f).padding(end = 4.dp).testTag("device_battery_card")
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Icon(Icons.Default.BatteryFull, contentDescription = null, tint = BentoGreenVibrant)
                    Text("Battery", fontSize = 13.sp, color = BentoTextSecondary, modifier = Modifier.padding(top = 8.dp))
                    Text("$battery%", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = BentoTextPrimary)
                    Text(
                        if (fusionState.isCharging) "Charging" else "On battery",
                        fontSize = 12.sp, color = BentoTextSecondary
                    )
                }
            }
            Card(
                colors = CardDefaults.cardColors(containerColor = BentoCardBg),
                border = androidx.compose.foundation.BorderStroke(1.dp, BentoBorder),
                modifier = Modifier.weight(1f).padding(start = 4.dp).testTag("device_temp_card")
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Icon(Icons.Default.DeviceThermostat, contentDescription = null, tint = if (verdict == "Normal") BentoGreenVibrant else BentoAmber)
                    Text("Temperature", fontSize = 13.sp, color = BentoTextSecondary, modifier = Modifier.padding(top = 8.dp))
                    Text(
                        text = if (temp > 0f) "${"%.0f".format(temp)}°C" else "Unavailable",
                        fontSize = 22.sp, fontWeight = FontWeight.Bold, color = BentoTextPrimary
                    )
                    Text(verdict, fontSize = 12.sp, color = BentoTextSecondary)
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Card(
            colors = CardDefaults.cardColors(containerColor = BentoCardBg),
            border = androidx.compose.foundation.BorderStroke(1.dp, BentoBorder),
            modifier = Modifier.fillMaxWidth().testTag("device_health_card")
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("System Health", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = BentoTextPrimary)
                Row(modifier = Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Sensors", fontSize = 12.sp, color = BentoTextSecondary)
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = if (sensorsTotal > 0) "$sensorsWorking of $sensorsTotal reporting" else "Unavailable",
                        fontSize = 12.sp, color = BentoTextMuted
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Battery temperature is the battery sensor reading, not the phone's internal temperature.",
            fontSize = 11.sp, color = BentoTextMuted
        )
    }
}
