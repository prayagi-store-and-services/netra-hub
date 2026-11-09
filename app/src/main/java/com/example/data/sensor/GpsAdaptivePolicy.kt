package com.example.data.sensor

/**
 * Pure interval policy for adaptive location sampling (battery). While driving mode is on, fixes stay frequent
 * so the speed gate keeps fresh evidence. While the phone is not driving, fixes slow down - but never beyond
 * [SpeedGate.OPEN_GAP_MAX_MS], because a wider gap would make the gate's sustained-speed window unachievable
 * and silently disable driving detection. Nothing is guessed: a stationary phone still gets fixes, just fewer.
 */
object GpsAdaptivePolicy {
    const val DRIVING_INTERVAL_MS = 5_000L
    const val DRIVING_MIN_INTERVAL_MS = 3_000L
    const val STATIONARY_INTERVAL_MS = 15_000L
    const val STATIONARY_MIN_INTERVAL_MS = 10_000L

    fun intervalMs(driving: Boolean): Long = if (driving) DRIVING_INTERVAL_MS else STATIONARY_INTERVAL_MS
    fun minIntervalMs(driving: Boolean): Long = if (driving) DRIVING_MIN_INTERVAL_MS else STATIONARY_MIN_INTERVAL_MS

    /** Invariant the whole policy depends on: stationary sampling never exceeds the gate's max evidence gap. */
    fun preservesDrivingDetection(): Boolean = STATIONARY_INTERVAL_MS <= SpeedGate.OPEN_GAP_MAX_MS
}
