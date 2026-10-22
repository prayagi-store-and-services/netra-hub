package com.example.sos

/**
 * Finds where the vehicle really parked during a drive, from real GPS fixes only (nothing guessed or extrapolated).
 *
 * A stop counts only when the phone stays under [STOP_KMH] for at least [MIN_RUN_MS] over at least [MIN_FIXES] fixes
 * that are accurate to [MAX_FIX_ACCURACY_M] or better. The spot is then the middle (median) of the fixes that agree with
 * each other within [CLUSTER_M], so one wild fix cannot move it. Once confirmed, the stop is kept while the phone is only
 * walking (under [DRIVE_KMH]); it is dropped when the vehicle moves off at [DRIVE_KMH] or more, and replaced by a later
 * confirmed stop. Order and age use the phone's monotonic clock (milliseconds since boot), never the wall clock.
 * Pure logic, unit tested.
 */
class StopTracker {
    /** at = monotonic ms of the fix (order and age). wallAt = wall-clock ms of the fix, for display only. */
    data class Fix(val lat: Double, val lon: Double, val accuracyM: Float, val at: Long, val wallAt: Long = at)

    companion object {
        const val STOP_KMH = 3f
        const val DRIVE_KMH = 15f
        const val MIN_RUN_MS = 10_000L
        const val MIN_FIXES = 2
        const val MAX_FIX_ACCURACY_M = 50f
        const val CLUSTER_M = 30.0
        private const val MAX_RUN = 24
    }

    /** The confirmed parked spot, or null. */
    var candidate: Fix? = null; private set
    /** The latest fix seen (any speed). Shown in the status text only, never saved without a confirmed stop. */
    var last: Fix? = null; private set
    private var moved = false
    private val run = ArrayList<Fix>()

    fun reset() { candidate = null; last = null; moved = false; run.clear() }

    /** kmh null = no usable speed: remembered as the latest position, says nothing about stopping. */
    fun onFix(lat: Double, lon: Double, accuracyM: Float, at: Long, wallAt: Long, kmh: Float?) {
        if (lat == 0.0 && lon == 0.0) return
        val prev = last
        if (prev != null && at <= prev.at) return // same fix again, or out of order
        val f = Fix(lat, lon, accuracyM, at, wallAt)
        last = f
        if (kmh == null) return
        when {
            kmh >= DRIVE_KMH -> { moved = true; candidate = null; run.clear() }
            kmh < STOP_KMH && moved -> {
                run.add(f); if (run.size > MAX_RUN) run.removeAt(0)
                confirm()
            }
            else -> run.clear() // 3 to 15 km/h: crawling or walking, not a stop run
        }
    }

    private fun confirm() {
        val good = run.filter { it.accuracyM > 0f && it.accuracyM <= MAX_FIX_ACCURACY_M }
        if (good.size < MIN_FIXES || good.last().at - good.first().at < MIN_RUN_MS) return
        val mLat = median(good.map { it.lat }); val mLon = median(good.map { it.lon })
        val agree = good.filter { meters(it.lat, it.lon, mLat, mLon) <= CLUSTER_M }
        if (agree.size < MIN_FIXES || agree.last().at - agree.first().at < MIN_RUN_MS) return
        val cLat = median(agree.map { it.lat }); val cLon = median(agree.map { it.lon })
        val mid = agree[agree.size / 2]
        candidate = Fix(cLat, cLon, agree.maxOf { it.accuracyM }, mid.at, mid.wallAt)
    }

    private fun median(v: List<Double>): Double { val s = v.sorted(); return if (s.size % 2 == 1) s[s.size / 2] else (s[s.size / 2 - 1] + s[s.size / 2]) / 2.0 }

    private fun meters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1) * 6371000.0
        val dLon = Math.toRadians(lon2 - lon1) * 6371000.0 * Math.cos(Math.toRadians((lat1 + lat2) / 2))
        return Math.sqrt(dLat * dLat + dLon * dLon)
    }
}
