package com.example.ui.screens

import android.app.AlarmManager
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.os.Process
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.ui.theme.BentoBorder
import com.example.ui.theme.BentoCardBg
import com.example.ui.theme.BentoGreenPrimary
import com.example.ui.theme.BentoRed
import com.example.ui.theme.BentoTextPrimary
import com.example.ui.theme.BentoTextSecondary

/** One permission row: name, plain reason, live status read from Android, and the Android page a tap opens. */
private class HubPerm(
    val name: String,
    val reason: String,
    val status: (Context) -> String,
    val open: ((Context) -> Unit)?
)

private fun appDetails(c: Context) {
    c.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + c.packageName)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

private fun tryOpen(c: Context, i: Intent) {
    try { c.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (e: Exception) { appDetails(c) }
}

private fun runtime(c: Context, p: String): String = try {
    if (c.checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED) "Allowed" else "Not allowed"
} catch (e: Exception) { "Unavailable" }

private fun runtimeFrom(sdk: Int, c: Context, p: String): String =
    if (Build.VERSION.SDK_INT >= sdk) runtime(c, p) else "Not needed on this Android version"

private fun usageStatus(c: Context): String = try {
    val ops = c.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
    @Suppress("DEPRECATION")
    val m = ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), c.packageName)
    if (m == AppOpsManager.MODE_ALLOWED) "Allowed" else "Not allowed"
} catch (e: Exception) { "Unavailable" }

private fun alarmStatus(c: Context): String = try {
    if (Build.VERSION.SDK_INT < 31) "Not needed on this Android version"
    else if ((c.getSystemService(Context.ALARM_SERVICE) as AlarmManager).canScheduleExactAlarms()) "Allowed" else "Not allowed"
} catch (e: Exception) { "Unavailable" }

private fun batteryStatus(c: Context): String = try {
    if ((c.getSystemService(Context.POWER_SERVICE) as PowerManager).isIgnoringBatteryOptimizations(c.packageName)) "Allowed (unrestricted)" else "Not allowed (Android may limit background work)"
} catch (e: Exception) { "Unavailable" }

private fun installStatus(c: Context): String = try {
    if (Build.VERSION.SDK_INT < 26) "Not needed on this Android version"
    else if (c.packageManager.canRequestPackageInstalls()) "Allowed" else "Not allowed"
} catch (e: Exception) { "Unavailable" }

private fun hubPermissions(): List<HubPerm> = listOf(
    HubPerm("Location", "Used for weather and temperature sync, travel and SOS location. If you deny it, only these features are limited; sensors keep working.",
        { runtime(it, android.Manifest.permission.ACCESS_FINE_LOCATION) }, { appDetails(it) }),
    HubPerm("Send SMS", "Used by the SOS flow to text your chosen contacts. Nothing is sent unless SOS runs.",
        { runtime(it, android.Manifest.permission.SEND_SMS) }, { appDetails(it) }),
    HubPerm("Camera", "Used by Travel Checking to inspect for hidden cameras. No photo is stored or uploaded.",
        { runtime(it, android.Manifest.permission.CAMERA) }, { appDetails(it) }),
    HubPerm("Physical activity", "Used to count your steps from the phone's step sensor.",
        { runtimeFrom(29, it, android.Manifest.permission.ACTIVITY_RECOGNITION) }, { appDetails(it) }),
    HubPerm("Notifications", "Used to show safety alerts and the running-service notice.",
        { runtimeFrom(33, it, android.Manifest.permission.POST_NOTIFICATIONS) }, { appDetails(it) }),
    HubPerm("Nearby devices (Bluetooth)", "Used to read Bluetooth state for the security checks. No device is paired or connected by Hub.",
        { runtimeFrom(31, it, android.Manifest.permission.BLUETOOTH_CONNECT) }, { appDetails(it) }),
    HubPerm("Usage access", "Used by Digital Wellness to read how long apps are used on this phone. Nothing leaves the phone.",
        { usageStatus(it) }, { tryOpen(it, Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }),
    HubPerm("Exact alarms", "Android lets apps set exact-time alarms with this. Hub currently only checks whether it is allowed.",
        { alarmStatus(it) }, { if (Build.VERSION.SDK_INT >= 31) tryOpen(it, Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:" + it.packageName))) else appDetails(it) }),
    HubPerm("Battery optimisation", "Lets the safety service keep running in the background. Tap to change it on the Android page.",
        { batteryStatus(it) }, { tryOpen(it, Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }),
    HubPerm("Install apps", "Used only when you tap Install on an update, so Android can install the new Hub file.",
        { installStatus(it) }, { if (Build.VERSION.SDK_INT >= 26) tryOpen(it, Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + it.packageName))) else appDetails(it) }),
    HubPerm("Internet and network state", "Used to check for updates and weather. Always allowed by Android (normal permission).", { "Always allowed" }, null),
    HubPerm("Run at start, keep awake, vibrate, foreground service", "Used so safety monitoring can restart after a reboot, run while the screen is off and vibrate on alerts. Normal permissions, always allowed.", { "Always allowed" }, null)
)

/** Settings card listing every permission Hub declares. Status is re-read each time the screen resumes; no timer, no background work. */
@Composable
fun HubPermissionsCard() {
    val context = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    DisposableEffect(context) {
        val lc = (context as? ComponentActivity)?.lifecycle
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) tick++ }
        lc?.addObserver(obs)
        onDispose { lc?.removeObserver(obs) }
    }
    Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(BentoCardBg)
            .border(1.dp, BentoBorder, RoundedCornerShape(24.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Permissions", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = BentoTextPrimary)
        Text("What Hub uses and why. Tap a row to open its Android page.", fontSize = 12.sp, color = BentoTextSecondary)
        hubPermissions().forEach { p ->
            val st = remember(tick) { p.status(context) }
            val bad = st.startsWith("Not allowed")
            val m = if (p.open != null) Modifier.fillMaxWidth().clickable { p.open.invoke(context) } else Modifier.fillMaxWidth()
            Column(m, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(p.name + "  -  " + st, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = if (bad) BentoRed else BentoGreenPrimary)
                Text(p.reason, fontSize = 12.sp, color = BentoTextSecondary)
            }
        }
    }
}
