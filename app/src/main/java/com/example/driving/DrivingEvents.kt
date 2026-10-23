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

    /** An accepted transition whose id or request log could not be persisted: speech is held (fail-closed)
     * until persistence recovers. Crash semantics, defined: the pending intent is process-memory only, so a
     * process death drops it; the next genuine edge after death announces fresh, and the HELD transition entry
     * remains in the log wherever log persistence itself worked. A pending intent is NEVER replayed once the
     * world has moved to the opposite state - recovery reconciles with the authoritative current state. */
    private data class PendingAnnouncement(val started: Boolean, val speedKmH: Float?, val blindClose: Boolean, val diag: String, val at: Long, val attempt: Int, val seq: Long?)
    @Volatile private var pendingAnnouncement: PendingAnnouncement? = null
    private var heldAttempt = 0

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

    /** Retries a held announcement with the FULL envelope: a fresh id is issued only now (a held attempt
     * never carries an accepted id), the original STARTED/ENDED transition + request entry is persisted,
     * and only when id AND log are both durable does speech go out. */
    private fun retryPending(c: Context, now: Long) {
        val pa = pendingAnnouncement ?: return
        val p = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        // The ONE accepted record for this transition: the id the first attempt already persisted when it
        // exists, otherwise a fresh id minted now. Held attempts never carry a T#, so no id is ever reused.
        val seq = pa.seq ?: (p.getLong(KEY_TRANSITION_SEQ, 0L) + 1L)
        if (!p.edit().putLong(KEY_TRANSITION_SEQ, seq).commit()) return
        if (!DrivingLog.add(c, DrivingLog.Entry(now, if (pa.started) DrivingLog.STARTED else DrivingLog.ENDED, pa.speedKmH,
                "T#" + seq + " " + reasonFor(pa.started, pa.speedKmH, pa.blindClose) + pa.diag +
                    " (held attempt #" + pa.attempt + " since " + pa.at + ", recovered now) Announcement driving_T" + seq + " requested."))) return
        pendingAnnouncement = null
        speakAccepted(c, pa.started, seq, pa.speedKmH, now)
    }

    @Synchronized
    fun onGateChange(c: Context, started: Boolean, speedKmH: Float?, now: Long = System.currentTimeMillis(), blindClose: Boolean = false, recentSamples: List<String> = emptyList()) {
        // Reconcile a held announcement against the authoritative CURRENT state before anything else:
        // same direction -> retry it; opposite edge -> the held one is stale history, never replay it.
        pendingAnnouncement?.let { pa ->
            if (pa.started == started) {
                retryPending(c, now)
            } else {
                pendingAnnouncement = null
                DrivingLog.add(c, DrivingLog.Entry(now, DrivingLog.NOTE, speedKmH,
                    "Held announcement (attempt #" + pa.attempt + ", no id was issued) superseded by driving " + (if (started) "ON" else "OFF") + " before persistence recovered; not played."))
            }
        }
        // Duplicate same-state edge: no transition id consumed, no accepted-transition log, no speech, no trip.
        if (lastAnnouncedState == started) {
            DrivingLog.add(c, DrivingLog.Entry(now, DrivingLog.NOTE, speedKmH,
                "Duplicate driving " + (if (started) "ON" else "OFF") + " transition suppressed (no intervening opposite change)"))
            return
        }
        val p = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val seq = p.getLong(KEY_TRANSITION_SEQ, 0L) + 1L
        // Persist-before-TTS is the contract: id AND request entry must BOTH be durable before any speech.
        // A committed id alone proves nothing about the log; either failure HOLDS the announcement
        // (fail-closed), retried on the next edge. A held attempt is issued NO id - accepted ids are only
        // ever minted on durable accept, so a held T# can never be reused by a later transition.
        val idPersisted = p.edit().putLong(KEY_TRANSITION_SEQ, seq).commit()
        lastAnnouncedState = started
        val diag = if (recentSamples.isEmpty()) "" else " Recent gate samples: " + recentSamples.joinToString(", ") + "."
        // Persist the transition and the announcement request BEFORE any speech. Bounded diagnostics, no coordinates.
        val enabled = announceEnabled(c)
        val attempt = ++heldAttempt
        // A HELD attempt is a diagnostic NOTE with NO T# id: the STARTED/ENDED record with its unique id is
        // written exactly once, on durable accept (here or in retryPending) - never twice, never reused.
        val logPersisted =
            if (!idPersisted) DrivingLog.add(c, DrivingLog.Entry(now, DrivingLog.NOTE, speedKmH,
                "Driving " + (if (started) "ON" else "OFF") + " transition accepted but not durable (held attempt #" + attempt + ", no id issued): " +
                    reasonFor(started, speedKmH, blindClose) + diag + " Fail-closed: the announcement is retried on the next edge."))
            else DrivingLog.add(c, DrivingLog.Entry(now, if (started) DrivingLog.STARTED else DrivingLog.ENDED, speedKmH,
                "T#" + seq + " " + reasonFor(started, speedKmH, blindClose) + diag +
                    if (!enabled) " Announcement driving_T" + seq + " disabled by the user; skipped."
                    else " Announcement driving_T" + seq + " requested."))
        if (started) DrivingTrip.start(c, speedKmH, now) else DrivingTrip.stop()
        if (idPersisted && logPersisted) speakAccepted(c, started, seq, speedKmH, now)
        else pendingAnnouncement = PendingAnnouncement(started, speedKmH, blindClose, diag, now, attempt, if (idPersisted) seq else null)
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
