package com.example.data.sensor

import android.content.Context

/**
 * Speed gate for the always-on safety sensors. The sensors listen only while the phone is moving at or above
 * [thresholdKmH] (default 15 km/h), so walking never keeps them running. The gate closes only after the speed has been
 * below the threshold for [closeDelayMs], so a short stop at a traffic light does not switch the sensors off and on.
 * Unknown speed (no GPS fix, no permission) is not evidence of slowing down: it never advances that timer, and
 * only a long blind spell (UNKNOWN_CLOSE_MS) closes the gate. Nothing is guessed.
 * Sensor screens the user has open still show live data; the gate only controls the background listening.
 */
class SpeedGate(
    private val thresholdKmH: Float = DEFAULT_THRESHOLD_KMH,
    private val closeDelayMs: Long = DEFAULT_CLOSE_DELAY_MS
) {
    var isOpen: Boolean = false
        private set
    /** True when the gate last closed because GPS gave no speed for a long time (not because speed was low). */
    var closedByBlindSpell: Boolean = false
        private set
    private var belowSinceMs: Long = -1L
    private var unknownSinceMs: Long = -1L
    private var lastValidMs: Long = 0L
    private var blindSinceLastValid = false

    /** Feed a speed reading (null = unknown). Returns true when the gate changed state. */
    fun update(speedKmH: Float?, nowMs: Long): Boolean {
        val before = isOpen
        if (speedKmH == null) {
            // Unknown speed is NOT evidence of slowing down: it never advances the close timer.
            // Only a long blind spell (GPS lost) closes the gate, so a stuck ON state cannot last.
            blindSinceLastValid = true
            if (isOpen) {
                if (unknownSinceMs < 0) unknownSinceMs = nowMs
                if (nowMs - unknownSinceMs >= UNKNOWN_CLOSE_MS) { isOpen = false; closedByBlindSpell = true; belowSinceMs = -1L; unknownSinceMs = -1L }
            }
            return before != isOpen
        }
        unknownSinceMs = -1L
        if (speedKmH >= thresholdKmH) {
            isOpen = true
            belowSinceMs = -1L
        } else if (isOpen) {
            // A blind gap breaks the run of low readings: the timer restarts after it.
            if (belowSinceMs < 0 || (blindSinceLastValid && nowMs - lastValidMs > GAP_RESTARTS_MS)) belowSinceMs = nowMs
            if (nowMs - belowSinceMs >= closeDelayMs) {
                isOpen = false
                closedByBlindSpell = false
                belowSinceMs = -1L
            }
        }
        blindSinceLastValid = false
        lastValidMs = nowMs
        return before != isOpen
    }

    fun reset() {
        isOpen = false
        belowSinceMs = -1L
        unknownSinceMs = -1L
        lastValidMs = 0L
        blindSinceLastValid = false
    }

    companion object {
        const val DEFAULT_THRESHOLD_KMH = 15f
        const val DEFAULT_CLOSE_DELAY_MS = 90_000L
        const val UNKNOWN_CLOSE_MS = 300_000L
        const val GAP_RESTARTS_MS = 30_000L
        private const val PREFS = "netra_speed_gate"
        private const val KEY_ENABLED = "enabled"

        fun isEnabled(context: Context): Boolean =
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ENABLED, true)

        fun setEnabled(context: Context, enabled: Boolean) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_ENABLED, enabled).apply()
        }
    }
}
