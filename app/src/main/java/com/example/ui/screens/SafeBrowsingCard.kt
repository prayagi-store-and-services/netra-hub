package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.remember
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.BentoBorder
import com.example.ui.theme.BentoCardBg
import com.example.ui.theme.BentoTextPrimary
import com.example.ui.theme.BentoTextSecondary

const val SENSORGUARD_PACKAGE = "com.aistudio.sensorguard.prvsec"
const val SENSORGUARD_RELEASES = "https://github.com/prayagi-store-and-services/prayagi-Privacy-/releases/latest"

/**
 * Status card only. Blocking adult and gambling sites runs in SensorGuard (it holds the one local VPN); Hub has no VPN of its own.
 * Hub cannot read SensorGuard's on/off state, so it shows only whether SensorGuard is installed and opens it.
 */
@Composable
fun SafeBrowsingCard() {
    val context = LocalContext.current
    val launch: Intent? = remember(context) { runCatching { context.packageManager.getLaunchIntentForPackage(SENSORGUARD_PACKAGE) }.getOrNull() }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(BentoCardBg)
            .border(1.dp, BentoBorder, RoundedCornerShape(16.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("Safe browsing", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = BentoTextPrimary)
        Text(
            if (launch != null) "SensorGuard is installed. Turn on \"Block adult and gambling sites\" inside SensorGuard. Hub cannot see whether it is on."
            else "Blocking adult and gambling sites is done by SensorGuard (not installed on this phone). Hub has no VPN of its own.",
            fontSize = 12.sp, color = BentoTextSecondary
        )
        Text(
            "It blocks by website name only, using the BlockList Project and UT1 lists. A browser's own secure DNS, another VPN or an unlisted site can bypass it.",
            fontSize = 11.sp, color = BentoTextSecondary
        )
        Button(onClick = {
            val i = launch ?: Intent(Intent.ACTION_VIEW, Uri.parse(SENSORGUARD_RELEASES))
            runCatching { context.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        }) { Text(if (launch != null) "Open SensorGuard" else "Get SensorGuard") }
    }
}
