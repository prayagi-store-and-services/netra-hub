package com.example.ui.screens

import android.location.Location
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.service.OfficialLocationContextManager
import com.example.ui.theme.BentoBorder
import com.example.ui.theme.BentoCardBg
import com.example.ui.theme.BentoGreenPrimary
import com.example.ui.theme.BentoRed
import com.example.ui.theme.BentoTextPrimary
import com.example.ui.theme.BentoTextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** One earthquake read from the USGS public feed. */
data class QuakeItem(val magnitude: Double, val place: String, val timeMillis: Long, val distanceKm: Double, val id: String = "")

/** Pure parser so it can be tested: reads USGS GeoJSON and keeps events within radiusKm of the given point. */
internal fun parseUsgsQuakes(json: String, lat: Double, lon: Double, radiusKm: Double): List<QuakeItem>? = try {
    val features = JSONObject(json).getJSONArray("features")
    val out = mutableListOf<QuakeItem>()
    for (i in 0 until features.length()) {
        val f = features.getJSONObject(i)
        val p = f.getJSONObject("properties")
        if (p.isNull("mag")) continue
        val c = f.getJSONObject("geometry").getJSONArray("coordinates")
        val res = FloatArray(1)
        Location.distanceBetween(lat, lon, c.getDouble(1), c.getDouble(0), res)
        val km = res[0] / 1000.0
        if (km <= radiusKm) out.add(QuakeItem(p.getDouble("mag"), p.optString("place", "Unavailable"), p.getLong("time"), km, f.optString("id", "")))
    }
    out.sortedByDescending { it.timeMillis }
} catch (e: Exception) {
    null
}

private const val QUAKE_DAYS = 7

/**
 * Safety card: earthquakes within about 200 km of the phone in the last 7 days, from the USGS public feed.
 * Also shows the result of the automatic background check (QuakeWatchWorker). The button is a manual backup. Shows Unavailable when location or the feed cannot be read.
 */
