package com.example.sos

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** SOS shake settings. Switched OFF by default; the user turns it on here. */
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
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("SOS shake", fontSize = 20.sp)
        Text("Shake the phone hard 3 times and it sends your location by SMS to your emergency contacts. You get a 10 second countdown with a Cancel button first. Switched OFF by default. While ON, a notification stays visible and the phone's motion sensor is read, which uses some battery. It cannot call anyone and it cannot promise delivery: SMS needs mobile network.", fontSize = 13.sp)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(if (on) "SOS shake: ON" else "SOS shake: OFF")
            Switch(checked = on, onCheckedChange = { want ->
                if (!want) { SosService.stop(ctx); SosStore.setEnabled(ctx, false); on = false }
                else if (contacts.isEmpty()) msg = "Add at least one emergency contact first."
                else ask.launch(arrayOf(Manifest.permission.SEND_SMS, Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.POST_NOTIFICATIONS))
            })
        }
        if (msg.isNotBlank()) Text(msg, fontSize = 13.sp)
        Text("Emergency contacts (up to ${SosLogic.MAX_CONTACTS}, stored only on this phone)", fontSize = 14.sp)
        contacts.forEach { n ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(n, Modifier.padding(end = 8.dp))
                TextButton(onClick = { SosStore.removeContact(ctx, n); contacts = SosStore.contacts(ctx) }) { Text("Remove") }
            }
        }
        OutlinedTextField(value = input, onValueChange = { input = it }, label = { Text("Phone number with country code") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Button(onClick = {
            msg = if (SosStore.addContact(ctx, input)) { input = ""; contacts = SosStore.contacts(ctx); "" } else "Number not added: use 7 to 15 digits, no duplicates, max ${SosLogic.MAX_CONTACTS}."
        }) { Text("Add contact") }
        Text("Emergency profile (optional, stored only on this phone)", fontSize = 14.sp)
        Text("What you fill in here is added to the SOS SMS so helpers know who you are. Empty fields are left out. Age is worked out from your date of birth when the SMS is sent.", fontSize = 12.sp)
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
        }) { Text("Save profile") }
        if (pMsg.isNotBlank()) Text(pMsg, fontSize = 13.sp)
        Text("Official 112 India app", fontSize = 14.sp)
        Text("Opens the government 112 India app, or its Google Play page if it is not installed. This hub shares no data with it and does not contact 112 itself.", fontSize = 12.sp)
        Button(onClick = { open112App(ctx) }) { Text("Open 112 India") }
        var crash by remember { mutableStateOf(SosStore.crashAlert(ctx)) }
        Text("Crash alert while driving (optional)", fontSize = 14.sp)
        Text("When Driving mode is active and the phone feels a very hard hit, a loud siren plays for ${ImpactLogic.COUNTDOWN_S} seconds with a Cancel button. If you do not cancel, the same SOS SMS goes to your contacts. Phone sensors cannot tell a real crash from a hard drop, so false alarms can happen. Needs SOS shake to be ON. OFF by default.", fontSize = 12.sp)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(if (crash) "Crash alert: ON" else "Crash alert: OFF")
            Switch(checked = crash, onCheckedChange = { crash = it; SosStore.setCrashAlert(ctx, it) })
        }
        if (contacts.size < 3) Text("Tip: three or more emergency contacts are recommended. SOS works with one.", fontSize = 12.sp)
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
