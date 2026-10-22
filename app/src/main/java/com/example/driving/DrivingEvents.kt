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

    fun reasonFor(started: Boolean, speedKmH: Float?): String = if (started)
        "GPS speed reached " + (speedKmH?.let { "%.0f km/h".format(it) } ?: "the limit") + " (limit " + "%.0f".format(com.example.data.sensor.SpeedGate.DEFAULT_THRESHOLD_KMH) + " km/h)"
    else "Speed stayed under " + "%.0f".format(com.example.data.sensor.SpeedGate.DEFAULT_THRESHOLD_KMH) + " km/h for " + (com.example.data.sensor.SpeedGate.DEFAULT_CLOSE_DELAY_MS / 1000) + " seconds"

    fun onGateChange(c: Context, started: Boolean, speedKmH: Float?, now: Long = System.currentTimeMillis()) {
        DrivingLog.add(c, DrivingLog.Entry(now, if (started) DrivingLog.STARTED else DrivingLog.ENDED, speedKmH, reasonFor(started, speedKmH)))
        if (started) DrivingTrip.start(c, speedKmH, now) else DrivingTrip.stop()
        if (announceEnabled(c)) OfficialAlertAnnouncer.speakNotice(c, if (started) startedText() else endedText())
    }

    /** Records (at most once per 10 minutes) that detection is blind, so a missing detection is explained in the log. */
    fun noteBlind(c: Context, reason: String, now: Long = System.currentTimeMillis()) {
        if (now - lastNoteAt < NOTE_GAP_MS) return
        if (DrivingLog.list(c).firstOrNull()?.kind == DrivingLog.NOTE) return // never let notes push real entries out
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
