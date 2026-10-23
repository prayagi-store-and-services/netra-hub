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

    /** Same-process memory of the last announced state: a repeated same-state edge (double owner, replay) is
     * suppressed. Process death resets this - and the gate restarts CLOSED, so the next ON edge after death is
     * genuine and announces. Persisted state across death would suppress a genuine re-open. */
    @Volatile private var lastAnnouncedState: Boolean? = null

    /** Test seam: how a notice is actually spoken. Production = OfficialAlertAnnouncer. */
    @Volatile internal var speaker: (Context, String, String, ((Boolean, String?) -> Unit)?) -> String? =
        { c, text, id, cb -> OfficialAlertAnnouncer.speakNotice(c, text, id, cb) }

    /** An accepted transition whose id could not be persisted: speech is held (fail-closed) until the id lands. */
    private data class PendingAnnouncement(val started: Boolean, val seq: Long, val speedKmH: Float?)
    @Volatile private var pendingAnnouncement: PendingAnnouncement? = null

    @androidx.annotation.VisibleForTesting
    internal fun resetTransitionMemoryForTests() { lastAnnouncedState = null; pendingAnnouncement = null }

    private fun speakAccepted(c: Context, started: Boolean, seq: Long, speedKmH: Float?, now: Long) {
        if (!announceEnabled(c)) return // the transition entry already records "disabled; skipped"
        val reqId = "driving_T" + seq
        val limit = speaker(c, if (started) startedText() else endedText(), reqId) { ok, err ->
            // TTS result/failure is recorded per request, so a silent phone is never invisible.
            DrivingLog.add(c, DrivingLog.Entry(System.currentTimeMillis(), DrivingLog.NOTE, speedKmH,
                "Announcement " + reqId + (if (ok) " finished" else " failed" + (err?.let { ": " + it } ?: ""))))
        }
        // The state change is real even when sound cannot play; say so in the log instead of pretending.
        if (limit != null) DrivingLog.add(c, DrivingLog.Entry(now, DrivingLog.NOTE, speedKmH,
            "Driving mode " + (if (started) "ON" else "OFF") + " announcement could not play: " + limit))
    }

    /** Retries a held announcement: the id is persisted first, and only then does speech go out. */
    private fun retryPending(c: Context, now: Long) {
        val pa = pendingAnnouncement ?: return
        val p = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (p.edit().putLong(KEY_TRANSITION_SEQ, pa.seq).commit()) {
            pendingAnnouncement = null
            DrivingLog.add(c, DrivingLog.Entry(now, DrivingLog.NOTE, pa.speedKmH,
                "Transition id driving_T" + pa.seq + " persisted on retry; the held announcement now plays."))
            speakAccepted(c, pa.started, pa.seq, pa.speedKmH, now)
        }
    }

    @Synchronized
    fun onGateChange(c: Context, started: Boolean, speedKmH: Float?, now: Long = System.currentTimeMillis(), blindClose: Boolean = false, recentSamples: List<String> = emptyList()) {
        // A held announcement gets its retry before anything else, on every edge that reaches us.
        retryPending(c, now)
        // Duplicate same-state edge: no transition id consumed, no accepted-transition log, no speech, no trip.
        if (lastAnnouncedState == started) {
            DrivingLog.add(c, DrivingLog.Entry(now, DrivingLog.NOTE, speedKmH,
                "Duplicate driving " + (if (started) "ON" else "OFF") + " transition suppressed (no intervening opposite change)"))
            return
        }
        val p = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val seq = p.getLong(KEY_TRANSITION_SEQ, 0L) + 1L
        // Persist-before-TTS is the contract: if the id cannot be persisted the announcement is HELD
        // (fail-closed), retried on the next edge. The state change and the trip still happen - only speech waits.
        val persisted = p.edit().putLong(KEY_TRANSITION_SEQ, seq).commit()
        lastAnnouncedState = started
        val diag = if (recentSamples.isEmpty()) "" else " Recent gate samples: " + recentSamples.joinToString(", ") + "."
        // Persist the transition and the announcement request BEFORE any speech. Bounded diagnostics, no coordinates.
        val enabled = announceEnabled(c)
        DrivingLog.add(c, DrivingLog.Entry(now, if (started) DrivingLog.STARTED else DrivingLog.ENDED, speedKmH,
            "T#" + seq + " " + reasonFor(started, speedKmH, blindClose) + diag +
                if (!persisted) " Announcement driving_T" + seq + " HELD: transition id could not be persisted (fail-closed, retried on next edge)."
                else if (!enabled) " Announcement driving_T" + seq + " disabled by the user; skipped."
                else " Announcement driving_T" + seq + " requested."))
        if (started) DrivingTrip.start(c, speedKmH, now) else DrivingTrip.stop()
        if (persisted) speakAccepted(c, started, seq, speedKmH, now) else pendingAnnouncement = PendingAnnouncement(started, seq, speedKmH)
    }

    /** Trip recording resumed without a new driving edge (monitoring restart while the gate stayed open). */
    fun noteTripResumed(c: Context, now: Long = System.currentTimeMillis()) {
        DrivingLog.add(c, DrivingLog.Entry(now, DrivingLog.NOTE, null,
            "Trip recording resumed: the speed gate was still open after a monitoring restart (no new driving edge, no new announcement)"))
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
