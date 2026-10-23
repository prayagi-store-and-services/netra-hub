package com.example.data.sensor

import android.content.Context

/**
 * Speed gate for the always-on safety sensors. The sensors listen only while the phone is moving at or above
 * [thresholdKmH] (default 15 km/h), so walking never keeps them running. The gate closes only after the speed has been
 * below the threshold for [closeDelayMs], so a short stop at a traffic light does not switch the sensors off and on.
 * It opens only after the speed has stayed at/above the threshold for [openDelayMs], so a single GPS spike on a
 * still phone (common indoors) never turns driving mode on.
 * Unknown speed (no GPS fix, no permission) is not evidence of slowing down: it never advances that timer, and
 * only a long blind spell (UNKNOWN_CLOSE_MS) closes the gate. Nothing is guessed.
 * Sensor screens the user has open still show live data; the gate only controls the background listening.
 */
class SpeedGate(
    private val thresholdKmH: Float = DEFAULT_THRESHOLD_KMH,
    private val closeDelayMs: Long = DEFAULT_CLOSE_DELAY_MS,
    private val openDelayMs: Long = DEFAULT_OPEN_DELAY_MS
) {
    var isOpen: Boolean = false
        private set
    /** True when the gate last closed because GPS gave no speed for a long time (not because speed was low). */
    var closedByBlindSpell: Boolean = false
        private set
    private var belowSinceMs: Long = -1L
    private var unknownSinceMs: Long = -1L
    private var aboveSinceMs: Long = -1L
    private var lastValidMs: Long = 0L
    private var blindSinceLastValid = false
    /** Accepted above-threshold readings inside the current high window. Two spikes are only two endpoints,
     * not evidence of continuous speed: the gate opens only with at least MIN_OPEN_SAMPLES accepted readings
     * in the window (a stationary phone's paired spikes can never reach that). */
    private var acceptedInWindow = 0

    data class DiagnosticState(val open: Boolean, val confirmationCount: Int, val aboveSinceMs: Long,
                               val belowSinceMs: Long, val unknownSinceMs: Long, val blindClose: Boolean)
    @Synchronized fun diagnosticState() = DiagnosticState(isOpen, acceptedInWindow, aboveSinceMs,
        belowSinceMs, unknownSinceMs, closedByBlindSpell)

    /** Feed a speed reading (null = unknown). Returns true when the gate changed state. */
    @Synchronized
    fun update(speedKmH: Float?, nowMs: Long): Boolean {
        val before = isOpen
        if (speedKmH == null) {
            // Unknown speed is NOT evidence of slowing down: it never advances the close timer.
            // Only a long blind spell (GPS lost) closes the gate, so a stuck ON state cannot last.
            blindSinceLastValid = true
            aboveSinceMs = -1L // a blind gap is not consecutive fresh evidence: the high window restarts
            acceptedInWindow = 0
            if (isOpen) {
                if (unknownSinceMs < 0) unknownSinceMs = nowMs
                if (nowMs - unknownSinceMs >= UNKNOWN_CLOSE_MS) { isOpen = false; closedByBlindSpell = true; belowSinceMs = -1L; unknownSinceMs = -1L }
            }
            return before != isOpen
        }
        unknownSinceMs = -1L
        if (speedKmH >= thresholdKmH) {
            // One fast reading is not driving: GPS on a still phone can spike. The gate opens only after
            // the speed has stayed at/above the threshold for openDelayMs. A long blind gap makes an old
            // high run stale, so the window restarts after it.
            // Opening needs consecutive fresh evidence: a gap over OPEN_GAP_MAX_MS between valid readings is
            // treated like a blind spell and restarts the window (two spikes far apart are not "sustained").
            if (aboveSinceMs < 0 || nowMs - lastValidMs > OPEN_GAP_MAX_MS) { aboveSinceMs = nowMs; acceptedInWindow = 0 }
            acceptedInWindow++
            belowSinceMs = -1L
            if (!isOpen && acceptedInWindow >= MIN_OPEN_SAMPLES && nowMs - aboveSinceMs >= openDelayMs) isOpen = true
        } else {
            aboveSinceMs = -1L
            acceptedInWindow = 0
            if (isOpen) {
            // A blind gap breaks the run of low readings: the timer restarts after it.
            if (belowSinceMs < 0 || (blindSinceLastValid && nowMs - lastValidMs > GAP_RESTARTS_MS)) belowSinceMs = nowMs
            if (nowMs - belowSinceMs >= closeDelayMs) {
                isOpen = false
                closedByBlindSpell = false
                belowSinceMs = -1L
            }
            }
        }
        blindSinceLastValid = false
        lastValidMs = nowMs
        return before != isOpen
    }

    @Synchronized
    fun reset() {
        isOpen = false
        belowSinceMs = -1L
        unknownSinceMs = -1L
        aboveSinceMs = -1L
        lastValidMs = 0L
        blindSinceLastValid = false
        acceptedInWindow = 0
    }

    /** Clock-driven blind advance for tick sources that have no speed at all (the 10 s UI poller): it moves only
     * the blind-close timer, so a stuck-ON gate still closes when live updates stop. It never opens the gate,
     * never confirms a pending window and never restarts the high window. Returns true when the gate closed. */
    @Synchronized
    fun onBlindTick(nowMs: Long): Boolean {
        if (!isOpen) return false
        blindSinceLastValid = true
        if (unknownSinceMs < 0) unknownSinceMs = nowMs
        if (nowMs - unknownSinceMs >= UNKNOWN_CLOSE_MS) {
            isOpen = false; closedByBlindSpell = true; belowSinceMs = -1L; unknownSinceMs = -1L
            return true
        }
        return false
    }

    companion object {
        const val DEFAULT_THRESHOLD_KMH = 15f
        const val DEFAULT_CLOSE_DELAY_MS = 90_000L
        const val DEFAULT_OPEN_DELAY_MS = 10_000L
        const val OPEN_GAP_MAX_MS = 15_000L
        const val MIN_OPEN_SAMPLES = 3
        const val UNKNOWN_CLOSE_MS = 300_000L
        const val GAP_RESTARTS_MS = 30_000L
        private const val PREFS = "netra_speed_gate"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_OPEN_DELAY_MS = "open_delay_ms"

        fun isEnabled(context: Context): Boolean =
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ENABLED, true)

        fun setEnabled(context: Context, enabled: Boolean) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_ENABLED, enabled).apply()
        }

        /**
         * How long speed must stay at/above the threshold before driving mode opens (default 10 s, clamped 5..60 s).
         * Internal configuration read once when the sensor pipeline starts; there is no user-facing control for it.
         */
        fun openDelayMs(context: Context): Long =
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(KEY_OPEN_DELAY_MS, DEFAULT_OPEN_DELAY_MS).coerceIn(5_000L, 60_000L)

        fun setOpenDelayMs(context: Context, ms: Long) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putLong(KEY_OPEN_DELAY_MS, ms).apply()
        }
    }
}
