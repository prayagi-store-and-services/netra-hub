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

/** One alert read from the NDMA SACHET (CAP) public feed. Geometry (polygon/circle) is read when the alert carries it. */
data class CapAlert(
    val id: String, val sender: String, val event: String, val severity: String,
    val headline: String, val areaDesc: String, val expiresMillis: Long,
    val polygon: List<Pair<Double, Double>>? = null,
    val circleKm: Triple<Double, Double, Double>? = null,
    val categories: Set<String> = emptySet()
)

internal const val SACHET_RSS = "https://sachet.ndma.gov.in/cap_public_website/rss/rss_india.xml"

/** Reject DTD declarations before parsing, so no external or recursive entity can be read.
 * The resolver is a second guard. Optional factory flags vary between Android XML providers.
 */
internal fun parseOfficialXml(xml: String): org.w3c.dom.Document {
    require(!Regex("<!\\s*(?:DOCTYPE|ENTITY)\\b", RegexOption.IGNORE_CASE).containsMatchIn(xml)) { "DTD declarations are not allowed" }
    val factory = DocumentBuilderFactory.newInstance()
    factory.isExpandEntityReferences = false
    for ((feature, enabled) in listOf(
        "http://apache.org/xml/features/disallow-doctype-decl" to true,
        "http://xml.org/sax/features/external-general-entities" to false,
        "http://xml.org/sax/features/external-parameter-entities" to false,
        "http://apache.org/xml/features/nonvalidating/load-external-dtd" to false
    )) {
        try { factory.setFeature(feature, enabled) } catch (_: javax.xml.parsers.ParserConfigurationException) {
            // The declaration rejection above and resolver below do not depend on provider features.
        }
    }
    val builder = factory.newDocumentBuilder()
    builder.setEntityResolver { _, _ -> throw org.xml.sax.SAXException("External entities are not allowed") }
    return builder.parse(ByteArrayInputStream(xml.toByteArray(Charsets.UTF_8)))
}

/** RSS item links (CAP XML addresses), keyed by guid. Null if the feed cannot be parsed. */
internal fun parseSachetRss(xml: String): List<Pair<String, String>>? = try {
    val doc = parseOfficialXml(xml)
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
    val doc = parseOfficialXml(xml)
    val root = doc.documentElement
    fun first(el: Element, tag: String) = el.getElementsByTagName(tag).item(0)?.textContent?.trim() ?: ""
    val infos = root.getElementsByTagName("cap:info")
    var info: Element? = null
    for (i in 0 until infos.length) { val e = infos.item(i) as Element; if (first(e, "cap:language").startsWith("en")) { info = e; break } }
    if (info == null && infos.length > 0) info = infos.item(0) as Element
    if (first(root, "cap:status") != "Actual" || info == null) null
    else {
        val poly = first(info, "cap:polygon").trim().split(Regex("\\s+")).mapNotNull { pt ->
            val ab = pt.split(",")
            if (ab.size == 2) (ab[0].toDoubleOrNull() ?: return@mapNotNull null) to (ab[1].toDoubleOrNull() ?: return@mapNotNull null) else null
        }
        val circ = first(info, "cap:circle").trim().split(Regex("\\s+")).let { parts ->
            if (parts.size == 2) {
                val ab = parts[0].split(",")
                val r = parts[1].toDoubleOrNull()
                if (ab.size == 2 && r != null && r > 0) Triple(ab[0].toDoubleOrNull() ?: 0.0, ab[1].toDoubleOrNull() ?: 0.0, r) else null
            } else null
        }
        CapAlert(
            id = first(root, "cap:identifier"), sender = first(root, "cap:sender"), event = first(info, "cap:event"),
            severity = first(info, "cap:severity"), headline = first(info, "cap:headline"),
            areaDesc = first(info, "cap:areaDesc"), expiresMillis = parseCapTime(first(info, "cap:expires")),
            polygon = if (poly.size >= 3) poly else null, circleKm = circ, categories = (0 until info.getElementsByTagName("cap:category").length).map { info.getElementsByTagName("cap:category").item(it).textContent.trim() }.toSet()
        )
    }
} catch (e: Exception) { null }

