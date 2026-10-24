package com.example.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.monitoring.MonitoringControl
import com.example.monitoring.MonitoringPreference

@Composable
fun MonitoringControlCard() {
    val context = LocalContext.current
    var enabled by remember { mutableStateOf(MonitoringPreference.isEnabled(context)) }
    var confirming by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    MonitoringControlContent(enabled, error) {
        if (enabled) confirming = true
        else {
            val success = MonitoringControl.resume(context)
            enabled = MonitoringPreference.isEnabled(context)
            error = if (success) null else "Could not resume. Check the monitoring status before trying again."
        }
    }
    if (confirming) AlertDialog(
        onDismissRequest = { confirming = false },
        title = { Text("Pause sensor monitoring?") },
        text = { Text("Driving detection and automatic sensor alerts will stop until you tap Resume. Separately configured SOS shake and official-alert checks are not switched off by this control.") },
        confirmButton = {
            TextButton(onClick = {
                val success = MonitoringControl.pause(context)
                enabled = MonitoringPreference.isEnabled(context)
                error = if (success) null else "Could not complete pause. Check status and close the app if monitoring continues."
                confirming = false
            }) { Text("Pause monitoring") }
        },
        dismissButton = { TextButton(onClick = { confirming = false }) { Text("Cancel") } }
    )
}

@Composable
internal fun MonitoringControlContent(enabled: Boolean, error: String? = null, onAction: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
        Column(Modifier.fillMaxWidth().padding(12.dp)) {
            Text(if (enabled) "Sensor monitoring: enabled by you" else "Sensor monitoring: paused",
                style = MaterialTheme.typography.titleSmall, modifier = Modifier.testTag("monitoring_state"))
            Text(if (enabled) "Enabled is your setting, not proof the service is running. Pause stops driving detection and automatic sensor alerts."
                 else "Stays paused after reopening, charging or restarting the phone. Resume only when you choose.",
                style = MaterialTheme.typography.bodySmall)
            Text("SOS shake and official-alert checks are separate.", style = MaterialTheme.typography.bodySmall)
            if (error != null) Text(error, color = MaterialTheme.colorScheme.error)
            OutlinedButton(onClick = onAction, modifier = Modifier.testTag("monitoring_action")) {
                Text(if (enabled) "Pause monitoring" else "Resume monitoring")
            }
        }
    }
}
