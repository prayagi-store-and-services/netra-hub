package com.example.driving

import android.content.Context
import com.example.data.engine.OfficialAlertAnnouncer

/** One place for "driving mode changed": writes the log entry and speaks the announcement (user switch, default ON). */
object DrivingEvents {
    const val PREFS = "netra_driving_announce"
    const val KEY_ENABLED = "enabled"
    const val TEST_TEXT = "This is a test of the Netra driving mode announcement. This is only a test. You are not necessarily driving."
    private const val NOTE_GAP_MS = 10 * 60 * 1000L
    @Volatile private var lastNoteAt = 0L

    fun announceEnabled(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ENABLED, true)
    fun setAnnounceEnabled(c: Context, on: Boolean) { c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_ENABLED, on).apply() }

    fun startedText() = "Netra driving mode is on. Safety sensors are listening."
    fun endedText() = "Netra driving mode is off."

    fun reasonFor(started: Boolean, speedKmH: Float?, blindClose: Boolean = false): String = if (!started && blindClose)
        "GPS gave no speed for " + (com.example.data.sensor.SpeedGate.UNKNOWN_CLOSE_MS / 60_000) + " minutes, so driving mode was closed" + (speedKmH?.let { " (last known speed %.0f km/h)".format(it) } ?: "")
    else if (started)
        "GPS speed reached " + (speedKmH?.let { "%.0f km/h".format(it) } ?: "the limit") + " (limit " + "%.0f".format(com.example.data.sensor.SpeedGate.DEFAULT_THRESHOLD_KMH) + " km/h)"
    else "Speed stayed under " + "%.0f".format(com.example.data.sensor.SpeedGate.DEFAULT_THRESHOLD_KMH) + " km/h for " + (com.example.data.sensor.SpeedGate.DEFAULT_CLOSE_DELAY_MS / 1000) + " seconds"

    private const val KEY_TRANSITION_SEQ = "transition_seq"
    private const val KEY_LAST_STATE = "transition_last_state"

    /** Next transition number, persisted so ids survive process death. Committed BEFORE any speech. */
    @Synchronized
    private fun nextTransition(c: Context, started: Boolean): Pair<Long, Boolean> {
        val p = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val last = if (p.contains(KEY_LAST_STATE)) p.getBoolean(KEY_LAST_STATE, false) else null
        val seq = p.getLong(KEY_TRANSITION_SEQ, 0L) + 1L
        p.edit().putLong(KEY_TRANSITION_SEQ, seq).putBoolean(KEY_LAST_STATE, started).commit()
        return seq to (last == started) // duplicate: same state with no intervening opposite transition
    }

    @Synchronized
    fun onGateChange(c: Context, started: Boolean, speedKmH: Float?, now: Long = System.currentTimeMillis(), blindClose: Boolean = false, recentSamples: List<String> = emptyList()) {
        val (seq, duplicate) = nextTransition(c, started)
        // Persist the transition BEFORE any speech, with bounded sample diagnostics (no coordinates).
        val diag = if (recentSamples.isEmpty()) "" else " Recent gate samples: " + recentSamples.joinToString(", ") + "."
        DrivingLog.add(c, DrivingLog.Entry(now, if (started) DrivingLog.STARTED else DrivingLog.ENDED, speedKmH,
            "T#" + seq + " " + reasonFor(started, speedKmH, blindClose) + diag))
        if (duplicate) {
            // A repeated same-state edge (double owner, replay, restart race): recorded above, never re-announced or re-tripped.
            DrivingLog.add(c, DrivingLog.Entry(now, DrivingLog.NOTE, speedKmH,
                "T#" + seq + " duplicate " + (if (started) "ON" else "OFF") + " transition suppressed (no intervening opposite change)"))
            return
        }
        if (started) DrivingTrip.start(c, speedKmH, now) else DrivingTrip.stop()
        if (announceEnabled(c)) {
            val reqId = "driving_T" + seq
            val limit = OfficialAlertAnnouncer.speakNotice(c, if (started) startedText() else endedText(), reqId) { ok, err ->
                // TTS result/failure is recorded per request, so a silent phone is never invisible.
                DrivingLog.add(c, DrivingLog.Entry(System.currentTimeMillis(), DrivingLog.NOTE, speedKmH,
                    "Announcement " + reqId + (if (ok) " finished" else " failed" + (err?.let { ": " + it } ?: ""))))
            }
            // The state change is real even when sound cannot play; say so in the log instead of pretending.
            if (limit != null) DrivingLog.add(c, DrivingLog.Entry(now, DrivingLog.NOTE, speedKmH,
                "Driving mode " + (if (started) "ON" else "OFF") + " announcement could not play: " + limit))
        }
    }

    /** Records (at most once per 10 minutes) that detection is blind, so a missing detection is explained in the log. */
    fun noteBlind(c: Context, reason: String, now: Long = System.currentTimeMillis()) {
        if (now - lastNoteAt < NOTE_GAP_MS) return
        if (DrivingLog.list(c).firstOrNull()?.let { it.kind == DrivingLog.NOTE && it.reason.startsWith("GPS gave no usable speed") } == true) return // never let repeated blind notes push real entries out
        lastNoteAt = now
        DrivingLog.add(c, DrivingLog.Entry(now, DrivingLog.NOTE, null, reason))
    }

    /** TEST button: speaks the labelled test line and writes a clearly labelled test entry. */
    fun test(c: Context): String {
        DrivingLog.add(c, DrivingLog.Entry(System.currentTimeMillis(), DrivingLog.NOTE, null, "TEST button pressed (no real driving)"))
        OfficialAlertAnnouncer.speakNotice(c, TEST_TEXT)
        return "Playing the driving-mode test announcement. It is only a test."
    }
}
