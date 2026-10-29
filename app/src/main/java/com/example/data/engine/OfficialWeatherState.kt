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
internal data class OfficialReportSnapshot(val version: Long, val checked: Long, val validUntil: Long, val place: String, val text: String)
internal fun reportVisible(report: OfficialReportSnapshot, areaVersion: Long, now: Long): Boolean =
    report.version == areaVersion && report.checked <= now && now - report.checked < 45 * 60_000L && report.validUntil > now && imdDate(report.checked) == imdDate(now)
internal object OfficialWeatherReports {
    private const val KEY = "official_report_v3"
    fun clear(ctx: Context) { ctx.getSharedPreferences(SachetWatchWorker.PREFS, Context.MODE_PRIVATE).edit().remove(KEY).apply() }
    fun save(ctx: Context, r: OfficialReportSnapshot) {
        val j = org.json.JSONObject().put("version", r.version).put("checked", r.checked).put("until", r.validUntil).put("place", r.place).put("text", r.text.take(24000))
        ctx.getSharedPreferences(SachetWatchWorker.PREFS, Context.MODE_PRIVATE).edit().putString(KEY,j.toString()).apply()
    }
    fun text(ctx: Context, now: Long): String {
        val raw = ctx.getSharedPreferences(SachetWatchWorker.PREFS, Context.MODE_PRIVATE).getString(KEY,null)
        val r = runCatching { val j = org.json.JSONObject(raw ?: ""); OfficialReportSnapshot(j.getLong("version"), j.getLong("checked"), j.getLong("until"),j.getString("place"),j.getString("text")) }.getOrNull()
        return if (r != null && reportVisible(r, AlertArea.version(ctx),now)) "Checked " + java.util.Date(r.checked) + ". Area at check: " + r.place + ".\n" + r.text else "Current official weather report unavailable or expired. Automatic checks are about every 30 minutes and Android may delay them. Not an all-clear."
    }
}
internal fun capIsWeather(cap: CapAlert): Boolean = cap.categories.any { it == "Met" } || Regex("rain|thunder|lightning|cyclone|weather|heat|cold|fog|snow|wind|hail",RegexOption.IGNORE_CASE).containsMatchIn(cap.event)
internal data class OutputDue(val notification: Boolean, val speech: Boolean)
internal fun officialOutputsDue(token: String, notified: String?, spoken: String?, audible: Boolean) = OutputDue(notified != token && audible, spoken != token && audible)
internal fun officialBatch(size: Int, cursor: Int, limit: Int = 40): List<Int> = if (size <= 0) emptyList() else (0 until minOf(size,limit)).map { (cursor.coerceAtLeast(0) + it) % size }
