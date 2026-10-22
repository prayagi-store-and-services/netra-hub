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

    const val GOOD_ACCURACY_M = 25f
    const val MAX_ACCURACY_M = 50f
    /** A confirmed stop is kept while the phone only walks; this is how old it may be when driving mode ends (monotonic ms). */
    const val STOP_MAX_AGE_MS = 10 * 60 * 1000L

    class Decision(val fix: StopTracker.Fix?, val status: String)

    /**
     * Picks what to save when driving ends. Only a confirmed stop (a sustained stationary run of accurate GPS fixes) is saved.
     * Without one, the earlier spot is kept and the status says why. [nowMono] is the monotonic clock (elapsedRealtime).
     */
    fun decide(candidate: StopTracker.Fix?, last: StopTracker.Fix?, nowMono: Long): Decision {
        if (candidate != null) {
            val age = nowMono - candidate.at
            if (age < -2_000L) return Decision(null, "Not saved: the GPS fix time was in the future, so it was not trusted")
            if (age > STOP_MAX_AGE_MS) return Decision(null, "Not saved: the last confirmed stop was %d min ago, too old".format(age / 60_000))
            if (candidate.accuracyM !in 0.1f..MAX_ACCURACY_M) return Decision(null, "Not saved: the stop fix was only %.0f m accurate (limit %.0f m)".format(candidate.accuracyM, MAX_ACCURACY_M))
            return Decision(candidate, "")
        }
        return Decision(null, if (last == null) "Not saved: no GPS fix during this drive"
            else "Not saved: no confirmed stop (the phone was not still for 10 s on accurate GPS fixes). The earlier spot is kept, if any")
    }

    fun accuracyText(acc: Float): String = when {
        acc < 0f -> "accuracy unknown (saved by an older version)"
        acc <= GOOD_ACCURACY_M -> "about %.0f m accurate".format(acc)
        else -> "fair fix, about %.0f m accurate (the spot may be off by that much)".format(acc)
    }

    /** Called once when driving mode ends. Saves the stop fix, or records honestly why nothing was saved. */
    fun onDrivingEnded(c: Context, t: StopTracker, nowMono: Long = android.os.SystemClock.elapsedRealtime()) {
        if (!enabled(c)) return
        val d = decide(t.candidate, t.last, nowMono)
        val f = d.fix
        if (f != null) save(c, f.lat, f.lon, f.wallAt, f.accuracyM) else p(c).edit().putString("status", d.status).apply()
    }

    fun markUnavailable(c: Context) { p(c).edit().putString("status", "Not saved: location unavailable").apply() }

    fun clear(c: Context) { p(c).edit().remove("lat").remove("lon").remove("at").putString("status", "").apply() }

    /** True when driving just ended and the user turned this on. */
    fun shouldSave(wasDriving: Boolean, nowDriving: Boolean, enabled: Boolean): Boolean = enabled && wasDriving && !nowDriving

    /** A fix is usable only with real coordinates that are not older than 15 minutes. */
    fun usable(lat: Double, lon: Double, fixAt: Long, now: Long = System.currentTimeMillis()): Boolean = LocationShare.hasFix(lat, lon, fixAt, now)

    fun mapsUri(lat: Double, lon: Double): String = "geo:%.6f,%.6f?q=%.6f,%.6f(Parked)".format(java.util.Locale.US, lat, lon, lat, lon)
}
