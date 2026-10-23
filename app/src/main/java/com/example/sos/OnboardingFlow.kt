package com.example.sos

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Mandatory safety setup (v1.4.32). Blocks the main app until the required profile exists: name, age and at
 * least 3 emergency contacts (max 5). Auto-LAUNCHES on app open - nothing is auto-filled or auto-approved.
 * Existing users after the update see the same flow with their saved details prefilled; a complete profile
 * finishes as a quick review. OS permissions are requested with reasons and may be declined (features then
 * show as disabled); finishing onboarding never switches on SMS/location disclosure by itself.
 */
@Composable
fun OnboardingFlow(onDone: () -> Unit) {
    val ctx = LocalContext.current
    var step by remember { mutableStateOf(0) }
    var contacts by remember { mutableStateOf(SosStore.contacts(ctx)) }
    var name by remember { mutableStateOf(SosStore.name(ctx)) }
    var ageText by remember {
        mutableStateOf(SosStore.ageYears(ctx).takeIf { it in 1..120 }?.toString()
            ?: SosLogic.ageYears(SosStore.dob(ctx), java.util.Calendar.getInstance().get(java.util.Calendar.YEAR),
                java.util.Calendar.getInstance().get(java.util.Calendar.MONTH) + 1,
                java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_MONTH))?.toString() ?: "")
    }
    var blood by remember { mutableStateOf(SosStore.blood(ctx)) }
    var msg by remember { mutableStateOf("") }

    Column(Modifier.padding(20.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Netra Human Safety - zaroori setup", fontSize = 20.sp)
        when (step) {
            0 -> {
                Text("Yeh app emergency mein kaam aati hai, isliye pehle chhoti setup poori karni hai. Bina iske main app nahi khulega. Jo details pehle se bhari hain woh waisi hi rakhi gayi hain - bas check karke aage badhein.", fontSize = 13.sp)
                Text("Zaroori: naam, age, aur kam se kam 3 emergency contacts. Blood group optional hai.", fontSize = 13.sp)
                Button(onClick = { step = 1 }) { Text("Shuru karein") }
            }
            1 -> {
                Text("Aapki details (SOS SMS mein bheji jati hain, jab aap SOS/crash alert ON karein)", fontSize = 14.sp)
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Naam (zaroori)") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = ageText, onValueChange = { ageText = it.filter(Char::isDigit).take(3) }, label = { Text("Age (zaroori)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = blood, onValueChange = { blood = it }, label = { Text("Blood group (optional)") }, modifier = Modifier.fillMaxWidth())
                if (msg.isNotEmpty()) Text(msg, fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { step = 0 }) { Text("Peeche") }
                    Button(onClick = {
                        val age = ageText.toIntOrNull()
                        when {
                            name.isBlank() -> msg = "Naam zaroori hai."
                            age == null || age !in 1..120 -> msg = "Sahi age likhein (1-120)."
                            else -> {
                                SosStore.saveProfile(ctx, name, SosStore.dob(ctx), blood)
                                SosStore.setAgeYears(ctx, age)
                                msg = ""; step = 2
                            }
                        }
                    }) { Text("Aage") }
                }
            }
            2 -> {
                Text("Emergency contacts (${contacts.size}/5) - kam se kam 3 zaroori", fontSize = 14.sp)
                Text("SOS SMS sirf inhi numbers par jata hai, aur sirf jab aap SOS ya crash alert ON karke countdown cancel na karein.", fontSize = 12.sp)
                contacts.forEach { n ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(n, Modifier.weight(1f), fontSize = 14.sp)
                        TextButton(onClick = { SosStore.removeContact(ctx, n); contacts = SosStore.contacts(ctx) }) { Text("Hatao") }
                    }
                }
                var input by remember { mutableStateOf("") }
                OutlinedTextField(value = input, onValueChange = { input = it }, label = { Text("Number type karein") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        if (SosStore.addContact(ctx, input)) { contacts = SosStore.contacts(ctx); input = ""; msg = "" }
                        else msg = "Number sahi nahi, pehle se hai, ya 5 contacts poore."
                    }) { Text("Add karein") }
                }
                val pickLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickContact()) { uri: Uri? ->
                    if (uri != null) {
                        val picked = readPickedContact(ctx, uri)
                        if (picked != null && SosStore.addContact(ctx, picked)) { contacts = SosStore.contacts(ctx); msg = "" }
                        else msg = if (picked == null) "Is contact ka number padh nahi paye - manually type karein." else "Number add nahi hua (duplicate ya limit)."
                    }
                }
                TextButton(onClick = { pickLauncher.launch(null) }) { Text("Phone contacts se chunein (ek-ek karke)") }
                Text("Sirf aapke chune hue contacts add hote hain - poori contact list kabhi nahi padhi ya bheji jati.", fontSize = 12.sp)
                if (msg.isNotEmpty()) Text(msg, fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { step = 1 }) { Text("Peeche") }
                    Button(onClick = { if (contacts.size >= 3) { msg = ""; step = 3 } else msg = "Kam se kam 3 emergency contacts zaroori hain." }) { Text("Aage") }
                }
            }
            3 -> {
                Text("Permissions (har ek ki wajah ke saath)", fontSize = 14.sp)
                Text("Koi bhi permission mana kar sakte hain - us feature ko disabled dikhaya jayega. Mana karna kabhi ON nahi maana jata.", fontSize = 12.sp)
                PermissionRow(ctx, "Location", "Driving mode, GPS speed aur SOS location ke liye. Background location se app band hone par bhi driving detection chalti hai.", Manifest.permission.ACCESS_FINE_LOCATION)
                PermissionRow(ctx, "SMS", "Emergency SOS aur crash SMS aapke contacts ko bhejne ke liye. Iske bina SOS SMS nahi ja sakta.", Manifest.permission.SEND_SMS)
                if (Build.VERSION.SDK_INT >= 33) PermissionRow(ctx, "Notifications", "SOS countdown aur safety alerts dikhane ke liye.", Manifest.permission.POST_NOTIFICATIONS)
                if (Build.VERSION.SDK_INT >= 29) PermissionRow(ctx, "Activity recognition", "Driving detection mein harkat pehchanne ke liye.", Manifest.permission.ACTIVITY_RECOGNITION)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { step = 2 }) { Text("Peeche") }
                    Button(onClick = { step = 4 }) { Text("Aage") }
                }
            }
            4 -> {
                var sosOn by remember { mutableStateOf(SosStore.enabled(ctx)) }
                var crash by remember { mutableStateOf(SosStore.crashAlert(ctx)) }
                val smsGranted = ctx.checkSelfPermission(Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED
                Text("Emergency alerts - aapka faisla", fontSize = 14.sp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("SOS shake", fontSize = 14.sp)
                        Text("3 tez jhatkon par 10 second siren + Cancel; cancel na karein to contacts ko SOS SMS. Location isi SMS mein jati hai.", fontSize = 12.sp)
                    }
                    Switch(checked = sosOn, onCheckedChange = { sosOn = it; SosStore.setEnabled(ctx, it) })
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Crash alert (driving ke dauran)", fontSize = 14.sp)
                        Text("Bahut tez jhatka (4g) aane par ${ImpactLogic.COUNTDOWN_S} second siren + Cancel. Cancel na karein to SOS SMS. " + SosConsent.payloadSummary() + " SMS in contacts ko jayega: ${contacts.joinToString(", ")}. Delivery confirm nahi hoti; phone asli crash aur girne mein farak nahi kar sakta. Chalne ke liye SOS shake bhi ON hona chahiye - dono alag switches hain, ek doosre ko apne aap ON nahi karta.", fontSize = 12.sp)
                    }
                    Switch(checked = crash, onCheckedChange = { want ->
                        if (want && !SosConsent.canEnable(contacts.size, smsGranted)) {
                            msg = "Crash alert ON karne ke liye kam se kam 1 contact aur SMS permission chahiye."
                        } else { crash = want; SosStore.setCrashAlert(ctx, want); msg = "" }
                    })
                }
                if (msg.isNotEmpty()) Text(msg, fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { step = 3 }) { Text("Peeche") }
                    Button(onClick = { msg = ""; step = 5 }) { Text("Aage") }
                }
            }
            5 -> {
                var tick by remember { mutableStateOf(0) }
                val smsGranted = ctx.checkSelfPermission(Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED
                val actions = contacts.associateWith { OnboardingStore.introAction(ctx, it) }
                val allActed = contacts.all { actions[it] != null }
                val locGranted = ctx.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                Text("Setup ka sach: " + (if (locGranted) "Location ON - driving detection chal sakti hai." else "Location permission nahi - driving detection DISABLED rahegi jab tak allow na karein.") + (if (smsGranted) " SMS ON." else " SMS permission nahi - SOS/introduction SMS nahi ja sakta."), fontSize = 12.sp)
                Text("Contacts ko introduction SMS (zaroori step)", fontSize = 14.sp)
                Text("Har contact ko batana zaroori hai ki aapne unhe emergency contact banaya hai. Har contact ke saamne Send dabayenge to yeh SMS jayega (SMS charges lag sakte hain; delivery confirm nahi hoti; yeh emergency NAHI hai). Ya neeche 'Baad mein bataunga' chunein - dono faisle aapke hote hain, koi automatic SMS nahi jata.", fontSize = 12.sp)
                Text("Message jo jayega:", fontSize = 12.sp)
                Text(IntroSms.text(name), fontSize = 12.sp)
                contacts.forEach { n ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text(n, fontSize = 14.sp)
                            Text(when (actions[n]) { OnboardingStore.INTRO_SENT -> "SMS bheja gaya (delivery confirm nahi)"; OnboardingStore.INTRO_LATER -> "Baad mein batayenge"; else -> "Abhi faisla nahi" }, fontSize = 11.sp)
                        }
                        if (actions[n] == null) {
                            TextButton(onClick = {
                                if (!smsGranted) { msg = "SMS permission nahi hai - 'Baad mein' chunein ya permission dein." }
                                else if (IntroSms.send(ctx, n, name)) { tick++ } else { msg = "SMS bhejne mein samasya aayi." }
                            }) { Text("Send") }
                        }
                    }
                }
                if (msg.isNotEmpty()) Text(msg, fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { step = 4 }) { Text("Peeche") }
                    if (!allActed) TextButton(onClick = { contacts.forEach { if (OnboardingStore.introAction(ctx, it) == null) OnboardingStore.setIntroAction(ctx, it, OnboardingStore.INTRO_LATER) }; tick++ }) { Text("Baad mein bataunga") }
                    Button(onClick = {
                        if (allActed && OnboardingStore.requirementsMet(ctx)) { OnboardingStore.markDone(ctx); onDone() }
                        else msg = if (!allActed) "Har contact ke liye Send ya 'Baad mein' chunein." else "Zaroori details adhoori hain - peeche jaakar poore karein."
                    }) { Text("Setup poora karein") }
                }
                tick.let { }
            }
        }
    }
}

@Composable
private fun PermissionRow(ctx: Context, label: String, reason: String, permission: String) {
    var granted by remember { mutableStateOf(ctx.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("$label: " + if (granted) "ON" else "OFF (feature disabled)", fontSize = 14.sp)
                Text(reason, fontSize = 12.sp)
            }
            if (!granted) TextButton(onClick = { launcher.launch(permission) }) { Text("Allow") }
        }
    }
}

/** Reads name/number of the one contact the user picked. The picker grants temporary access to that contact only; no address-book scan. */
private fun readPickedContact(ctx: Context, uri: Uri): String? = try {
    var id: String? = null
    ctx.contentResolver.query(uri, arrayOf(ContactsContract.Contacts._ID), null, null, null)?.use { if (it.moveToFirst()) id = it.getString(0) }
    if (id == null) null else ctx.contentResolver.query(
        ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
        arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
        ContactsContract.CommonDataKinds.Phone.CONTACT_ID + "=?", arrayOf(id), null
    )?.use { if (it.moveToFirst()) it.getString(0) else null }
} catch (_: Exception) { null }
