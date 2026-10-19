package com.example.data.engine

import java.util.Locale

/**
 * Persistent per-alert announcement state for SACHET weather alerts.
 * Owner rule (19 Oct 2026): speak/notify once per alert; again only when the severity changes
 * (up or down) or when the alert is over. The same severity never re-announces.
 * Identity is what a person calls "the same alert": the event and its area, not the RSS guid
 * (SACHET re-issues the same warning under a new guid, which used to re-announce it).
 */
data class SachetAlertEntry(val severity: String, val expiresMillis: Long)

enum class SachetDecision { FIRST_ALERT, SEVERITY_CHANGED, SAME_SEVERITY_SKIP }

object SachetAlertState {

    /** What a person calls "the same alert": the event and its area, normalized. */
    fun identityKey(event: String, areaDesc: String): String {
        fun n(s: String) = s.lowercase(Locale.ROOT).replace(Regex("[^a-z0-9]+"), " ").trim()
        return n(event) + "|" + n(areaDesc)
    }

    fun decide(stored: SachetAlertEntry?, currentSeverity: String): SachetDecision = when {
        stored == null -> SachetDecision.FIRST_ALERT
        stored.severity != currentSeverity -> SachetDecision.SEVERITY_CHANGED
        else -> SachetDecision.SAME_SEVERITY_SKIP
    }

    /** Keys whose warning has passed its expiry: announce "over" once, then drop. Alerts without an expiry are kept, never guessed. */
    fun expiredKeys(active: Map<String, SachetAlertEntry>, nowMillis: Long): List<String> =
        active.filter { it.value.expiresMillis > 0L && it.value.expiresMillis <= nowMillis }.keys.sorted()

    /** Human-readable form of an identity key for the "warning over" line. */
    fun readable(key: String): String = key.replace("|", ", area ")

    // Storage: one line per alert "key=severity,expiresMillis". Keys hold no '=' or newline.
    fun encode(active: Map<String, SachetAlertEntry>): String =
        active.entries.toList().takeLast(150).joinToString("\n") { it.key + "=" + it.value.severity + "," + it.value.expiresMillis }

    fun decode(s: String): Map<String, SachetAlertEntry> =
        s.lines().mapNotNull { line ->
            val eq = line.lastIndexOf('=')
            if (eq <= 0) return@mapNotNull null
            val v = line.substring(eq + 1).split(",")
            if (v.size != 2) return@mapNotNull null
            line.substring(0, eq) to SachetAlertEntry(v[0], v[1].toLongOrNull() ?: 0L)
        }.toMap()
}
