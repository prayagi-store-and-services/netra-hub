package com.example.data.sensor

/**
 * Decides whether a location fix's speed can be trusted as a real speed. A phone lying still indoors still gets
 * GPS fixes, and their reported speed can jump far above zero for a moment (multipath, cell/Wi-Fi re-anchoring).
 * Feeding such spikes to the driving gate made driving mode turn ON and OFF on a motionless phone. A fix whose
 * position accuracy is coarse, or whose own speed error estimate is large, is treated as "speed unknown" instead
 * of a real speed. Nothing is guessed; unknown speed can never open the gate.
 */
object SpeedTrust {
    const val MAX_ACCURACY_M = 50f
    const val MAX_SPEED_ACCURACY_MPS = 4f

    const val MAX_PLAUSIBLE_KMH = 200f

    /**
     * Position-accuracy and (when the device supplies one, API 26+) speed-error checks. A device that cannot
     * report a speed error estimate at all (missing, not negative) stays usable: the sustained-speed gate window,
     * accuracy bound and plausibility cap still apply. A supplied but invalid estimate (negative or non-finite)
     * is a broken reading and rejects.
     */
    fun trusted(accuracyM: Float, hasSpeedAccuracy: Boolean, speedAccuracyMps: Float): Boolean {
        if (!(accuracyM > 0f) || accuracyM > MAX_ACCURACY_M || !accuracyM.isFinite()) return false
        if (hasSpeedAccuracy && (!(speedAccuracyMps >= 0f) || !speedAccuracyMps.isFinite() || speedAccuracyMps > MAX_SPEED_ACCURACY_MPS)) return false
        return true
    }

    /** A NaN, infinite, negative or implausibly high reading is UNKNOWN, never a real (low or high) speed. */
    fun cleanSpeed(speedKmH: Float?): Float? = speedKmH?.takeIf { it.isFinite() && it in 0f..MAX_PLAUSIBLE_KMH }
}
