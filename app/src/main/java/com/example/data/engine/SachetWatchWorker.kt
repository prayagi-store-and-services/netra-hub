package com.example.data.engine

import android.content.Context
import androidx.core.app.NotificationManagerCompat
import androidx.work.*
import com.example.data.model.SafetyRiskLevel
import com.example.data.service.OfficialLocationContextManager
import com.example.util.NetraNotificationManager
import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.xml.parsers.DocumentBuilderFactory

/** One alert read from the NDMA SACHET (CAP) public feed. */
data class CapAlert(
    val id: String, val sender: String, val event: String, val severity: String,
    val headline: String, val areaDesc: String, val expiresMillis: Long
)

internal const val SACHET_RSS = "https://sachet.ndma.gov.in/cap_public_website/rss/rss_india.xml"

/** RSS item links (CAP XML addresses), keyed by guid. Null if the feed cannot be parsed. */
internal fun parseSachetRss(xml: String): List<Pair<String, String>>? = try {
    val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(ByteArrayInputStream(xml.toByteArray(Charsets.UTF_8)))
    val items = doc.getElementsByTagName("item")
    (0 until items.length).mapNotNull {
        val e = items.item(it) as Element
        val g = e.getElementsByTagName("guid").item(0)?.textContent?.trim()
        val l = e.getElementsByTagName("link").item(0)?.textContent?.trim()
        if (g.isNullOrEmpty() || l.isNullOrEmpty()) null else g to l
    }
} catch (e: Exception) { null }

private fun parseCapTime(s: String): Long = try {
    SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US).parse(s.trim())?.time ?: 0L
} catch (e: Exception) { 0L }

/** Reads the first English "info" block of a CAP 1.2 alert. Null if it cannot be read. */
internal fun parseCapAlert(xml: String): CapAlert? = try {
    val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(ByteArrayInputStream(xml.toByteArray(Charsets.UTF_8)))
    val root = doc.documentElement
    fun first(el: Element, tag: String) = el.getElementsByTagName(tag).item(0)?.textContent?.trim() ?: ""
    val infos = root.getElementsByTagName("cap:info")
    var info: Element? = null
    for (i in 0 until infos.length) { val e = infos.item(i) as Element; if (first(e, "cap:language").startsWith("en")) { info = e; break } }
    if (info == null && infos.length > 0) info = infos.item(0) as Element
    if (first(root, "cap:status") != "Actual" || info == null) null
    else CapAlert(
        id = first(root, "cap:identifier"), sender = first(root, "cap:sender"), event = first(info, "cap:event"),
        severity = first(info, "cap:severity"), headline = first(info, "cap:headline"),
        areaDesc = first(info, "cap:areaDesc"), expiresMillis = parseCapTime(first(info, "cap:expires"))
    )
} catch (e: Exception) { null }

private fun norm(s: String) = s.lowercase(Locale.ROOT).replace("-", " ").replace("_", " ").trim()

/**
 * Text match only (the feed gives area names, not coordinates): the alert must be unexpired, Severe or Extreme,
 * and its area text must contain the phone's district or locality, or the issuing state body must name the phone's state.
 */
internal fun capMatchesPlace(a: CapAlert, district: String, locality: String, state: String, nowMillis: Long): Boolean {
    if (a.expiresMillis != 0L && a.expiresMillis < nowMillis) return false
    if (a.severity != "Severe" && a.severity != "Extreme") return false
    val area = norm(a.areaDesc)
    fun known(v: String) = v.isNotBlank() && !v.contains("unavailable", true)
    if (known(district) && area.contains(norm(district))) return true
    if (known(locality) && area.contains(norm(locality))) return true
    if (known(state) && (area.contains(norm(state)) || norm(a.sender).contains(norm(state)))) return true
    return false
}

/**
 * Automatic official alerts from NDMA SACHET, about every 30 minutes when the phone has a network (Android may delay it).
 * Matching is by area name only, never by coordinates, and the notification shows the alert's own area text.
 */
class SachetWatchWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    private fun get(url: String): String? = try {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 10000; c.readTimeout = 15000
        if (c.responseCode != 200) null else c.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
    } catch (e: Exception) { null }

    override suspend fun doWork(): Result {
        val ctx = applicationContext
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        fun save(msg: String) { prefs.edit().putLong(KEY_LAST, now).putString(KEY_MSG, msg).apply() }
        try {
            val loc = OfficialLocationContextManager(ctx).refreshLocation()
            val ok = loc.locationStatus == "LOCATION_AVAILABLE" || loc.locationStatus == "COORDINATES_AVAILABLE" || loc.locationStatus == "GEOCODER_FAILED"
            if (!ok || (loc.state.contains("unavailable", true) && loc.district.contains("unavailable", true))) {
                save("Unavailable: no recent phone location with a state or district name (" + loc.locationStatus + ").")
                return Result.success()
            }
            val rss = get(SACHET_RSS)?.let { parseSachetRss(it) }
            if (rss == null) { save("Unavailable: the NDMA SACHET feed could not be read."); return Result.success() }
            val seen = prefs.getStringSet(KEY_SEEN, emptySet()) ?: emptySet()
            val fresh = rss.filter { it.first !in seen }.take(40)
            val matched = mutableListOf<CapAlert>()
            val checked = mutableListOf<String>()
            for ((guid, link) in fresh) {
                val cap = get(link)?.let { parseCapAlert(it) } ?: continue
                checked.add(guid)
                if (capMatchesPlace(cap, loc.district, loc.locality, loc.state, now)) matched.add(cap)
            }
            if (matched.isNotEmpty() && !NotificationManagerCompat.from(ctx).areNotificationsEnabled()) {
                save("Unavailable: notifications are off, " + matched.size + " matching official alert(s) could not be shown.")
                return Result.success()
            }
            if (matched.isNotEmpty()) {
                val top = matched.first()
                NetraNotificationManager(ctx).sendEmergencyAlert(
                    title = top.event + " (NDMA SACHET, official)",
                    message = top.headline + " Area in the alert: " + top.areaDesc + ". Issued by " + top.sender +
                        (if (matched.size > 1) " (+" + (matched.size - 1) + " more)" else "") + ". Matched by area name to your place.",
                    riskLevel = SafetyRiskLevel.WARNING
                )
            }
            val updated = (seen + checked).toList().takeLast(400).toSet()
            prefs.edit().putStringSet(KEY_SEEN, updated).apply()
            save(if (matched.isEmpty()) "OK: " + checked.size + " new official alert(s) read, none matched by area name to " + loc.state + "."
                 else "Notified: " + matched.size + " matching official alert(s).")
            return Result.success()
        } catch (e: Exception) {
            save("Unavailable: check failed.")
            return Result.retry()
        }
    }

    companion object {
        const val PREFS = "sachet_watch"
        const val KEY_SEEN = "seen_ids"
        const val KEY_LAST = "last_check"
        const val KEY_MSG = "last_message"

        fun schedule(context: Context) {
            val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
            val request = PeriodicWorkRequestBuilder<SachetWatchWorker>(30, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork("SachetWatchPeriodicWork", ExistingPeriodicWorkPolicy.UPDATE, request)
        }
    }
}
