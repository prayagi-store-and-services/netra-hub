package com.example.update

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Settings card: shows the installed version and a "Check for update" button that tells whether this is the latest version. */
@Composable
fun UpdateCheckCard(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var newer by remember { mutableStateOf<AppRelease?>(null) }
    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("App version", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            Text("Installed: " + AppUpdater.installedVersionName(context), fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedButton(enabled = !busy, onClick = {
                scope.launch {
                    busy = true
                    status = null
                    newer = null
                    when (val r = withContext(Dispatchers.IO) { AppUpdater.checkNow(context) }) {
                        is AppUpdater.CheckResult.Latest -> status = "You are on the latest version."
                        is AppUpdater.CheckResult.Newer -> { newer = r.release; status = "Update available: " + r.release.versionName }
                        is AppUpdater.CheckResult.Failed -> status = "Unavailable: could not check. Check your internet connection and try again."
                    }
                    busy = false
                }
            }) { Text(if (busy) "Checking..." else "Check for update") }
            status?.let { Text(it, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface) }
            newer?.let { r ->
                Button(enabled = !busy, onClick = {
                    scope.launch {
                        busy = true
                        try {
                            val file = withContext(Dispatchers.IO) { AppUpdater.download(context, r) }
                            AppUpdater.install(context, file)
                        } catch (e: Exception) {
                            status = plainFailure(e, "Update failed.")
                        }
                        busy = false
                    }
                }) { Text(if (busy) "Downloading..." else "Update to " + r.versionName) }
            }
        }
    }
}
