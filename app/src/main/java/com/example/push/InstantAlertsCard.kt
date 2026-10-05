package com.example.push

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.data.service.OfficialLocationContextManager
import com.example.ui.theme.BentoBorder
import com.example.ui.theme.BentoCardBg
import com.example.ui.theme.BentoTextPrimary
import com.example.ui.theme.BentoTextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Opt-in switch for instant alerts (push). Off by default. Disabled with a plain reason when this build has no push configuration. */
@Composable
fun InstantAlertsCard() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val configured = remember { PushAlerts.isConfigured(context) }
    var on by remember { mutableStateOf(PushAlerts.isEnabled(context)) }
    var status by remember { mutableStateOf(PushAlerts.status(context)) }

    fun apply(want: Boolean) {
        scope.launch {
            val loc = withContext(Dispatchers.IO) { OfficialLocationContextManager(context).refreshLocation() }
            withContext(Dispatchers.IO) { PushAlerts.setEnabled(context, want, loc.state, loc.district) }
            on = PushAlerts.isEnabled(context)
            status = PushAlerts.status(context)
        }
    }

    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) apply(true) else status = "Unavailable: notifications are not allowed, so alerts could not be shown."
    }

    Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(BentoCardBg)
            .border(1.dp, BentoBorder, RoundedCornerShape(24.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("Instant alerts", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = BentoTextPrimary)
        Text(
            "When on, Hub works out your state and district on this phone and joins a public Firebase Cloud Messaging alert group for that area. Our server never receives your place, location or an ID; Google's messaging service sees this app's install token and the group names. An alert is spoken aloud and repeated every 20 seconds until you tap Stop. Delivery depends on Android, your network and the phone maker's battery settings, and may be late or missing. Alerts are not official unless they say so. Turned off, Hub keeps its normal checks about every 30 minutes.",
            fontSize = 12.sp, color = BentoTextSecondary
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(if (configured) "Receive instant alerts" else "Unavailable: instant alerts are not set up in this build.", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = BentoTextPrimary, modifier = Modifier.padding(end = 8.dp))
            Switch(checked = on, enabled = configured, onCheckedChange = { want ->
                if (!want) apply(false)
                else if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else apply(true)
            })
        }
        status?.let { Text(it, fontSize = 12.sp, color = BentoTextPrimary) }
    }
}
