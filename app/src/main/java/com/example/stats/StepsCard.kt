package com.example.stats

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.util.Calendar
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val PREFS = "netra_steps"

private fun load(p: android.content.SharedPreferences): StepState? {
    val d = p.getString("day", null) ?: return null
    return StepState(d, p.getFloat("base", 0f), p.getFloat("carried", 0f), p.getFloat("last", 0f), p.getLong("since", 0L))
}

private fun save(p: android.content.SharedPreferences, s: StepState) {
    p.edit().putString("day", s.day).putFloat("base", s.base).putFloat("carried", s.carried).putFloat("last", s.lastRaw).putLong("since", s.sinceMs).apply()
}

/**
 * Home card: today's steps from the phone's own step counter. Real data only. The sensor is read only while this
 * screen is visible (no background work); the phone keeps counting on its own, so steps taken while the app was closed
 * are included from the first reading of the day. Before that first reading nothing is known, so it says so.
 */
@Composable
fun StepsCard(modifier: Modifier = Modifier) {
    val c = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val prefs = remember { c.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }
    val sm = remember { c.getSystemService(Context.SENSOR_SERVICE) as? SensorManager }
    val sensor = remember { sm?.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) }
    fun granted() = Build.VERSION.SDK_INT < 29 ||
        c.checkSelfPermission(Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED
    var ok by remember { mutableStateOf(granted()) }
    var state by remember { mutableStateOf(load(prefs)) }
    var dobText by remember { mutableStateOf(prefs.getString("dob", null)) }
    var askDob by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok = it }

    DisposableEffect(owner, ok, sensor) {
        val listener = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                val day = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
                val next = StepMath.advance(load(prefs), day, e.values[0], System.currentTimeMillis())
                save(prefs, next); state = next
            }
            override fun onAccuracyChanged(s: Sensor?, a: Int) {}
        }
        var on = false
        val obs = LifecycleEventObserver { _, ev ->
            if (ev == Lifecycle.Event.ON_START && ok && sensor != null && !on) { on = sm?.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI) == true }
            else if (ev == Lifecycle.Event.ON_STOP && on) { sm?.unregisterListener(listener); on = false }
        }
        owner.lifecycle.addObserver(obs)
        if (ok && sensor != null && owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) && !on) {
            on = sm?.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI) == true
        }
        onDispose { owner.lifecycle.removeObserver(obs); if (on) sm?.unregisterListener(listener) }
    }

    Card(modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Steps today", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
            val s = state?.takeIf { it.day == today }
            when {
                sensor == null -> Text("Unavailable: this phone has no step counter sensor.", fontSize = 14.sp)
                !ok -> {
                    Text("Unavailable: Android needs the Physical activity permission to read the step counter. Nothing is recorded or sent anywhere.", fontSize = 14.sp)
                    OutlinedButton(onClick = { launcher.launch(Manifest.permission.ACTIVITY_RECOGNITION) }) { Text("Allow step counting") }
                }
                s == null -> Text("Waiting for the first reading from the step counter...", fontSize = 14.sp)
                else -> {
                    Text("${StepMath.today(s)} steps", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    Text(
                        "Counted since ${SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(s.sinceMs))} today, when this app first read the phone's step counter. " +
                            "Steps before that time are not known, so the real total for the day can be higher.",
                        fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    val cal = Calendar.getInstance()
                    val ny = cal.get(Calendar.YEAR); val nm = cal.get(Calendar.MONTH) + 1; val nd = cal.get(Calendar.DAY_OF_MONTH)
                    val dob = dobText?.let { StepTarget.parseDob(it, ny, nm, nd) }
                    if (dob == null) {
                        Text("Daily target: Unavailable until you enter your date of birth. It stays on this phone.", fontSize = 12.sp)
                    } else {
                        val age = StepTarget.ageYears(dob, ny, nm, nd)
                        val target = StepTarget.forAge(age)
                        if (target == null) {
                            Text("Daily target: Unavailable. The published step reviews give no single evidence-based target for age $age (under 6, or 65 and over).", fontSize = 12.sp)
                        } else {
                            val steps = StepMath.today(s)
                            Text("Daily target for age $age: $target steps (you are at ${(steps * 100 / target).coerceAtMost(100)}%).", fontSize = 12.sp)
                            if (steps >= target) {
                                Text("Target reached today. Well done, keep it up!", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            }
                            Text(
                                "Source: Tudor-Locke et al. 2011, Int J Behav Nutr Phys Act 8:78-80 (adults 10,000 steps/day is reasonable; children and adolescents) and Colley et al. 2012 (12,000 steps/day matches 60 minutes of active time for ages 6 to 19). A general guide, not medical advice.",
                                fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    OutlinedButton(onClick = { askDob = true }) { Text(if (dobText == null) "Enter date of birth" else "Change date of birth") }
                }
            }
        }
    }

    if (askDob) {
        var txt by remember { mutableStateOf(dobText ?: "") }
        var err by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { askDob = false },
            title = { Text("Date of birth") },
            text = {
                Column {
                    OutlinedTextField(value = txt, onValueChange = { txt = it; err = false }, singleLine = true, label = { Text("DD-MM-YYYY") }, isError = err)
                    Text("Used only to pick your daily step target. Stored on this phone, never sent.", fontSize = 11.sp)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val cal = Calendar.getInstance()
                    val ok2 = StepTarget.parseDob(txt, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH))
                    if (ok2 == null) err = true else { prefs.edit().putString("dob", txt.trim()).apply(); dobText = txt.trim(); askDob = false }
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { askDob = false }) { Text("Cancel") } }
        )
    }
}