@Composable
fun EarthquakeCard() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var filters by remember { mutableStateOf(com.example.data.engine.QuakeAlertSettings.read(context)) }
    var lines by remember { mutableStateOf<List<String>>(emptyList()) }
    val prefs = remember { context.getSharedPreferences(com.example.data.engine.QuakeWatchWorker.PREFS, android.content.Context.MODE_PRIVATE) }
    val autoLast = prefs.getLong(com.example.data.engine.QuakeWatchWorker.KEY_LAST, 0L)
    val autoMsg = prefs.getString(com.example.data.engine.QuakeWatchWorker.KEY_MSG, null)
    var headline by remember { mutableStateOf("Not checked in this session. Tap Check now.") }
    var bad by remember { mutableStateOf(false) }
    val fmt = remember { SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault()) }
    Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(BentoCardBg)
            .border(1.dp, BentoBorder, RoundedCornerShape(24.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("Earthquakes near you", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = BentoTextPrimary)
        QuakeFilterControls(filters, onChange = { changed ->
            filters = changed
            com.example.data.engine.QuakeAlertSettings.write(context, changed)
            com.example.data.engine.QuakeWatchWorker.schedule(context)
            lines = emptyList()
            headline = "Filters changed. Tap Check now for matching results."
        }, editable = !busy)
        Text(
            "Last " + QUAKE_DAYS + " days, within " + filters.distanceKm + " km of this phone, magnitude " + filters.minimumMagnitude + " and above. Source: USGS (earthquake.usgs.gov). When enabled, the app also checks automatically about every 30 minutes and notifies you of a new quake (Android may delay this). Smaller quakes in India may be missing from this feed, and this is not an official warning.",
            fontSize = 12.sp, color = BentoTextSecondary
        )
        Text(
            if (!filters.enabled) "Automatic earthquake alerts: off on this phone."
            else if (autoLast == 0L || autoMsg == null) "Automatic check: has not run yet on this phone (Android decides when it runs)."
            else "Automatic check (" + fmt.format(Date(autoLast)) + "): " + autoMsg,
            fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = BentoTextPrimary
        )
        val sPrefs = remember { context.getSharedPreferences(com.example.data.engine.SachetWatchWorker.PREFS, android.content.Context.MODE_PRIVATE) }
        val sLast = sPrefs.getLong(com.example.data.engine.SachetWatchWorker.KEY_LAST, 0L)
        val sMsg = sPrefs.getString(com.example.data.engine.SachetWatchWorker.KEY_MSG, null)
        Text(
            if (sLast == 0L || sMsg == null) "Official alerts (NDMA SACHET, matched by area name): has not run yet on this phone."
            else "Official alerts, NDMA SACHET (" + fmt.format(Date(sLast)) + "): " + sMsg,
            fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = BentoTextPrimary
        )
        AlertAreaControls()
        var announceOn by remember { mutableStateOf(com.example.data.engine.OfficialAlertAnnouncer.isEnabled(context)) }
        androidx.compose.foundation.layout.Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Announce official alerts aloud", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = BentoTextPrimary)
                Text(
                    "Reads sarkari (official) weather/disaster and earthquake alerts aloud even in silent time, using the alarm sound channel. Official alerts only, never anything else.",
                    fontSize = 11.sp, color = BentoTextSecondary
                )
            }
            Switch(checked = announceOn, onCheckedChange = { on ->
                announceOn = on
                com.example.data.engine.OfficialAlertAnnouncer.setEnabled(context, on)
                if (on && !com.example.data.engine.OfficialAlertAnnouncer.hasPolicyAccess(context)) {
                    try {
                        context.startActivity(android.content.Intent(android.provider.Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
                    } catch (_: Exception) {}
                }
            })
        }
        run {
            val am = context.getSystemService(android.content.Context.AUDIO_SERVICE) as? android.media.AudioManager
            val nm = context.getSystemService(android.content.Context.NOTIFICATION_SERVICE) as? android.app.NotificationManager
            val limit = com.example.data.engine.OfficialAlertAnnouncer.limitsText(
                nm?.currentInterruptionFilter ?: android.app.NotificationManager.INTERRUPTION_FILTER_ALL,
                am?.getStreamVolume(android.media.AudioManager.STREAM_ALARM) ?: 1
            )
            if (limit != null) Text(limit, fontSize = 11.sp, color = BentoRed)
        }
        // Clearly labelled TEST buttons (ecosystem rule, 19 Oct): each exercises the real
        // alert path - same alarm stream, focus handling and emergency channel - with wording
        // that can never be mistaken for a real emergency.
        var testNote by remember { mutableStateOf<String?>(null) }
        androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                testNote = com.example.data.engine.OfficialAlertAnnouncer.sendTestAnnouncement(context)
            }) { Text("Test announcement", fontSize = 12.sp) }
            Button(onClick = {
                val posted = com.example.util.NetraNotificationManager(context).sendTestNotification()
                testNote = if (posted) "Test notification posted. It is only a test - real alerts say NETRA ALERT."
                else "Test notification could not be posted: notification permission is off for this app."
            }) { Text("Test notification", fontSize = 12.sp) }
        }
        testNote?.let { Text(it, fontSize = 11.sp, color = BentoTextSecondary) }
        Button(enabled = !busy, onClick = {
            busy = true
            scope.launch {
                val loc = withContext(Dispatchers.IO) { OfficialLocationContextManager(context).refreshLocation() }
                val ok = loc.locationStatus == "LOCATION_AVAILABLE" || loc.locationStatus == "COORDINATES_AVAILABLE" || loc.locationStatus == "GEOCODER_FAILED"
                if (!ok || (loc.latitude == 0.0 && loc.longitude == 0.0)) {
                    headline = "Unavailable: no area check was made. " + com.example.data.service.LocationAccess.failureText(loc.locationStatus, true)
                    bad = true; lines = emptyList()
                } else {
                    val start = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(System.currentTimeMillis() - QUAKE_DAYS * 86_400_000L))
                    val url = "https://earthquake.usgs.gov/fdsnws/event/1/query?format=geojson&latitude=" + loc.latitude +
                        "&longitude=" + loc.longitude + "&maxradiuskm=" + filters.distanceKm +
                        "&minmagnitude=" + filters.minimumMagnitude + "&starttime=" + start + "&orderby=time&limit=50"
                    val body = withContext(Dispatchers.IO) {
                        try {
                            val c = URL(url).openConnection() as HttpURLConnection
                            c.connectTimeout = 10000; c.readTimeout = 10000
                            if (c.responseCode != 200) null else c.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
                        } catch (e: Exception) { null }
                    }
                    val quakes = body?.let { parseUsgsQuakes(it, loc.latitude, loc.longitude, filters.distanceKm.toDouble()) }
                        ?.filter { filters.copy(enabled = true).accepts(it.distanceKm, it.magnitude) }
                    if (quakes == null) {
                        headline = "Unavailable: the USGS feed could not be read. Check your internet connection."
                        bad = true; lines = emptyList()
                    } else {
                        bad = false
                        val where = com.example.data.service.LocationAccess.placeText(loc.locality, loc.district, loc.state).let { place ->
                            val fixAge = if (loc.timestamp > 0) " (location fix from " + fmt.format(Date(loc.timestamp)) + ", source " + loc.source + ")" else ""
                            (if (place == "Unavailable") "within " + filters.distanceKm + " km of " + String.format(Locale.US, "%.2f, %.2f", loc.latitude, loc.longitude) else "within " + filters.distanceKm + " km of " + place) + fixAge
                        }
                        headline = if (quakes.isEmpty()) "No earthquake listed by USGS " + where + " in the last " + QUAKE_DAYS + " days (checked " + fmt.format(Date()) + ")."
                        else quakes.size.toString() + " earthquake(s) listed by USGS (checked " + fmt.format(Date()) + "):"
                        lines = quakes.take(5).map { q -> "M " + q.magnitude + ", " + q.distanceKm.toInt() + " km away, " + fmt.format(Date(q.timeMillis)) + ", " + q.place }
                    }
                }
                busy = false
            }
        }) { Text(if (busy) "Checking..." else "Check now") }
        Text(headline, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = if (bad) BentoRed else BentoGreenPrimary)
        lines.forEach { Text(it, fontSize = 12.sp, color = BentoTextPrimary) }
    }
}

