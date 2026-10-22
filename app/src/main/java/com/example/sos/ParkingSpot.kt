package com.example.sos

import android.content.Context
import com.example.share.LocationShare

/**
 * Optional parking spot memory. OFF by default. When on, the phone saves ONE spot (latitude, longitude, time) in this app's
 * private storage when driving ends. No history, never sent anywhere. Driving detection is a sensor guess, so the spot can
 * be wrong or missed. If there is no real location fix, nothing is saved and the screen says so.
 */
object ParkingSpot {
    private const val FILE = "parking_prefs"
    private fun p(c: Context) = c.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun enabled(c: Context) = p(c).getBoolean("enabled", false)
    fun setEnabled(c: Context, on: Boolean) { p(c).edit().putBoolean("enabled", on).apply(); if (!on) clear(c) }

    /** at = time of the GPS fix itself (not the time it was saved). accuracyM < 0 = unknown (saved by an older version). */
    class Spot(val lat: Double, val lon: Double, val at: Long, val accuracyM: Float = -1f)

    fun get(c: Context): Spot? {
        val s = p(c)
        if (!s.contains("lat")) return null
        return Spot(java.lang.Double.longBitsToDouble(s.getLong("lat", 0L)), java.lang.Double.longBitsToDouble(s.getLong("lon", 0L)), s.getLong("at", 0L), s.getFloat("acc", -1f))
    }

    fun status(c: Context): String = p(c).getString("status", "") ?: ""

    /** Replaces the single saved spot. */
    fun save(c: Context, lat: Double, lon: Double, at: Long, accuracyM: Float = -1f) {
        p(c).edit().putLong("lat", java.lang.Double.doubleToLongBits(lat)).putLong("lon", java.lang.Double.doubleToLongBits(lon))
            .putLong("at", at).putFloat("acc", accuracyM).putString("status", "").apply()
    }

    const val GOOD_ACCURACY_M = 50f
    const val MAX_ACCURACY_M = 200f
    const val STOP_FIX_MAX_AGE_MS = 15 * 60 * 1000L
    const val LAST_FIX_MAX_AGE_MS = 120 * 1000L

    class Decision(val fix: StopTracker.Fix?, val status: String)

    /**
     * Picks what to save when driving ends. Order: the fix where the vehicle stopped, else the latest GPS fix if it is under
     * two minutes old. A fix worse than 200 m is never saved. A fix between 50 and 200 m is saved but labelled as rough.
     */
    fun decide(candidate: StopTracker.Fix?, last: StopTracker.Fix?, now: Long): Decision {
        fun ok(f: StopTracker.Fix?, maxAge: Long) = f != null && now - f.at in -60_000L..maxAge
        val pick = when {
            ok(candidate, STOP_FIX_MAX_AGE_MS) && candidate!!.accuracyM in 0.1f..MAX_ACCURACY_M -> candidate
            ok(last, LAST_FIX_MAX_AGE_MS) && last!!.accuracyM in 0.1f..MAX_ACCURACY_M -> last
            else -> null
        }
        if (pick != null) return Decision(pick, "")
        val best = last ?: candidate
        return Decision(null, if (best == null) "Not saved: no GPS fix during this drive"
            else "Not saved: the best GPS fix was %.0f m accurate and %d s old (limit %.0f m, and a stop fix under 15 min or a latest fix under 2 min)".format(best.accuracyM, ((now - best.at) / 1000).coerceAtLeast(0), MAX_ACCURACY_M))
    }

    fun accuracyText(acc: Float): String = when {
        acc < 0f -> "accuracy unknown (saved by an older version)"
        acc <= GOOD_ACCURACY_M -> "about %.0f m accurate".format(acc)
        else -> "rough fix, only about %.0f m accurate, the spot may be off by that much".format(acc)
    }

    /** Called once when driving mode ends. Saves the stop fix, or records honestly why nothing was saved. */
    fun onDrivingEnded(c: Context, t: StopTracker, now: Long = System.currentTimeMillis()) {
        if (!enabled(c)) return
        val d = decide(t.candidate, t.last, now)
        val f = d.fix
        if (f != null) save(c, f.lat, f.lon, f.at, f.accuracyM) else p(c).edit().putString("status", d.status).apply()
    }

    fun markUnavailable(c: Context) { p(c).edit().putString("status", "Not saved: location unavailable").apply() }

    fun clear(c: Context) { p(c).edit().remove("lat").remove("lon").remove("at").putString("status", "").apply() }

    /** True when driving just ended and the user turned this on. */
    fun shouldSave(wasDriving: Boolean, nowDriving: Boolean, enabled: Boolean): Boolean = enabled && wasDriving && !nowDriving

    /** A fix is usable only with real coordinates that are not older than 15 minutes. */
    fun usable(lat: Double, lon: Double, fixAt: Long, now: Long = System.currentTimeMillis()): Boolean = LocationShare.hasFix(lat, lon, fixAt, now)

    fun mapsUri(lat: Double, lon: Double): String = "geo:%.6f,%.6f?q=%.6f,%.6f(Parked)".format(java.util.Locale.US, lat, lon, lat, lon)
}
