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
    }
}