/**
 * Area for official alerts. Two named choices: use a saved area, or use the current location at each check
 * (the default, how it always worked). Saving an area needs a successful foreground location fix; if the fix
 * fails nothing changes. Stored on this phone only.
 */
@Composable
internal fun AlertAreaControls() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var area by remember { mutableStateOf(com.example.data.engine.AlertArea.read(context)) }
    var useSaved by remember { mutableStateOf(com.example.data.engine.AlertArea.useSaved(context)) }
    var busy by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf<String?>(null) }
    val fmt = remember { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()) }
    val now = System.currentTimeMillis()
    fun fixAndSave() {
        busy = true
        scope.launch {
            try {
                val loc = withContext(Dispatchers.IO) { OfficialLocationContextManager(context).refreshLocation() }
                val ok = loc.locationStatus == "LOCATION_AVAILABLE" || loc.locationStatus == "COORDINATES_AVAILABLE" || loc.locationStatus == "GEOCODER_FAILED"
                if (!ok || !com.example.data.engine.AlertArea.plausible(loc.latitude, loc.longitude)) {
                    note = "Unavailable: area not saved, nothing changed. " + com.example.data.service.LocationAccess.failureText(loc.locationStatus, true)
                } else {
                    val a = com.example.data.engine.SavedAlertArea(loc.latitude, loc.longitude,
                        loc.locality.takeUnless { it.contains("unavailable", true) } ?: "",
                        loc.district.takeUnless { it.contains("unavailable", true) } ?: "",
                        loc.state.takeUnless { it.contains("unavailable", true) } ?: "", System.currentTimeMillis())
                    com.example.data.engine.AlertArea.save(context, a)
                    area = a; useSaved = true
                    note = "Area saved: " + com.example.data.engine.AlertArea.placeText(a) + ". Automatic checks now use it."
                }
            } catch (e: Exception) {
                note = "Unavailable: area not saved, nothing changed (" + (e.message ?: "error") + ")."
            } finally {
                busy = false
            }
        }
    }
    Text("Alert area", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = BentoTextPrimary)
    Text(com.example.data.engine.AlertArea.modeText(area, useSaved), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = BentoTextPrimary)
    Text(
        area?.let { "Saved area: " + com.example.data.engine.AlertArea.placeText(it) + " (saved " + fmt.format(Date(it.savedAtMillis)) + "). " }.orEmpty() +
            "A saved area stays on this phone only and checks do not send it anywhere. Naming the place uses Android's location service when you save. The manual Check now button sends your coordinates to USGS whichever choice is on. Changing the choice or the area starts alert tracking fresh.",
        fontSize = 11.sp, color = BentoTextSecondary
    )
    area?.let { a -> com.example.data.engine.AlertArea.staleNote(a, now, useSaved)?.let { Text(it, fontSize = 11.sp, color = BentoRed) } }
    androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(enabled = !busy && !(useSaved && area != null), onClick = {
            if (area != null) {
                com.example.data.engine.AlertArea.setUseSaved(context, true); useSaved = true
                note = "Automatic checks now use your saved area."
            } else fixAndSave()
        }) { Text(if (busy) "Saving..." else if (area == null) "Save my area" else "Use saved area", fontSize = 12.sp) }
        Button(enabled = !busy && useSaved, onClick = {
            com.example.data.engine.AlertArea.setUseSaved(context, false); useSaved = false
            note = "Automatic checks now read your current location at each check. Your saved area is kept but not used."
        }) { Text("Use current location each check", fontSize = 12.sp) }
    }
    androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (area != null) Button(enabled = !busy, onClick = { fixAndSave() }) { Text("Update area", fontSize = 12.sp) }
        if (area != null) Button(enabled = !busy, onClick = {
            com.example.data.engine.AlertArea.clear(context); area = null; useSaved = false
            note = "Saved area cleared. Automatic checks read your current location at each check."
        }) { Text("Clear area", fontSize = 12.sp) }
    }
    note?.let { Text(it, fontSize = 11.sp, color = BentoTextSecondary) }
}
