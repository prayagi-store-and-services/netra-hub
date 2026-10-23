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
 * (Doze and battery saver can delay it). It asks the USGS public feed for earthquakes matching the device-local magnitude filter
 * within the user-selected radius of the last position the phone knows, and notifies once per new quake from the last 24 hours.
 * It never invents a position: with no recent location it records "Unavailable" and sends nothing.
 */
class QuakeWatchWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val ctx = applicationContext
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val areaVer = AlertArea.version(ctx)
        fun gsave(msg: String) { AlertArea.commitIfCurrent(ctx, areaVer) { save(prefs, now, msg) } }
        try {
            val filters = QuakeAlertSettings.read(ctx)
            if (!filters.enabled) { gsave("Off: earthquake notifications disabled on this phone."); return Result.success() }
            val loc = AlertArea.asLocation(ctx) ?: OfficialLocationContextManager(ctx).refreshLocation()
            val ok = loc.locationStatus == "LOCATION_AVAILABLE" || loc.locationStatus == "COORDINATES_AVAILABLE" || loc.locationStatus == "GEOCODER_FAILED"
            if (!ok || (loc.latitude == 0.0 && loc.longitude == 0.0)) {
                gsave("Unavailable: " + com.example.data.service.LocationAccess.failureText(loc.locationStatus, com.example.data.service.LocationAccess.backgroundGranted(ctx)))
                return Result.success()
            }
            val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
            val start = fmt.format(Date(now - 2 * 86_400_000L))
            // Global last-day summary feed: no coordinates leave the phone; distance is filtered here.
            val url = AlertArea.usgsDayFeedUrl(filters.minimumMagnitude)
            val body = try {
                val c = URL(url).openConnection() as HttpURLConnection
                c.connectTimeout = 10000; c.readTimeout = 10000
                if (c.responseCode != 200) null else c.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
            } catch (e: Exception) { null }
            val quakes = body?.let { parseUsgsQuakes(it, loc.latitude, loc.longitude, filters.distanceKm.toDouble()) }
            if (quakes == null) {
                gsave("Unavailable: the USGS feed could not be read.")
                return Result.success()
            }
            val committed = AlertArea.commitIfCurrent(ctx, areaVer) {
                val seen = prefs.getStringSet(AlertArea.scoped(KEY_SEEN, areaVer), emptySet()) ?: emptySet()
                // Respect an off/filter change made while the request was in flight.
                val currentFilters = QuakeAlertSettings.read(ctx)
                if (!currentFilters.enabled) { gsave("Off: earthquake notifications disabled on this phone."); return Result.success() }
                if (currentFilters != filters) {
                    gsave("Filters changed during this check. A new check is needed; no result was used.")
                    return Result.retry()
                }
                val fresh = selectNewQuakes(quakes, seen, now, 24 * 3_600_000L)
                    .filter { currentFilters.accepts(it.distanceKm, it.magnitude) }
                if (fresh.isEmpty()) {
                    gsave("OK: no new earthquake matching M${currentFilters.minimumMagnitude}+ within ${currentFilters.distanceKm} km in the last 24 hours.")
                } else if (!NotificationManagerCompat.from(ctx).areNotificationsEnabled()) {
                    // Do not mark as seen: notify once notifications are allowed.
                    gsave("Unavailable: notifications are off, " + fresh.size + " new earthquake(s) could not be shown.")
                } else {
                    val top = fresh.maxByOrNull { it.magnitude }!!
                    val tf = SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault())
                    OfficialAlertAnnouncer.announce(ctx, "quake", "Earthquake magnitude " + top.magnitude, top.place, top.distanceKm.toInt().toString() + " km from your area")
                    NetraNotificationManager(ctx).sendEmergencyAlert(
                        title = "Earthquake M" + top.magnitude + " near you (USGS)",
                        message = top.distanceKm.toInt().toString() + " km away, " + tf.format(Date(top.timeMillis)) + ", " + top.place +
                            (if (fresh.size > 1) " (+" + (fresh.size - 1) + " more)" else "") + ". USGS data, not an official warning.",
                        riskLevel = SafetyRiskLevel.ATTENTION
                    )
                    val updated = (seen + fresh.map { it.id }).toList().takeLast(200).toSet()
                    prefs.edit().putStringSet(AlertArea.scoped(KEY_SEEN, areaVer), updated).apply()
                    gsave("Notified: " + fresh.size + " new earthquake(s), strongest M" + top.magnitude + ".")
                }
            }
            if (!committed) return Result.retry()
            return Result.success()
        } catch (e: Exception) {
            gsave("Unavailable: check failed.")
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

        /** One immediate check (e.g. fresh location fix or permission just granted). Android may still delay it without a network. */
        fun enqueueNow(context: Context) {
            if (!QuakeAlertSettings.read(context).enabled) return
            val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
            val request = OneTimeWorkRequestBuilder<QuakeWatchWorker>()
                .setConstraints(constraints)
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork("QuakeWatchNow", ExistingWorkPolicy.KEEP, request)
        }

        fun schedule(context: Context) {
            if (!QuakeAlertSettings.read(context).enabled) {
                WorkManager.getInstance(context).cancelUniqueWork("QuakeWatchPeriodicWork")
                return
            }
            val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
            val request = PeriodicWorkRequestBuilder<QuakeWatchWorker>(30, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork("QuakeWatchPeriodicWork", ExistingPeriodicWorkPolicy.UPDATE, request)
        }
    }
}