private fun norm(s: String) = s.lowercase(Locale.ROOT).replace("-", " ").replace("_", " ").trim()

/** Whole-word match: the area text names the place as its own word(s), never a substring of a longer name. */
internal fun wordMatch(areaNorm: String, termNorm: String): Boolean =
    termNorm.isNotBlank() && Regex("(^|[^a-z0-9])" + Regex.escape(termNorm) + "([^a-z0-9]|$)").containsMatchIn(areaNorm)

/** Standard ray-casting point-in-polygon on (lat, lon) pairs. */
internal fun pointInPolygon(lat: Double, lon: Double, poly: List<Pair<Double, Double>>): Boolean {
    var inside = false
    var j = poly.size - 1
    for (i in poly.indices) {
        val (ilat, ilon) = poly[i]
        val (jlat, jlon) = poly[j]
        if ((ilon > lon) != (jlon > lon) && lat < (jlat - ilat) * (lon - ilon) / (jlon - ilon) + ilat) inside = !inside
        j = i
    }
    return inside
}

internal fun haversineKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val r = 6371.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val h = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
        Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.sin(dLon / 2) * Math.sin(dLon / 2)
    return 2 * r * Math.asin(Math.sqrt(h))
}

internal fun plausibleCoords(lat: Double?, lon: Double?): Boolean =
    lat != null && lon != null && lat in -90.0..90.0 && lon in -180.0..180.0 && !(lat == 0.0 && lon == 0.0)

/**
 * Truth-only matching (owner rule, 19 Oct 2026): alert only for the phone's own area.
 * 1) When the alert carries a polygon or circle and the phone has coordinates, the phone must lie
 *    inside that area - the alert's own geometry decides, nothing else.
 * 2) Otherwise the alert's area text must name the phone's district, locality, or state as a whole
 *    word - never a substring of a longer place name.
 * The issuing body's name never decides: a state body issuing a warning for one far district used to
 * alert every phone in the state (the 19 Oct false alert ~70-80 km away came from that).
 * No verifiable match means no alert.
 */
internal fun capMatchesPlace(
    a: CapAlert, district: String, locality: String, state: String,
    latitude: Double? = null, longitude: Double? = null, nowMillis: Long
): Boolean {
    if (a.expiresMillis != 0L && a.expiresMillis < nowMillis) return false
    if (a.severity !in setOf("Minor", "Moderate", "Severe", "Extreme", "Unknown")) return false
    if (plausibleCoords(latitude, longitude)) {
        a.polygon?.let { return pointInPolygon(latitude!!, longitude!!, it) }
        a.circleKm?.let { return haversineKm(latitude!!, longitude!!, it.first, it.second) <= it.third }
    }
    val area = norm(a.areaDesc)
    if (area.isBlank()) return false
    fun known(v: String) = v.isNotBlank() && !v.contains("unavailable", true)
    if (known(district) && wordMatch(area, norm(district))) return true
    if (known(locality) && wordMatch(area, norm(locality))) return true
    if (known(state) && wordMatch(area, norm(state))) return true
    return false
}

/** District nowcast-style status: names the place, the source, and what was read. Never invents a warning. */
internal fun noWarningStatus(district: String, locality: String, state: String, readCount: Int, matchedCount: Int, activeCount: Int = 0): String {
    fun known(v: String) = v.isNotBlank() && !v.contains("unavailable", true)
    val place = when {
        known(district) -> district
        known(locality) -> locality
        known(state) -> state
        else -> "your area"
    }
    return if (matchedCount > 0)
        "Matching official warning(s): " + matchedCount + " for " + place + ". Notification/TTS delivery depends on phone settings. Source: NDMA SACHET (includes IMD)."
    else if (activeCount > 0)
        "Previously matched warning(s) still within their stated validity: " + activeCount + " for " + place + ". No new matching warning in this batch. Source: NDMA SACHET (includes IMD)."
    else
        "No matching warning found in this checked batch for " + place + ". Source: NDMA SACHET (includes IMD); " + readCount + " alert(s) checked. This is not an IMD all-clear or a complete nowcast."

}

