package com.example.driving

import android.content.Context

/**
 * Local history of driving-mode changes (time, speed, reason). Kept in this app's private storage, newest 500 entries,
 * never sent anywhere. Survives app updates and restarts (uninstall or clearing app data removes it).
 */
object DrivingLog {
    const val PREFS = "netra_driving_log"
    const val KEY = "entries"
    const val MAX = 500

    data class Entry(val at: Long, val kind: String, val speedKmH: Float?, val reason: String)

    const val STARTED = "STARTED"
    const val ENDED = "ENDED"
    const val NOTE = "NOTE"
    const val SPEED = "SPEED"
    const val BRAKE = "BRAKE"
    const val BUMP = "BUMP"

    fun encode(list: List<Entry>): String = list.joinToString("\n") { e ->
        listOf(e.at.toString(), e.kind, e.speedKmH?.toString() ?: "", e.reason.replace('|', '/').replace('\n', ' ')).joinToString("|")
    }

    fun decode(s: String): List<Entry> = s.lineSequence().mapNotNull { line ->
        val p = line.split("|", limit = 4)
        if (p.size < 4) return@mapNotNull null
        val at = p[0].toLongOrNull() ?: return@mapNotNull null
        Entry(at, p[1], p[2].toFloatOrNull(), p[3])
    }.toList()

    /** Newest first, capped. Pure so it can be tested without a phone. */
    fun append(existing: List<Entry>, e: Entry): List<Entry> = (listOf(e) + existing).take(MAX)

    @Synchronized
    fun add(context: Context, e: Entry) {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        p.edit().putString(KEY, encode(append(decode(p.getString(KEY, "") ?: ""), e))).apply()
    }

    fun list(context: Context): List<Entry> =
        decode(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "") ?: "")

    fun clear(context: Context) { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY).apply() }
}
