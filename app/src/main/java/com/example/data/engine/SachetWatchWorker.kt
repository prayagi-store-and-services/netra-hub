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
    val circleKm: Triple<Double, Double, Double>? = null
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
            polygon = if (poly.size >= 3) poly else null, circleKm = circ
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
    if (a.severity != "Severe" && a.severity != "Extreme") return false
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
internal fun noWarningStatus(district: String, locality: String, state: String, readCount: Int, matchedCount: Int): String {
    fun known(v: String) = v.isNotBlank() && !v.contains("unavailable", true)
    val place = when {
        known(district) -> district
        known(locality) -> locality
        known(state) -> state
        else -> "your area"
    }
    return if (matchedCount == 0)
        "No official warning for " + place + " right now. Source: NDMA SACHET, which carries IMD warnings; " + readCount + " new alert(s) read, none named your area."
    else
        "Notified: " + matchedCount + " matching official alert(s) for " + place + ". Source: NDMA SACHET, which carries IMD warnings."
}

/**
 * Automatic official alerts from NDMA SACHET, about every 30 minutes when the phone has a network (Android may delay it).
 * Matching uses the alert's own polygon/circle when the phone has coordinates, otherwise exact whole-word
 * district/locality/state names only; the notification shows the alert's own area text. Each alert speaks
 * and notifies once; again only when its severity changes or the warning is over (see SachetAlertState).
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
            val areaVer = AlertArea.version(ctx)
            val loc = AlertArea.asLocation(ctx) ?: OfficialLocationContextManager(ctx).refreshLocation()
            val ok = loc.locationStatus == "LOCATION_AVAILABLE" || loc.locationStatus == "COORDINATES_AVAILABLE" || loc.locationStatus == "GEOCODER_FAILED"
            if (!ok || (!plausibleCoords(loc.latitude, loc.longitude) && loc.state.contains("unavailable", true) && loc.district.contains("unavailable", true))) {
                save("Unavailable: " + com.example.data.service.LocationAccess.failureText(loc.locationStatus, com.example.data.service.LocationAccess.backgroundGranted(ctx)).let { if (loc.locationStatus == "LOCATION_TIMEOUT" || loc.locationStatus == "PERMISSION_NOT_GRANTED" || loc.locationStatus == "PROVIDER_DISABLED") it else "No state or district name for your location, so area alerts cannot be matched." })
                return Result.success()
            }
            val rss = get(SACHET_RSS)?.let { parseSachetRss(it) }
            if (rss == null) { save("Unavailable: the NDMA SACHET feed could not be read."); return Result.success() }
            val seen = prefs.getStringSet(AlertArea.scoped(KEY_SEEN, areaVer), emptySet()) ?: emptySet()
            val fresh = rss.filter { it.first !in seen }.take(40)
            val lat = if (plausibleCoords(loc.latitude, loc.longitude)) loc.latitude else null
            val lon = if (plausibleCoords(loc.latitude, loc.longitude)) loc.longitude else null
            val matched = mutableListOf<CapAlert>()
            val checked = mutableListOf<String>()
            for ((guid, link) in fresh) {
                val cap = get(link)?.let { parseCapAlert(it) } ?: continue
                checked.add(guid)
                if (capMatchesPlace(cap, loc.district, loc.locality, loc.state, lat, lon, now)) matched.add(cap)
            }
            if (matched.isNotEmpty() && !NotificationManagerCompat.from(ctx).areNotificationsEnabled()) {
                save("Unavailable: notifications are off, " + matched.size + " matching official alert(s) could not be shown.")
                return Result.success()
            }
            // The saved area changed while the feed was being read: this result belongs to the old area.
            if (AlertArea.changedSince(areaVer, AlertArea.version(ctx))) return Result.retry()
            val active = SachetAlertState.decode(prefs.getString(AlertArea.scoped(KEY_ACTIVE, areaVer), "") ?: "").toMutableMap()
            // Speak/notify once per alert; again only on a severity change. Same severity stays silent.
            val toAnnounce = mutableListOf<Pair<CapAlert, SachetDecision>>()
            for (cap in matched) {
                val key = SachetAlertState.identityKey(cap.event, cap.areaDesc)
                val decision = SachetAlertState.decide(active[key], cap.severity)
                active[key] = SachetAlertEntry(cap.severity, cap.expiresMillis)
                if (decision != SachetDecision.SAME_SEVERITY_SKIP) toAnnounce.add(cap to decision)
            }
            if (toAnnounce.isNotEmpty()) {
                val (top, decision) = toAnnounce.first()
                val headline = if (decision == SachetDecision.SEVERITY_CHANGED)
                    "Warning updated. Severity is now " + top.severity + ". " + top.headline else top.headline
                OfficialAlertAnnouncer.announce(ctx, "sachet", top.event, headline, top.areaDesc)
                NetraNotificationManager(ctx).sendEmergencyAlert(
                    title = top.event + " (NDMA SACHET, official)" + (if (decision == SachetDecision.SEVERITY_CHANGED) " - updated" else ""),
                    message = headline + " Area in the alert: " + top.areaDesc + ". Issued by " + top.sender +
                        (if (toAnnounce.size > 1) " (+" + (toAnnounce.size - 1) + " more)" else "") +
                        ". Matched by the alert's own area map or your exact district name.",
                    riskLevel = SafetyRiskLevel.WARNING
                )
            }
            // Warnings that have passed their expiry are over: say so once, then forget them.
            val over = SachetAlertState.expiredKeys(active, now)
            if (over.isNotEmpty()) {
                val first = over.first()
                OfficialAlertAnnouncer.announce(ctx, "sachet", "Weather warning over",
                    "The earlier official warning has ended. " + SachetAlertState.readable(first) + ".", "")
                NetraNotificationManager(ctx).sendEmergencyAlert(
                    title = "Warning over (NDMA SACHET, official)",
                    message = "The earlier official warning has ended: " + SachetAlertState.readable(first) +
                        (if (over.size > 1) " (+" + (over.size - 1) + " more)" else "") + ".",
                    riskLevel = SafetyRiskLevel.SAFE
                )
                over.forEach { active.remove(it) }
            }
            prefs.edit().putString(AlertArea.scoped(KEY_ACTIVE, areaVer), SachetAlertState.encode(active)).apply()
            val updated = (seen + checked).toList().takeLast(400).toSet()
            prefs.edit().putStringSet(AlertArea.scoped(KEY_SEEN, areaVer), updated).apply()
            save(noWarningStatus(loc.district, loc.locality, loc.state, checked.size, matched.size))
            return Result.success()
        } catch (e: Exception) {
            save("Unavailable: check failed.")
            return Result.retry()
        }
    }

    companion object {
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
