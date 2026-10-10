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

    class Spot(val lat: Double, val lon: Double, val at: Long)

    fun get(c: Context): Spot? {
        val s = p(c)
        if (!s.contains("lat")) return null
        return Spot(java.lang.Double.longBitsToDouble(s.getLong("lat", 0L)), java.lang.Double.longBitsToDouble(s.getLong("lon", 0L)), s.getLong("at", 0L))
    }

    fun status(c: Context): String = p(c).getString("status", "") ?: ""

    /** Replaces the single saved spot. */
    fun save(c: Context, lat: Double, lon: Double, at: Long) {
        p(c).edit().putLong("lat", java.lang.Double.doubleToLongBits(lat)).putLong("lon", java.lang.Double.doubleToLongBits(lon))
            .putLong("at", at).putString("status", "").apply()
    }

    fun markUnavailable(c: Context) { p(c).edit().putString("status", "Not saved: location unavailable").apply() }

    fun clear(c: Context) { p(c).edit().remove("lat").remove("lon").remove("at").putString("status", "").apply() }

    /** True when driving just ended and the user turned this on. */
    fun shouldSave(wasDriving: Boolean, nowDriving: Boolean, enabled: Boolean): Boolean = enabled && wasDriving && !nowDriving

    /** A fix is usable only with real coordinates that are not older than 15 minutes. */
    fun usable(lat: Double, lon: Double, fixAt: Long, now: Long = System.currentTimeMillis()): Boolean = LocationShare.hasFix(lat, lon, fixAt, now)

    fun mapsUri(lat: Double, lon: Double): String = "geo:%.6f,%.6f?q=%.6f,%.6f(Parked)".format(java.util.Locale.US, lat, lon, lat, lon)
}