/**
 * Automatic official alerts from NDMA SACHET, about every 30 minutes when the phone has a network (Android may delay it).
 * Matching uses the alert's own polygon/circle when the phone has coordinates, otherwise exact whole-word
 * district/locality/state names only; the notification shows the alert's own area text. Each alert speaks
 * and notifies once; again only when its severity changes or the warning is over (see SachetAlertState).
 */
class SachetWatchWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    private fun get(url: String): String? {
        // Only the fixed official datasets or their same-origin CAP links are read.
        val u = runCatching { java.net.URI(url) }.getOrNull() ?: return null
        if (u.scheme != "https" || u.host !in setOf("sachet.ndma.gov.in", "reactjs.imd.gov.in")) return null
        var c: HttpURLConnection? = null
        return try {
            c = URL(url).openConnection() as HttpURLConnection
            c.connectTimeout = 10000; c.readTimeout = 15000; c.instanceFollowRedirects = false
            if (c.responseCode != 200 || c.contentLengthLong > MAX_OFFICIAL_BYTES) null else c.inputStream.use { boundedOfficialText(it) }
        } catch (_: Exception) { null } finally { c?.disconnect() }
    }

    override suspend fun doWork(): Result {
        val ctx = applicationContext
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val areaVer = AlertArea.version(ctx)
        fun save(msg: String) { AlertArea.commitIfCurrent(ctx, areaVer) { prefs.edit().putLong(KEY_LAST, now).putString(KEY_MSG, msg).apply() } }
        try {
            val loc = AlertArea.asLocation(ctx) ?: OfficialLocationContextManager(ctx).refreshLocation()
            val ok = loc.locationStatus in setOf("LOCATION_AVAILABLE", "COORDINATES_AVAILABLE", "GEOCODER_FAILED")
            if (!ok) {
                AlertArea.commitIfCurrent(ctx, areaVer) { OfficialWeatherReports.clear(ctx) }
                save("Unavailable: current alert location could not be verified.")
                return Result.success()
            }
            val imd = if (plausibleCoords(loc.latitude,loc.longitude)) runCatching { get(IMD_WARNING_URL)?.let { parseImdWarning(it,loc.latitude,loc.longitude,now) } }.getOrNull() else null
            val rss = get(SACHET_RSS)?.let { parseSachetRss(it) }
            val cursorKey = AlertArea.scoped("cap_cursor_v3",areaVer)
            val cursor = prefs.getInt(cursorKey,0)
            val batch = officialBatch(rss?.size ?: 0,cursor)
            val matched = mutableListOf<CapAlert>()
            var checked = 0
            for (index in batch) {
                val cap = get(rss!![index].second)?.let { parseCapAlert(it) } ?: continue
                checked++
                if (capMatchesPlace(cap,loc.district,loc.locality,loc.state,loc.latitude,loc.longitude,now)) matched.add(cap)
            }
            val committed = AlertArea.commitIfCurrent(ctx,areaVer) {
                // Cursor advances across failures and blocked outputs. Each entry is revisited, never permanently seen.
                prefs.edit().putInt(cursorKey, if (rss.isNullOrEmpty()) 0 else (cursor + batch.size) % rss.size).apply()
                val messages = mutableListOf<String>()
                messages.add(imd?.text() ?: "IMD subdivision report unavailable. Not an all-clear.")
                messages.add(if (rss == null) "NDMA SACHET feed unavailable. Not an all-clear." else noWarningStatus(loc.district,loc.locality,loc.state,checked,matched.size))
                matched.forEach { messages.add(it.event + ": " + it.severity + ". " + it.headline.take(1000) + ". Area: " + it.areaDesc.take(500) + ". Valid until: " + (if (it.expiresMillis > 0) java.util.Date(it.expiresMillis).toString() else "not supplied")) }
                val notifications = NetraNotificationManager(ctx)
                fun deliver(identity: String, token: String, audible: Boolean, event: String, body: String, area: String) {
                    val key = AlertArea.scoped("official_output_v3_" + identity,areaVer)
                    val due = officialOutputsDue(token,prefs.getString(key + "_n",null),prefs.getString(key + "_s",null),audible)
                    if (due.notification && notifications.sendEmergencyAlert(event,body,SafetyRiskLevel.WARNING)) prefs.edit().putString(key + "_n",token).apply()
                    if (due.speech && OfficialAlertAnnouncer.deliveryLimit(ctx) == null) {
                        OfficialAlertAnnouncer.speakNotice(ctx,OfficialAlertAnnouncer.announcementText("sachet",event,body,area),"official_" + identity.hashCode()) { success, _ ->
                            if (success) AlertArea.commitIfCurrent(ctx,areaVer) { prefs.edit().putString(key + "_s",token).apply() }
                        }
                    }
                    if (due.notification && !notifications.canPostEmergency()) messages.add("Visual warning blocked by notification settings.")
                    if (due.speech) OfficialAlertAnnouncer.deliveryLimit(ctx)?.let { messages.add("Speech blocked: " + it) }
                }
                if (imd != null) {
                    if (imd.audible) deliver("imd_" + imd.area,imd.colour,true,"IMD " + imd.colour + " warning",imd.hazards,imd.area)
                    else {
                        val key = AlertArea.scoped("official_output_v3_imd_" + imd.area,areaVer)
                        prefs.edit().putString(key + "_n",imd.colour).putString(key + "_s",imd.colour).apply()
                    }
                }
                // Weather speech/notification uses verified IMD colour once, not a second CAP interpretation.
                // Non-weather Severe/Extreme CAP alerts retain independent per-alert delivery.
                matched.filter { !capIsWeather(it) && it.severity in setOf("Severe","Extreme") }.forEach { cap ->
                    deliver("cap_" + SachetAlertState.identityKey(cap.event,cap.areaDesc),cap.severity,true,cap.event + " (NDMA SACHET)",cap.headline,cap.areaDesc)
                }
                if (imd == null && matched.isEmpty()) OfficialWeatherReports.clear(ctx)
                else {
                    val earliestExpiry = matched.map { it.expiresMillis }.filter { it > now }.minOrNull() ?: (now + 45 * 60_000L)
                    OfficialWeatherReports.save(ctx,OfficialReportSnapshot(areaVer,now,minOf(now + 45 * 60_000L,earliestExpiry), listOf(loc.district,loc.locality,loc.state).filter { it.isNotBlank() }.distinct().joinToString(", "),messages.joinToString("\n")))
                }
                save(messages.take(2).joinToString("\n"))
            }
            return if (committed) Result.success() else Result.retry()
        } catch (_: Exception) {
            AlertArea.commitIfCurrent(ctx,areaVer) { OfficialWeatherReports.clear(ctx) }
            save("Unavailable: official alert check failed. Not an all-clear.")
            return Result.retry()
        }
    }

    companion object {
        const val KEY_CAP_REPORT = "cap_report"
        const val KEY_CAP_LAST = "cap_report_last"
        const val KEY_IMD_REPORT = "imd_report"
        const val KEY_IMD_LAST = "imd_last"
        const val PREFS = "sachet_watch"
        const val KEY_SEEN = "seen_ids"
        const val KEY_ACTIVE = "active_alerts"
        const val KEY_LAST = "last_check"
        const val KEY_MSG = "last_message"

        /** One immediate check (e.g. fresh location fix or permission just granted). Android may still delay it without a network. */
        fun enqueueNow(context: Context) {
            val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
            val request = OneTimeWorkRequestBuilder<SachetWatchWorker>()
                .setConstraints(constraints)
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork("SachetWatchNow", ExistingWorkPolicy.KEEP, request)
        }

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
