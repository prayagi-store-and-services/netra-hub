package com.example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.data.engine.QuakeAlertSettings

@Composable
fun QuakeFilterControls(settings: QuakeAlertSettings, onChange: (QuakeAlertSettings) -> Unit, editable: Boolean = true) {
    var distanceDialog by remember { mutableStateOf(false) }
    var magnitudeDialog by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Earthquake notifications", Modifier.weight(1f).padding(end = 8.dp))
            Switch(checked = settings.enabled, enabled = editable, onCheckedChange = { onChange(settings.copy(enabled = it)) })
        }
        OutlinedButton(onClick = { distanceDialog = true }, enabled = editable, modifier = Modifier.fillMaxWidth()) {
            Text("Maximum distance: ${settings.distanceKm} km")
        }
        OutlinedButton(onClick = { magnitudeDialog = true }, enabled = editable, modifier = Modifier.fillMaxWidth()) {
            Text("Minimum magnitude: ${settings.minimumMagnitude}")
        }
        Text("Saved only on this phone. Off stops automatic earthquake checks and notifications; manual checks remain available. Other official alerts are unchanged.", style = MaterialTheme.typography.bodySmall)
    }
    if (distanceDialog) AlertDialog(onDismissRequest = { distanceDialog = false }, title = { Text("Maximum distance") },
        text = { Column { QuakeAlertSettings.distances.forEach { km ->
            TextButton(onClick = { onChange(settings.copy(distanceKm = km)); distanceDialog = false }, modifier = Modifier.fillMaxWidth()) {
                Text((if (km == settings.distanceKm) "Selected: " else "") + "$km km")
            }
        } } }, confirmButton = { TextButton(onClick = { distanceDialog = false }) { Text("Cancel") } })
    if (magnitudeDialog) AlertDialog(onDismissRequest = { magnitudeDialog = false }, title = { Text("Minimum magnitude") },
        text = { Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState())) { QuakeAlertSettings.magnitudes.forEach { magnitude ->
            TextButton(onClick = { onChange(settings.copy(minimumMagnitude = magnitude)); magnitudeDialog = false }, modifier = Modifier.fillMaxWidth()) {
                Text((if (magnitude == settings.minimumMagnitude) "Selected: " else "") + magnitude)
            }
        } } }, confirmButton = { TextButton(onClick = { magnitudeDialog = false }) { Text("Cancel") } })
}
