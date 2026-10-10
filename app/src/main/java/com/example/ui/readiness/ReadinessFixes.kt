package com.example.ui.readiness

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Intents for the one-tap readiness fixes. Each opens a real Android screen; the app never changes these itself. */
object ReadinessFixes {

    fun batteryExemptionIntent(context: Context): Intent =
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + context.packageName))

    fun exactAlarmIntent(context: Context): Intent =
        Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:" + context.packageName))

    fun appDetailsIntent(context: Context): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + context.packageName))

    /** Known autostart / background-run screens per maker (realme and Oppo share ColorOS). Tried in order. */
    fun autostartComponents(manufacturer: String): List<ComponentName> {
        val m = manufacturer.lowercase()
        return when {
            m.contains("realme") || m.contains("oppo") || m.contains("oneplus") -> listOf(
                ComponentName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"),
                ComponentName("com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity"),
                ComponentName("com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity"),
                ComponentName("com.coloros.oppoguardelf", "com.coloros.powermanager.fuelgaue.PowerUsageModelActivity"),
                ComponentName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.FakeActivity")
            )
            m.contains("xiaomi") || m.contains("redmi") || m.contains("poco") -> listOf(
                ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")
            )
            m.contains("vivo") -> listOf(
                ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"),
                ComponentName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity")
            )
            m.contains("huawei") || m.contains("honor") -> listOf(
                ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
                ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity")
            )
            else -> emptyList()
        }
    }

    /**
     * Opens the maker's autostart screen if this phone has one, else the app's own settings page.
     * Returns true when a maker screen opened, false when the generic app page was used.
     */
    fun openAutostart(context: Context): Boolean {
        for (c in autostartComponents(Build.MANUFACTURER.orEmpty())) {
            try {
                context.startActivity(Intent().setComponent(c).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return true
            } catch (e: ActivityNotFoundException) {
            } catch (e: SecurityException) {
            }
        }
        try {
            context.startActivity(appDetailsIntent(context).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: ActivityNotFoundException) {
        }
        return false
    }

    fun launchSafely(context: Context, intent: Intent, fallback: Intent = appDetailsIntent(context)) {
        try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            try {
                context.startActivity(fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (e2: Exception) {
            }
        }
    }
}

/** One-tap fix buttons, shown only for items that are still not OK. Each has a plain-words reason. */
@Composable
fun ReadinessFixButtons(
    batteryOk: Boolean,
    alarmsOk: Boolean,
    notificationsOk: Boolean,
    showAutostart: Boolean,
    manufacturer: String,
    onRecheck: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { onRecheck() }
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (!batteryOk) {
            FixRow(
                "Allow unrestricted battery",
                "Without this, Android can stop the safety sensors when the screen is off. Opens the system pop-up: tap Allow."
            ) { ReadinessFixes.launchSafely(context, ReadinessFixes.batteryExemptionIntent(context)) }
        }
        if (!alarmsOk && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            FixRow(
                "Allow exact alarms",
                "Lets timed safety checks and reminders fire on time. Opens the Alarms & reminders screen: turn it on for this app."
            ) { ReadinessFixes.launchSafely(context, ReadinessFixes.exactAlarmIntent(context)) }
        }
        if (!notificationsOk) {
            FixRow(
                "Allow notifications",
                "So safety alerts can reach you. If Android no longer shows the pop-up, the app's settings page opens instead."
            ) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    // If the pop-up was already refused twice Android shows nothing; the page below is the way out.
                } else {
                    ReadinessFixes.launchSafely(
                        context,
                        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    )
                }
            }
            FixRow(
                "Open notification settings",
                "Use this if the pop-up above does nothing."
            ) {
                ReadinessFixes.launchSafely(
                    context,
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                )
            }
        }
        if (showAutostart) {
            FixRow(
                "Open Autostart settings ($manufacturer)",
                "Your phone's maker adds its own background rules. Turn Autostart on for ${com.example.Brand.name} and set Battery to Unrestricted. Android gives apps no way to see this switch, so it cannot be checked here."
            ) { ReadinessFixes.openAutostart(context) }
        }
    }
}

@Composable
private fun FixRow(label: String, why: String, onClick: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) { Text(label, fontSize = 12.sp) }
        Text(why, fontSize = 10.sp, lineHeight = 13.sp)
    }
}

/** Settings switch for the speed gate (on by default): background safety sensors listen only at 30 km/h or more. */
@Composable
fun SpeedGateCard(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var enabled by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(com.example.data.sensor.SpeedGate.isEnabled(context)) }
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        androidx.compose.foundation.layout.Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Safety sensors only above 30 km/h", fontSize = 13.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
            androidx.compose.material3.Switch(
                checked = enabled,
                onCheckedChange = { enabled = it; com.example.data.sensor.SpeedGate.setEnabled(context, it) }
            )
        }
        Text(
            "When on, the background sensors listen only while you travel at 30 km/h or faster, so walking does not keep them running and drain the battery. " +
                "They switch off 30 seconds after your speed drops below 30. If the phone cannot give a speed, they stay off. Screens you open yourself still show live data.",
            fontSize = 11.sp,
            lineHeight = 14.sp
        )
    }
}
