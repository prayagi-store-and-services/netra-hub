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

    /** Scalar-only provenance for bounded local decision facts. Never carries coordinates. Shared by
     * cached/live Android Location paths so filtering does not erase the original rejected measurement. */
    fun diagnosticFacts(rawSpeedKmH: Float?, accuracyM: Float, hasSpeedAccuracy: Boolean,
                        speedAccuracyMps: Float): Map<String, String> {
        val rejection = when {
            rawSpeedKmH == null -> "missing_speed"
            cleanSpeed(rawSpeedKmH) == null -> "invalid_or_implausible_speed"
            !trusted(accuracyM, false, 0f) -> "invalid_position_accuracy"
            !trusted(accuracyM, hasSpeedAccuracy, speedAccuracyMps) -> "invalid_speed_accuracy"
            else -> "none"
        }
        return mapOf("originalSpeedKmH" to (rawSpeedKmH?.toString() ?: "missing"),
            "hasSpeedAccuracy" to hasSpeedAccuracy.toString(),
            "originalSpeedAccuracyMps" to if (hasSpeedAccuracy) speedAccuracyMps.toString() else "missing",
            "upstreamRejection" to rejection)
    }

    /** A NaN, infinite, negative or implausibly high reading is UNKNOWN, never a real (low or high) speed. */
    fun cleanSpeed(speedKmH: Float?): Float? = speedKmH?.takeIf { it.isFinite() && it in 0f..MAX_PLAUSIBLE_KMH }
}
