package com.example.camcheck

import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

private fun backCameraWithFlash(context: Context): String? = try {
    val cm = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    cm.cameraIdList.firstOrNull { id ->
        val c = cm.getCameraCharacteristics(id)
        c.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true &&
            c.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
    }
} catch (e: Exception) {
    null
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            content()
        }
    }
}

/**
 * Hidden camera check. Everything here is something a phone can really do, and the screen says what it cannot prove.
 * Nothing on this screen is stored or sent anywhere.
 */
@Composable
fun CameraCheckScreen(modifier: Modifier = Modifier, onBack: (() -> Unit)? = null) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val main = remember { Handler(Looper.getMainLooper()) }

    var step by remember { mutableStateOf(0) }
    val stepCount = 5

    // Magnetic field
    var magNow by remember { mutableFloatStateOf(Float.NaN) }
    var magBase by remember { mutableFloatStateOf(Float.NaN) }
    var hasMag by remember { mutableStateOf(true) }
    DisposableEffect(step == 0) {
        if (step != 0) return@DisposableEffect onDispose { }
        magBase = Float.NaN
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensor = sm.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
        val l = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                val v = CameraCheckLogic.magnitude(e.values[0], e.values[1], e.values[2])
                magNow = v
                if (magBase.isNaN()) magBase = v
            }
            override fun onAccuracyChanged(s: Sensor?, a: Int) {}
        }
        if (sensor == null) hasMag = false else sm.registerListener(l, sensor, SensorManager.SENSOR_DELAY_UI)
        onDispose { sm.unregisterListener(l) }
    }

    // Torch
    val flashId = remember { backCameraWithFlash(context) }
    var torchOn by remember { mutableStateOf(false) }
    var torchError by remember { mutableStateOf<String?>(null) }
    // The torch is switched off whenever you leave step 2 or this screen.
    DisposableEffect(step) {
        onDispose {
            if (torchOn && flashId != null) {
                try { (context.getSystemService(Context.CAMERA_SERVICE) as CameraManager).setTorchMode(flashId, false) } catch (e: Exception) {}
                torchOn = false
            }
        }
    }

    // Wi-Fi scan
    val devices = remember { mutableStateListOf<LanDevice>() }
    var scanning by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var scanMsg by remember { mutableStateOf<String?>(null) }
    var job by remember { mutableStateOf<Job?>(null) }
    DisposableEffect(Unit) { onDispose { job?.cancel() } }

    Column(modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (onBack != null) OutlinedButton(onClick = onBack) { Text("Back") }
        Text("Travel Checking", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(
            "Honest note: a phone cannot prove that a room has no hidden camera. These tools only give hints and help you look. " +
                "Nothing here is stored or sent anywhere. Nothing runs in the background: the magnetic meter works only while step 1 is open, the torch only while step 2 is open, and the Wi-Fi scan only when you tap Scan.",
            style = MaterialTheme.typography.bodyMedium
        )
        val titles = listOf("Magnetic field meter", "Lens finder", "Infrared check", "Wi-Fi device scan", "Room checklist")
        Text("Step ${step + 1} of $stepCount: ${titles[step]}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        LinearProgressIndicator(progress = { (step + 1) / stepCount.toFloat() }, modifier = Modifier.fillMaxWidth())

        if (step == 0) SectionCard("1. Magnetic field meter") {
            if (!hasMag) {
                Text("Unavailable: this phone has no magnetic sensor.")
            } else {
                val level = if (magNow.isNaN() || magBase.isNaN()) null else CameraCheckLogic.magLevel(magBase, magNow)
                Text(if (magNow.isNaN()) "Reading: Unavailable (waiting for the sensor)" else "Now: %.1f uT   Start value: %.1f uT".format(magNow, magBase))
                Text(
                    when (level) {
                        null -> "Waiting for a reading."
                        MagLevel.NORMAL -> "Normal. No jump from the start value."
                        MagLevel.NOTICEABLE -> "Noticeable jump: a magnet or electronics may be close. Look at what is near the phone."
                        MagLevel.STRONG -> "Strong jump: a strong magnet or powered device is very close. Look at it closely."
                    },
                    fontWeight = FontWeight.SemiBold
                )
                Text("Hold the phone in open air, tap Set start value, then move it slowly near objects. This can show that something with a magnet or motor is there. It cannot tell if it is a camera.", style = MaterialTheme.typography.bodySmall)
                OutlinedButton(onClick = { if (!magNow.isNaN()) magBase = magNow }) { Text("Set start value here") }
            }
        }

        if (step == 1) SectionCard("2. Lens finder (torch)") {
            Text("Switch the room lights off. Turn the torch on and sweep it slowly over mirrors, clocks, smoke detectors, vents and shelves. A camera lens gives back a small bright glint. Look at it from a few angles.")
            if (flashId == null) {
                Text("Unavailable: this phone has no back torch.")
            } else {
                Button(onClick = {
                    try {
                        (context.getSystemService(Context.CAMERA_SERVICE) as CameraManager).setTorchMode(flashId, !torchOn)
                        torchOn = !torchOn; torchError = null
                    } catch (e: Exception) { torchError = "Could not switch the torch: another app may be using the camera." }
                }) { Text(if (torchOn) "Turn torch off" else "Turn torch on") }
                torchError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        }

        if (step == 2) SectionCard("3. Infrared check (guide)") {
            Text("Some hidden cameras use infrared lights for night vision. In a dark room, many rear phone cameras show these lights as faint purple or white dots. Open your camera, point it around the dark room and look for dots that do not move with the phone.")
            Text("Some phones filter infrared out. Seeing nothing does NOT prove the room is clear.", style = MaterialTheme.typography.bodySmall)
            Button(onClick = {
                try { context.startActivity(Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                catch (e: Exception) { scanMsg = "Unavailable: could not open the camera app." }
            }) { Text("Open camera app") }
        }

        if (step == 3) SectionCard("4. Wi-Fi device scan") {
            Text("Lists devices that answer on the Wi-Fi network you are connected to (like a network scanner). A camera that records to a card or uses another network will not show up here.")
            Text("Limits: Android does not let apps read device MAC addresses or brands, so those are Unavailable. \"Possible camera\" is a hint, not proof.", style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(enabled = !scanning, onClick = {
                    devices.clear(); scanMsg = null; progress = 0f; scanning = true
                    job = scope.launch {
                        val r = LanScanner.scan(
                            context,
                            onProgress = { d, t -> main.post { progress = d.toFloat() / t } },
                            onDevice = { dev -> main.post { devices.add(dev) } }
                        )
                        main.post {
                            scanning = false
                            scanMsg = when (r) {
                                is ScanOutcome.NotAvailable -> r.reason
                                is ScanOutcome.Done -> "Done. Checked ${r.checked} addresses, found ${r.found} device(s)."
                            }
                        }
                    }
                }) { Text(if (scanning) "Scanning..." else "Scan this Wi-Fi network") }
                if (scanning) OutlinedButton(onClick = { job?.cancel(); scanning = false; scanMsg = "Stopped." }) { Text("Stop") }
            }
            if (scanning) LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
            scanMsg?.let { Text(it, fontWeight = FontWeight.SemiBold) }
            devices.sortedBy { d -> d.ip.split(".").last().toIntOrNull() ?: 0 }.forEach { d ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text(d.ip + "   " + (d.hostName ?: "name: Unavailable"), fontWeight = FontWeight.SemiBold)
                        Text("Open ports: " + (if (d.openPorts.isEmpty()) "none found (answered a ping only)" else d.openPorts.joinToString(", ")), style = MaterialTheme.typography.bodySmall)
                        Text("MAC / brand: Unavailable on this Android version", style = MaterialTheme.typography.bodySmall)
                        if (d.cameraHint != null) Text("Possible camera: " + d.cameraHint, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        if (step == 4) SectionCard("5. Hotel room check, step by step") {
            CameraCheckLogic.ROOM_CHECKLIST.forEachIndexed { i, s -> Text("${i + 1}. $s") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(enabled = step > 0, onClick = { step-- }) { Text("Previous") }
            Button(enabled = step < stepCount - 1, onClick = { step++ }) { Text(if (step < stepCount - 1) "Next step" else "Done") }
        }
        Spacer(Modifier.height(24.dp))
    }
}
