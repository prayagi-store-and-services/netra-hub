package com.example.data.engine

import android.content.Context
import androidx.core.app.NotificationManagerCompat
import androidx.work.*
import com.example.data.model.SafetyRiskLevel
import com.example.data.service.OfficialLocationContextManager
import com.example.ui.screens.QuakeItem
import com.example.ui.screens.parseUsgsQuakes
import com.example.util.NetraNotificationManager
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

/** Pure helper: quakes not yet notified and not older than maxAgeMillis. */
internal fun selectNewQuakes(quakes: List<QuakeItem>, seenIds: Set<String>, nowMillis: Long, maxAgeMillis: Long): List<QuakeItem> =
    quakes.filter { it.id.isNotEmpty() && it.id !in seenIds && nowMillis - it.timeMillis in 0L..maxAgeMillis }

/**
 * Automatic earthquake watch. Android runs it about every 30 minutes when the phone has a network
 * (Doze and battery saver can delay it). It asks the USGS public feed for earthquakes of magnitude 2.5 or more
 * within 200 km of the last position the phone knows, and notifies once per new quake from the last 24 hours.
 * It never invents a position: with no recent location it records "Unavailable" and sends nothing.
 */
class QuakeWatchWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val ctx = applicationContext
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        try {
            val loc = OfficialLocationContextManager(ctx).refreshLocation()
            val ok = loc.locationStatus == "LOCATION_AVAILABLE" || loc.locationStatus == "COORDINATES_AVAILABLE" || loc.locationStatus == "GEOCODER_FAILED"
            if (!ok || (loc.latitude == 0.0 && loc.longitude == 0.0)) {
                save(prefs, now, "Unavailable: no recent phone location (" + loc.locationStatus + ").")
                return Result.success()
            }
            val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
            val start = fmt.format(Date(now - 2 * 86_400_000L))
            val url = "https://earthquake.usgs.gov/fdsnws/event/1/query?format=geojson&latitude=" + loc.latitude +
                "&longitude=" + loc.longitude + "&maxradiuskm=200&minmagnitude=2.5&starttime=" + start + "&orderby=time&limit=50"
            val body = try {
                val c = URL(url).openConnection() as HttpURLConnection
                c.connectTimeout = 10000; c.readTimeout = 10000
                if (c.responseCode != 200) null else c.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
            } catch (e: Exception) { null }
            val quakes = body?.let { parseUsgsQuakes(it, loc.latitude, loc.longitude, 200.0) }
            if (quakes == null) {
                save(prefs, now, "Unavailable: the USGS feed could not be read.")
                return Result.success()
            }
            val seen = prefs.getStringSet(KEY_SEEN, emptySet()) ?: emptySet()
            val fresh = selectNewQuakes(quakes, seen, now, 24 * 3_600_000L)
            if (fresh.isEmpty()) {
                save(prefs, now, "OK: no new earthquake of M2.5 or more within 200 km in the last 24 hours.")
            } else if (!NotificationManagerCompat.from(ctx).areNotificationsEnabled()) {
                // Do not mark as seen: notify once notifications are allowed.
                save(prefs, now, "Unavailable: notifications are off, " + fresh.size + " new earthquake(s) could not be shown.")
            } else {
                val top = fresh.maxByOrNull { it.magnitude }!!
                val tf = SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault())
                NetraNotificationManager(ctx).sendEmergencyAlert(
                    title = "Earthquake M" + top.magnitude + " near you (USGS)",
                    message = top.distanceKm.toInt().toString() + " km away, " + tf.format(Date(top.timeMillis)) + ", " + top.place +
                        (if (fresh.size > 1) " (+" + (fresh.size - 1) + " more)" else "") + ". USGS data, not an official warning.",
                    riskLevel = SafetyRiskLevel.ATTENTION
                )
                val updated = (seen + fresh.map { it.id }).toList().takeLast(200).toSet()
                prefs.edit().putStringSet(KEY_SEEN, updated).apply()
                save(prefs, now, "Notified: " + fresh.size + " new earthquake(s), strongest M" + top.magnitude + ".")
            }
            return Result.success()
        } catch (e: Exception) {
            save(prefs, now, "Unavailable: check failed.")
            return Result.retry()
        }
    }

    private fun save(prefs: android.content.SharedPreferences, now: Long, msg: String) {
        prefs.edit().putLong(KEY_LAST, now).putString(KEY_MSG, msg).apply()
    }

    companion object {
        const val PREFS = "quake_watch"
        const val KEY_SEEN = "seen_ids"
        const val KEY_LAST = "last_check"
        const val KEY_MSG = "last_message"

        fun schedule(context: Context) {
            val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
            val request = PeriodicWorkRequestBuilder<QuakeWatchWorker>(30, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork("QuakeWatchPeriodicWork", ExistingPeriodicWorkPolicy.UPDATE, request)
        }
    }
}
