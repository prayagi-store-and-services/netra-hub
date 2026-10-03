package com.example.data.sensor

import android.content.Context

/**
 * Speed gate for the always-on safety sensors. The sensors listen only while the phone is moving at or above
 * [thresholdKmH] (default 30 km/h), so walking never keeps them running. The gate closes only after the speed has been
 * below the threshold for [closeDelayMs], so a short stop at a traffic light does not switch the sensors off and on.
 * Unknown speed (no GPS fix, no permission) counts as below the threshold: nothing is guessed.
 * Sensor screens the user has open still show live data; the gate only controls the background listening.
 */
class SpeedGate(
    private val thresholdKmH: Float = DEFAULT_THRESHOLD_KMH,
    private val closeDelayMs: Long = DEFAULT_CLOSE_DELAY_MS
) {
    var isOpen: Boolean = false
        private set
    private var belowSinceMs: Long = -1L

    /** Feed a speed reading (null = unknown). Returns true when the gate changed state. */
    fun update(speedKmH: Float?, nowMs: Long): Boolean {
        val before = isOpen
        if (speedKmH != null && speedKmH >= thresholdKmH) {
            isOpen = true
            belowSinceMs = -1L
        } else if (isOpen) {
            if (belowSinceMs < 0) belowSinceMs = nowMs
            if (nowMs - belowSinceMs >= closeDelayMs) {
                isOpen = false
                belowSinceMs = -1L
            }
        }
        return before != isOpen
    }

    fun reset() {
        isOpen = false
        belowSinceMs = -1L
    }

    companion object {
        const val DEFAULT_THRESHOLD_KMH = 30f
        const val DEFAULT_CLOSE_DELAY_MS = 30_000L
        private const val PREFS = "netra_speed_gate"
        private const val KEY_ENABLED = "enabled"

        fun isEnabled(context: Context): Boolean =
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ENABLED, true)

        fun setEnabled(context: Context, enabled: Boolean) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_ENABLED, enabled).apply()
        }
    }
}
