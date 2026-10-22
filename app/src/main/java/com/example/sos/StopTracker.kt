package com.example.sos

/**
 * Remembers where the vehicle actually stopped during a drive: the first GPS fix of the latest stationary run
 * (speed under [STOP_KMH] after the vehicle had been moving). Uses only real GPS fixes with their own time and accuracy;
 * nothing is guessed or extrapolated. Pure logic, unit tested.
 */
class StopTracker {
    data class Fix(val lat: Double, val lon: Double, val accuracyM: Float, val at: Long)

    companion object { const val STOP_KMH = 5f }

    var candidate: Fix? = null; private set
    var last: Fix? = null; private set
    private var moved = false

    fun reset() { candidate = null; last = null; moved = false }

    /** kmh null = no usable speed: the fix is remembered as the latest position but says nothing about stopping. */
    fun onFix(lat: Double, lon: Double, accuracyM: Float, at: Long, kmh: Float?) {
        if (lat == 0.0 && lon == 0.0) return
        val prev = last
        if (prev != null && at <= prev.at) return // the same fix again
        val f = Fix(lat, lon, accuracyM, at)
        last = f
        if (kmh == null) return
        if (kmh >= STOP_KMH) { moved = true; candidate = null }
        else if (moved && candidate == null) candidate = f
    }
}
