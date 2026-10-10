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

private const val QUAKE_RADIUS_KM = 200.0
private const val QUAKE_DAYS = 7
private const val QUAKE_MIN_MAG = 2.5

/**
 * Safety card: earthquakes within about 200 km of the phone in the last 7 days, from the USGS public feed.
 * Also shows the result of the automatic background check (QuakeWatchWorker). The button is a manual backup. Shows Unavailable when location or the feed cannot be read.
 */
@Composable
fun EarthquakeCard() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
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
        Text(
            "Last " + QUAKE_DAYS + " days, within " + QUAKE_RADIUS_KM.toInt() + " km of this phone, magnitude " + QUAKE_MIN_MAG + " and above. Source: USGS (earthquake.usgs.gov). Hub also checks automatically about every 30 minutes and notifies you of a new quake (Android may delay this). Smaller quakes in India may be missing from this feed, and this is not an official warning.",
            fontSize = 12.sp, color = BentoTextSecondary
        )
        Text(
            if (autoLast == 0L || autoMsg == null) "Automatic check: has not run yet on this phone (Android decides when it runs)."
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
                        "&longitude=" + loc.longitude + "&maxradiuskm=" + QUAKE_RADIUS_KM.toInt() +
                        "&minmagnitude=" + QUAKE_MIN_MAG + "&starttime=" + start + "&orderby=time&limit=50"
                    val body = withContext(Dispatchers.IO) {
                        try {
                            val c = URL(url).openConnection() as HttpURLConnection
                            c.connectTimeout = 10000; c.readTimeout = 10000
                            if (c.responseCode != 200) null else c.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
                        } catch (e: Exception) { null }
                    }
                    val quakes = body?.let { parseUsgsQuakes(it, loc.latitude, loc.longitude, QUAKE_RADIUS_KM) }
                    if (quakes == null) {
                        headline = "Unavailable: the USGS feed could not be read. Check your internet connection."
                        bad = true; lines = emptyList()
                    } else {
                        bad = false
                        val where = com.example.data.service.LocationAccess.placeText(loc.locality, loc.district, loc.state).let { place ->
                            val fixAge = if (loc.timestamp > 0) " (location fix from " + fmt.format(Date(loc.timestamp)) + ", source " + loc.source + ")" else ""
                            (if (place == "Unavailable") "within " + QUAKE_RADIUS_KM.toInt() + " km of " + String.format(Locale.US, "%.2f, %.2f", loc.latitude, loc.longitude) else "within " + QUAKE_RADIUS_KM.toInt() + " km of " + place) + fixAge
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
