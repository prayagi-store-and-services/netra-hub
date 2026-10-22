package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.sensor.DrivingState
import com.example.driving.DrivingEvents
import com.example.driving.DrivingLog
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Plain-words driving status for the card. Pure so it can be unit tested. */
fun drivingStatusLines(driving: Boolean, speed: DrivingState.SpeedInfo, now: Long): List<String> {
    val stale = speed.speedAt == 0L || now - speed.speedAt > 60_000L
    val lines = mutableListOf(when {
        driving && stale -> "Driving mode: ON (GPS speed unknown right now, held until GPS returns)"
        driving -> "Driving mode: ON"
        stale -> "Driving mode: OFF (GPS speed unknown, waiting)"
        else -> "Driving mode: OFF"
    })
    lines += when {
        speed.speedAt == 0L -> "GPS speed: Unavailable (no speed received yet)"
        now - speed.speedAt > 60_000L -> "GPS speed: Unavailable now (last was %.0f km/h, %d s ago)".format(speed.kmh ?: 0f, (now - speed.speedAt) / 1000)
        else -> "GPS speed: %.0f km/h (%d s ago)".format(speed.kmh ?: 0f, (now - speed.speedAt) / 1000)
    }
    return lines
}

@Composable
fun DrivingStatusCard(onOpenSos: () -> Unit) {
    val context = LocalContext.current
    val driving by DrivingState.driving.collectAsStateWithLifecycle()
    val speed by DrivingState.speed.collectAsStateWithLifecycle()
    var announce by remember { mutableStateOf(DrivingEvents.announceEnabled(context)) }
    var showLog by remember { mutableStateOf(false) }
    var entries by remember { mutableStateOf(DrivingLog.list(context)) }
    var testMsg by remember { mutableStateOf("") }
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(driving, showLog) {
        while (true) { now = System.currentTimeMillis(); entries = DrivingLog.list(context); kotlinx.coroutines.delay(2000L) }
    }
    val fmt = remember { SimpleDateFormat("d MMM, hh:mm:ss a", Locale.getDefault()) }
    Card(modifier = Modifier.fillMaxWidth().testTag("driving_status_card")) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Driving detection", fontWeight = FontWeight.Bold)
            drivingStatusLines(driving, speed, now).forEach { Text(it, fontSize = 13.sp) }
            val live = com.example.driving.DrivingTrip.liveness()
            Text(when {
                !driving -> "Motion sensor: off until driving mode is on"
                !live.running -> "Motion sensor: not running"
                live.samples == 0L -> "Motion sensor: running, no samples yet"
                else -> "Motion sensor: %d samples, about %.0f a second, last %d s ago".format(live.samples, live.perSecond, (now - live.lastSampleAt) / 1000)
            } + (if (driving && live.lastFixAt > 0L) "; last GPS fix %d s ago".format((now - live.lastFixAt) / 1000) else ""), fontSize = 12.sp)
            Text("Driving mode starts at ${"%.0f".format(com.example.data.sensor.SpeedGate.DEFAULT_THRESHOLD_KMH)} km/h and ends after ${com.example.data.sensor.SpeedGate.DEFAULT_CLOSE_DELAY_MS / 1000} seconds below that. It needs location allowed all the time and battery optimisation off.", fontSize = 11.sp)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text("Speak when driving mode turns on or off", fontSize = 13.sp, modifier = Modifier.weight(1f))
                Switch(checked = announce, onCheckedChange = { announce = it; DrivingEvents.setAnnounceEnabled(context, it) }, modifier = Modifier.testTag("driving_announce_switch"))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { testMsg = DrivingEvents.test(context) }, modifier = Modifier.testTag("driving_test_button")) { Text("TEST announcement", fontSize = 12.sp) }
                OutlinedButton(onClick = { showLog = !showLog }, modifier = Modifier.testTag("driving_log_toggle")) { Text(if (showLog) "Hide log" else "Driving log (${entries.size})", fontSize = 12.sp) }
            }
            if (testMsg.isNotEmpty()) Text(testMsg, fontSize = 11.sp)
            Button(onClick = onOpenSos, modifier = Modifier.fillMaxWidth().testTag("open_sos_button")) { Text("Open SOS and crash alert settings") }
            if (showLog) {
                if (entries.isEmpty()) Text("Nothing recorded yet. Entries appear here when driving mode turns on or off, or when GPS gives no speed.", fontSize = 12.sp)
                entries.take(80).forEach { e ->
                    val what = when (e.kind) { DrivingLog.STARTED -> "Driving ON"; DrivingLog.ENDED -> "Driving OFF"; DrivingLog.SPEED -> "Speed change"; DrivingLog.BRAKE -> "Braking"; DrivingLog.BUMP -> "Bump"; else -> "Note" }
                    Text(fmt.format(Date(e.at)) + "  " + what + (e.speedKmH?.let { "  %.0f km/h".format(it) } ?: ""), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text(e.reason, fontSize = 11.sp)
                }
            }
        }
    }
}
