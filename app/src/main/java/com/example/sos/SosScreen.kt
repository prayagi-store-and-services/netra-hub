package com.example.sos

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.BentoBackground
import com.example.ui.theme.BentoBorder
import com.example.ui.theme.BentoCardBg
import com.example.ui.theme.BentoGreenPrimary
import com.example.ui.theme.BentoGreenVibrant
import com.example.ui.theme.BentoRed
import com.example.ui.theme.BentoTextMuted
import com.example.ui.theme.BentoTextPrimary
import com.example.ui.theme.BentoTextSecondary

/**
 * SOS & Emergency screen (restyle per the owner's 9 Nov 2026 reference, bento dark navy).
 * Everything here is the existing working feature set; the only new behavior is the big
 * press-and-hold SOS button, which starts the EXACT shake countdown path in SosService
 * (ACTION_MANUAL_TRIGGER): 10 s cancel-first countdown, then the same SMS pipeline.
 * A tap never sends anything. Delivery is never promised.
 */
@Composable
fun SosScreen() {
    val ctx = LocalContext.current
    var on by remember { mutableStateOf(SosStore.enabled(ctx)) }
    var contacts by remember { mutableStateOf(SosStore.contacts(ctx)) }
    var input by remember { mutableStateOf("") }
    var msg by remember { mutableStateOf("") }
    var pName by remember { mutableStateOf(SosStore.name(ctx)) }
    var pDob by remember { mutableStateOf(SosStore.dob(ctx)) }
    var pBlood by remember { mutableStateOf(SosStore.blood(ctx)) }
    var pMsg by remember { mutableStateOf("") }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { res ->
        val sms = res[Manifest.permission.SEND_SMS] == true
        val loc = res[Manifest.permission.ACCESS_FINE_LOCATION] == true || res[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (sms && loc) { SosStore.setEnabled(ctx, true); SosService.start(ctx); on = true; msg = "" }
        else msg = "SOS stays OFF: SMS and location permission are both needed."
    }

    Column(
        Modifier.fillMaxSize().background(BentoBackground).verticalScroll(rememberScrollState()).padding(16.dp).testTag("sos_screen"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Hero: press-and-hold SOS. Deliberate by design: 3 s unbroken hold, then the 10 s cancel countdown.
        var holding by remember { mutableStateOf(false) }
        var holdProgress by remember { mutableStateOf(0f) }
        LaunchedEffect(holding) {
            if (!holding) { holdProgress = 0f; return@LaunchedEffect }
            val start = System.currentTimeMillis()
            while (true) {
                val held = System.currentTimeMillis() - start
                holdProgress = (held.toFloat() / SosLogic.MANUAL_HOLD_MS).coerceIn(0f, 1f)
                if (SosLogic.manualHoldFires(held)) break
                kotlinx.coroutines.delay(50)
            }
            holding = false
            holdProgress = 0f
            if (SosStore.enabled(ctx) && SosStore.contacts(ctx).isNotEmpty()) {
                ctx.startService(Intent(ctx, SosService::class.java).setAction(SosService.ACTION_MANUAL_TRIGGER))
            } else {
                msg = "SOS is not ready: turn on SOS shake with SMS + location permission and add at least one emergency contact first."
            }
        }
        Card(
            colors = CardDefaults.cardColors(containerColor = BentoCardBg),
            border = BorderStroke(1.dp, BentoBorder),
            modifier = Modifier.fillMaxWidth().testTag("sos_hero_card")
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(20.dp)) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(170.dp)) {
                    CircularProgressIndicator(
                        progress = { holdProgress },
                        color = BentoGreenPrimary,
                        trackColor = BentoBorder,
                        modifier = Modifier.size(170.dp),
                        strokeWidth = 4.dp
                    )
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(150.dp)
                            .background(BentoRed, CircleShape)
                            .pointerInput(Unit) {
                                detectTapGestures(onPress = {
                                    holding = true
                                    tryAwaitRelease()
                                    holding = false
                                })
                            }
                            .testTag("sos_hold_button")
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Call, contentDescription = null, tint = androidx.compose.ui.graphics.Color.White, modifier = Modifier.size(36.dp))
                            Text("SOS", color = androidx.compose.ui.graphics.Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Text(
                    "Press and hold for 3 seconds to send an emergency alert. A 10 second countdown with a Cancel button always comes first - nothing is sent on a tap.",
                    fontSize = 13.sp, color = BentoTextSecondary, textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 12.dp)
                )
                Text(
                    "SMS needs mobile network and delivery is not confirmed. Your location is included only if the phone has a fix.",
                    fontSize = 11.sp, color = BentoTextMuted, textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
        }

        // Shake SOS master switch.
        SosSection(title = "Shake SOS", subtitle = "Send SOS on 3 strong shakes") {
            Text("Shake the phone hard 3 times and it sends your location by SMS to your emergency contacts. You get a 10 second countdown with a Cancel button first. Switched OFF by default. While ON, a notification stays visible and the phone's motion sensor is read, which uses some battery. It cannot call anyone and it cannot promise delivery: SMS needs mobile network.", fontSize = 13.sp, color = BentoTextSecondary)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(if (on) "SOS shake: ON" else "SOS shake: OFF", color = BentoTextPrimary)
                Switch(
                    checked = on,
                    colors = SwitchDefaults.colors(checkedTrackColor = BentoGreenVibrant),
                    onCheckedChange = { want ->
                        if (!want) { SosService.stop(ctx); SosStore.setEnabled(ctx, false); on = false }
                        else if (contacts.isEmpty()) msg = "Add at least one emergency contact first."
                        else ask.launch(arrayOf(Manifest.permission.SEND_SMS, Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.POST_NOTIFICATIONS))
                    },
                    modifier = Modifier.testTag("sos_shake_switch")
                )
            }
            if (msg.isNotBlank()) Text(msg, fontSize = 13.sp, color = BentoTextSecondary)
        }

        // Emergency contacts.
        SosSection(title = "Emergency Contacts (${contacts.size})", subtitle = "Up to ${SosLogic.MAX_CONTACTS}, stored only on this phone") {
            contacts.forEach { n ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(n, Modifier.padding(end = 8.dp), color = BentoTextPrimary)
                    TextButton(onClick = { SosStore.removeContact(ctx, n); contacts = SosStore.contacts(ctx) }) { Text("Remove", color = BentoGreenPrimary) }
                }
            }
            OutlinedTextField(value = input, onValueChange = { input = it }, label = { Text("Phone number with country code") }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("sos_contact_input"))
            Button(onClick = {
                msg = if (SosStore.addContact(ctx, input)) { input = ""; contacts = SosStore.contacts(ctx); "" } else "Number not added: use 7 to 15 digits, no duplicates, max ${SosLogic.MAX_CONTACTS}."
            }, modifier = Modifier.testTag("sos_add_contact")) { Text("Add contact") }
        }

        // SOS message profile.
        SosSection(title = "SOS Message", subtitle = "Optional emergency profile, stored only on this phone") {
            Text("What you fill in here is added to the SOS SMS so helpers know who you are. Empty fields are left out. Age is worked out from your date of birth when the SMS is sent.", fontSize = 12.sp, color = BentoTextSecondary)
            OutlinedTextField(value = pName, onValueChange = { pName = it }, label = { Text("Your name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = pDob, onValueChange = { pDob = it }, label = { Text("Date of birth (yyyy-mm-dd)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = pBlood, onValueChange = { pBlood = it }, label = { Text("Blood group (optional, like O+)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Button(onClick = {
                val cal = java.util.Calendar.getInstance()
                val blood = SosLogic.cleanBlood(pBlood)
                pMsg = when {
                    blood == null -> "Blood group not saved: use one of ${SosLogic.BLOOD_GROUPS.joinToString(", ")} or leave it empty."
                    pDob.isNotBlank() && SosLogic.ageYears(pDob, cal.get(java.util.Calendar.YEAR), cal.get(java.util.Calendar.MONTH) + 1, cal.get(java.util.Calendar.DAY_OF_MONTH)) == null -> "Date of birth not saved: write it as yyyy-mm-dd, not in the future."
                    else -> { SosStore.saveProfile(ctx, pName, pDob, blood); "Profile saved on this phone." }
                }
            }, modifier = Modifier.testTag("sos_save_profile")) { Text("Save profile") }
            if (pMsg.isNotBlank()) Text(pMsg, fontSize = 13.sp, color = BentoTextSecondary)
        }

        // Crash detection.
        var crash by remember { mutableStateOf(SosStore.crashAlert(ctx)) }
        SosSection(title = "Crash Detection", subtitle = "Auto detect vehicle impact") {
            Text("When Driving mode is active and the phone feels a very hard hit, a loud siren plays for ${ImpactLogic.COUNTDOWN_S} seconds with a Cancel button. If you do not cancel, the same SOS SMS goes to your contacts. Phone sensors cannot tell a real crash from a hard drop, so false alarms can happen. Needs SOS shake to be ON. OFF by default.", fontSize = 12.sp, color = BentoTextSecondary)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(if (crash) "Crash alert: ON" else "Crash alert: OFF", color = BentoTextPrimary)
                Switch(
                    checked = crash,
                    colors = SwitchDefaults.colors(checkedTrackColor = BentoGreenVibrant),
                    onCheckedChange = { crash = it; SosStore.setCrashAlert(ctx, it) },
                    modifier = Modifier.testTag("sos_crash_switch")
                )
            }
            Text("Stop the siren", fontSize = 14.sp, color = BentoTextPrimary)
            Text("During the crash countdown the siren keeps playing until you press this or the notification button. It also cancels the SOS SMS.", fontSize = 12.sp, color = BentoTextSecondary)
            Button(onClick = {
                if (SosStore.enabled(ctx)) ctx.startService(Intent(ctx, SosService::class.java).setAction(SosService.ACTION_CANCEL))
                else msg = "Nothing to stop: SOS shake is OFF."
            }, modifier = Modifier.testTag("sos_cancel")) { Text("STOP SIREN / CANCEL") }
        }

        // Parking spot.
        var park by remember { mutableStateOf(ParkingSpot.enabled(ctx)) }
        var parkMsg by remember { mutableStateOf("") }
        var spot by remember { mutableStateOf(ParkingSpot.get(ctx)) }
        val parkAsk = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { res ->
            val ok = res[Manifest.permission.ACCESS_FINE_LOCATION] == true || res[Manifest.permission.ACCESS_COARSE_LOCATION] == true
            if (ok) { ParkingSpot.setEnabled(ctx, true); park = true; parkMsg = "" } else parkMsg = "Parking spot stays OFF: location permission is needed."
        }
        SosSection(title = "Parking Spot", subtitle = "Saves where you stopped, on this phone only") {
            Text("When driving ends, this saves where you stopped so you can find your vehicle. One spot only, kept on this phone, never sent anywhere. Driving detection is a sensor guess, so the spot can be wrong or missed.", fontSize = 12.sp, color = BentoTextSecondary)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(if (park) "Parking spot: ON" else "Parking spot: OFF", color = BentoTextPrimary)
                Switch(
                    checked = park,
                    colors = SwitchDefaults.colors(checkedTrackColor = BentoGreenVibrant),
                    onCheckedChange = { want ->
                        if (!want) { ParkingSpot.setEnabled(ctx, false); park = false; spot = null; parkMsg = "" }
                        else parkAsk.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                    },
                    modifier = Modifier.testTag("sos_parking_switch")
                )
            }
            if (parkMsg.isNotBlank()) Text(parkMsg, fontSize = 13.sp, color = BentoTextSecondary)
            if (park) {
                val s = spot ?: ParkingSpot.get(ctx)
                if (s != null) {
                    Text("Last parking spot: GPS fix from " + java.text.SimpleDateFormat("d MMM, HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(s.at)) + ", " + ParkingSpot.accuracyText(s.accuracyM), fontSize = 13.sp, color = BentoTextPrimary)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            try { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(ParkingSpot.mapsUri(s.lat, s.lon))).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                            catch (e: Exception) { parkMsg = "No maps app found on this phone." }
                        }) { Text("Open in Maps") }
                        TextButton(onClick = { ParkingSpot.clear(ctx); spot = null }) { Text("Clear", color = BentoGreenPrimary) }
                    }
                } else {
                    val st = ParkingSpot.status(ctx)
                    Text(if (st.isNotBlank()) st else "No parking spot saved yet.", fontSize = 13.sp, color = BentoTextSecondary)
                }
            }
        }

        // Official 112 India app.
        SosSection(title = "Official 112 India app", subtitle = "Government emergency app, no data shared") {
            Text("Opens the government 112 India app, or its Google Play page if it is not installed. This hub shares no data with it and does not contact 112 itself.", fontSize = 12.sp, color = BentoTextSecondary)
            Button(onClick = { open112App(ctx) }, modifier = Modifier.testTag("sos_open_112")) { Text("Open 112 India") }
        }

        Text("Battery Sentinel is currently the only Netra app that receives the driving flag (yes or no, and a time).", fontSize = 12.sp, color = BentoTextMuted)
        Text("Crash alert and parking spot were tested in simulation (unit tests), not in real crashes or real drives.", fontSize = 12.sp, color = BentoTextMuted)
        if (contacts.size < 3) Text("Tip: three or more emergency contacts are recommended. SOS works with one.", fontSize = 12.sp, color = BentoTextMuted)
    }
}

@Composable
private fun SosSection(title: String, subtitle: String, content: @Composable () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = BentoCardBg),
        border = BorderStroke(1.dp, BentoBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = BentoTextPrimary)
                Text("- $subtitle", fontSize = 12.sp, color = BentoTextMuted)
            }
            content()
        }
    }
}

private const val APP_112 = "in.cdac.ners.psa.mobile.android.national"

/** Opens the official 112 India app if installed, else its Play page (market:// first, then the web link). */
private fun open112App(ctx: android.content.Context) {
    val launch = ctx.packageManager.getLaunchIntentForPackage(APP_112)
    val intent = launch ?: Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$APP_112"))
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try { ctx.startActivity(intent) } catch (e: Exception) {
        try {
            ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$APP_112")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e2: Exception) { android.widget.Toast.makeText(ctx, "Could not open 112 India. Install it from Google Play.", android.widget.Toast.LENGTH_LONG).show() }
    }
}
