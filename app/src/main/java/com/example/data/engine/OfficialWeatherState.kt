package com.example.data.engine

import android.content.Context
import java.io.InputStream

internal const val MAX_OFFICIAL_BYTES = 8 * 1024 * 1024
internal fun boundedOfficialText(input: InputStream, limit: Int = MAX_OFFICIAL_BYTES): String {
    val out = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    while (true) { val n = input.read(buffer); if (n < 0) break; require(out.size() + n <= limit) { "Official response too large" }; out.write(buffer, 0, n) }
    return out.toString("UTF-8")
}
internal data class OfficialReportSnapshot(val version: Long, val checked: Long, val validUntil: Long, val place: String, val text: String, val capItems: String = "[]")
internal fun reportVisible(report: OfficialReportSnapshot, areaVersion: Long, now: Long): Boolean =
    report.version == areaVersion && report.checked <= now && now - report.checked < 45 * 60_000L && report.validUntil > now && imdDate(report.checked) == imdDate(now)
internal object OfficialWeatherReports {
    private const val KEY = "official_report_v3"
    fun clear(ctx: Context) { ctx.getSharedPreferences(SachetWatchWorker.PREFS, Context.MODE_PRIVATE).edit().remove(KEY).apply() }
    fun save(ctx: Context, r: OfficialReportSnapshot) {
        val j = org.json.JSONObject().put("version", r.version).put("checked", r.checked).put("until", r.validUntil).put("place", r.place).put("text", r.text).put("cap",r.capItems)
        ctx.getSharedPreferences(SachetWatchWorker.PREFS, Context.MODE_PRIVATE).edit().putString(KEY,j.toString()).apply()
    }
    fun text(ctx: Context, now: Long): String {
        val raw = ctx.getSharedPreferences(SachetWatchWorker.PREFS, Context.MODE_PRIVATE).getString(KEY,null)
        val r = runCatching { val j = org.json.JSONObject(raw ?: ""); OfficialReportSnapshot(j.getLong("version"), j.getLong("checked"), j.getLong("until"),j.getString("place"),j.getString("text"),j.optString("cap","[]")) }.getOrNull()
        return if (r != null && reportVisible(r, AlertArea.version(ctx),now)) "Checked " + java.util.Date(r.checked) + ". Area at check: " + r.place + ".\n" + r.text + renderCapItems(r.capItems,now) else "Current official weather report unavailable or expired. Automatic checks are about every 30 minutes and Android may delay them. Not an all-clear."
    }
}
internal fun capIsWeather(cap: CapAlert): Boolean = cap.categories.any { it == "Met" } || Regex("rain|thunder|lightning|cyclone|weather|heat|cold|fog|snow|wind|hail",RegexOption.IGNORE_CASE).containsMatchIn(cap.event)
internal data class OutputDue(val notification: Boolean, val speech: Boolean)
internal fun officialOutputsDue(token: String, notified: String?, spoken: String?, audible: Boolean) = OutputDue(notified != token && audible, spoken != token && audible)
internal fun officialBatch(size: Int, cursor: Int, limit: Int = 40): List<Int> = if (size <= 0) emptyList() else (0 until minOf(size,limit)).map { (cursor.coerceAtLeast(0) + it) % size }

internal data class CapLifecycle(val severity: String, val expires: Long, val token: String)
internal fun capLifecycle(previous: CapLifecycle?, severity: String, expires: Long, now: Long): CapLifecycle {
    val same = previous != null && previous.expires > now && previous.severity == severity
    return CapLifecycle(severity,expires,if (same) previous!!.token else severity + "|" + now)
}
internal fun mergeCapReports(old: org.json.JSONArray, current: List<CapAlert>, now: Long): org.json.JSONArray {
    val items = linkedMapOf<String,org.json.JSONObject>()
    for (i in 0 until minOf(old.length(),150)) runCatching {
        val j=old.getJSONObject(i)
        if (j.getLong("until") > now) items[j.getString("key")]=j
    }
    current.forEach { c ->
        val key=SachetAlertState.identityKey(c.event,c.areaDesc)
        val until=if(c.expiresMillis > now)c.expiresMillis else now+45*60_000L
        items[key]=org.json.JSONObject().put("key",key).put("until",until).put("text",(c.event + ": " + c.severity + ". " + c.headline + ". Area: " + c.areaDesc).take(1500))
    }
    return org.json.JSONArray(items.values.toList().takeLast(150))
}

internal fun renderCapItems(raw: String, now: Long): String = runCatching {
    val items=org.json.JSONArray(raw)
    (0 until minOf(items.length(),150)).mapNotNull { i -> val j=items.getJSONObject(i); if(j.getLong("until")>now)j.getString("text") else null }.joinToString("\n",prefix="\n")
}.getOrDefault("")
/** Persist newest source timestamp per identity. Undated reports remain visible but cannot advance speech. */
internal fun latestCapRevisions(batch: List<CapAlert>, persisted: org.json.JSONObject): List<CapAlert> {
    val current=batch.groupBy { SachetAlertState.identityKey(it.event,it.areaDesc) }.mapNotNull { (key,items) ->
        val newest=items.maxWithOrNull(compareBy<CapAlert> { maxOf(it.sentMillis,it.effectiveMillis) }.thenBy { it.id }) ?: return@mapNotNull null
        val timestamp=maxOf(newest.sentMillis,newest.effectiveMillis)
        val previous=persisted.optLong(key,0)
        if(timestamp>0 && timestamp<previous) null else {
            if(timestamp>previous)persisted.put(key,timestamp)
            newest
        }
    }
    // Bounded revision ledger; dropping old entries never marks an unspoken output delivered.
    while(persisted.length()>200) persisted.remove(persisted.keys().next())
    return current
}

/** Per-instance transport/output seam, unset in production; integration tests exercise the real worker/state path. */
internal data class OfficialWorkerIo(val now: Long, val location: com.example.data.service.LocationContextInfo, val fetch: (String)->String?, val notify: (String,String)->Boolean, val speechLimit: ()->String?, val speak: (String,(Boolean)->Unit)->Unit)
