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

    /** One journal instance owns accepted transitions for this process. Persistence is independent of the
     * announcement preferences; a commit-failed preference cache never supplies accepted history. */
    private var journal: DrivingJournal? = null
    private var journalStore: DrivingJournalStore? = null
    @Volatile internal var journalStoreFactory: (Context) -> DrivingJournalStore = { AndroidDrivingJournalStore(it) }

    /** Test seam: production routes through the same announcer as official safety alerts. */
    @Volatile internal var speaker: (Context, String, String, ((Boolean, String?) -> Unit)?) -> String? =
        { c, text, id, cb -> OfficialAlertAnnouncer.speakNotice(c, text, id, cb) }

    @Synchronized private fun journal(c: Context): DrivingJournal {
        journal?.let { return it }
        val store = journalStoreFactory(c)
        val loaded = DrivingJournal(store)
        journalStore = store
        journal = loaded
        return loaded
    }

    @androidx.annotation.VisibleForTesting
    @Synchronized internal fun resetTransitionMemoryForTests() { journal = null; journalStore = null }

    /** UI history comes from durable snapshots, not a failed SharedPreferences memory write. */
    @Synchronized internal fun acceptedHistory(c: Context): List<DrivingLog.Entry> {
        journal(c) // applies the explicit no-replay restart policy once
        return journalStore!!.readDurable().asReversed().map { e ->
            DrivingLog.Entry(e.at, if (e.started) DrivingLog.STARTED else DrivingLog.ENDED, e.speedKmH,
                "Event " + e.id + " " + e.reason + " " + e.diagnostics +
                    " Announcement " + e.audio + ": " + e.result)
        }
    }

    @Synchronized
    fun onGateChange(c: Context, started: Boolean, speedKmH: Float?, now: Long = System.currentTimeMillis(), blindClose: Boolean = false, recentSamples: List<String> = emptyList()) {
        // Trip lifecycle reflects the real detector edge even when storage is unavailable; audio does not.
        // start() is idempotent and also reconciles a same-state monitoring restart.
        if (started) DrivingTrip.start(c, speedKmH, now) else DrivingTrip.stop()
        try {
            val owner = journal(c)
            val diagnostics = recentSamples.joinToString(", ").take(8000)
            owner.accept(started, now, speedKmH, reasonFor(started, speedKmH, blindClose), diagnostics)
            // This commits accepted history + SUBMITTED intent together before the engine receives work.
            // Disabled recovery is committed as SKIPPED; superseded unsubmitted history is never erased.
            for (event in owner.prepareAudio(announceEnabled(c))) {
                val requestId = "driving_" + event.id
                try {
                    val limit = speaker(c, if (event.started) startedText() else endedText(), requestId) { ok, error ->
                        owner.complete(event.id, ok, error ?: if (ok) "engine reported done" else "engine reported failure")
                    }
                    if (limit != null) owner.complete(event.id, false, limit)
                } catch (_: Exception) {
                    owner.complete(event.id, false, "engine submission threw an exception")
                }
            }
        } catch (_: Exception) {
            // No durable store means no announcement. Never erase an unreadable journal or call this fixed.
            DrivingLog.add(c, DrivingLog.Entry(now, DrivingLog.NOTE, speedKmH,
                "Driving transition could not be durably recorded; audio held. Storage requires recovery."))
        }
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
