package com.example.data.engine

import android.content.Context
import com.example.data.service.LocationAccess
import com.example.data.service.LocationContextInfo

/**
 * The area the user chose for official alerts (NDMA SACHET, USGS), saved on this phone only.
 * It is never uploaded. When no area is saved the workers keep their earlier behaviour (a fresh
 * location fix on every run). The user can update or clear it at any time.
 */
data class SavedAlertArea(
    val latitude: Double, val longitude: Double,
    val locality: String, val district: String, val state: String,
    val savedAtMillis: Long
)

object AlertArea {
    const val PREFS = "netra_alert_area"
    const val STALE_DAYS = 14L

    fun read(ctx: Context): SavedAlertArea? {
        val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val at = p.getLong("at", 0L)
        if (at == 0L) return null
        val lat = java.lang.Double.longBitsToDouble(p.getLong("lat", 0L))
        val lon = java.lang.Double.longBitsToDouble(p.getLong("lon", 0L))
        if (!plausible(lat, lon)) return null
        return SavedAlertArea(lat, lon, p.getString("loc", "") ?: "", p.getString("dist", "") ?: "", p.getString("state", "") ?: "", at)
    }

    /** Bumped on every save/clear so a worker that started under an older area can tell and stop. */
    fun version(ctx: Context): Long = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong("ver", 0L)

    /** True when the area changed after a worker run started: that run must not announce or write state. */
    fun changedSince(started: Long, current: Long) = started != current

    /**
     * Alerts seen or active under the old area say nothing about the new one: forget them, without a
     * "warning over" for the old area. Alerts matching the new area announce once on the next check.
     */
    private fun resetAlertMemory(ctx: Context) {
        ctx.getSharedPreferences(SachetWatchWorker.PREFS, Context.MODE_PRIVATE).edit()
            .remove(SachetWatchWorker.KEY_SEEN).remove(SachetWatchWorker.KEY_ACTIVE).apply()
        ctx.getSharedPreferences(QuakeWatchWorker.PREFS, Context.MODE_PRIVATE).edit()
            .remove(QuakeWatchWorker.KEY_SEEN).apply()
    }

    /** USGS summary feed for the last day at or below the user's minimum magnitude. No coordinates are sent. */
    fun usgsDayFeedUrl(minimumMagnitude: Double): String {
        val bucket = when {
            minimumMagnitude >= 4.5 -> "4.5"
            minimumMagnitude >= 2.5 -> "2.5"
            minimumMagnitude >= 1.0 -> "1.0"
            else -> "all"
        }
        return "https://earthquake.usgs.gov/earthquakes/feed/v1.0/summary/" + bucket + "_day.geojson"
    }

    fun save(ctx: Context, a: SavedAlertArea) {
        val ver = version(ctx) + 1
        resetAlertMemory(ctx)
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putLong("ver", ver)
            .putLong("lat", java.lang.Double.doubleToRawLongBits(a.latitude))
            .putLong("lon", java.lang.Double.doubleToRawLongBits(a.longitude))
            .putString("loc", a.locality).putString("dist", a.district).putString("state", a.state)
            .putLong("at", a.savedAtMillis).apply()
    }

    fun clear(ctx: Context) {
        val ver = version(ctx) + 1
        resetAlertMemory(ctx)
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().putLong("ver", ver).apply()
    }

    /** A saved area as the location info the workers already understand, or null when none is saved. */
    fun asLocation(ctx: Context): LocationContextInfo? {
        val a = read(ctx) ?: return null
        return LocationContextInfo(
            latitude = a.latitude, longitude = a.longitude, timestamp = a.savedAtMillis,
            locality = a.locality.ifBlank { "Location Unavailable" },
            district = a.district.ifBlank { "Location Unavailable" },
            state = a.state.ifBlank { "Location Unavailable" },
            source = "SAVED_AREA", locationStatus = "LOCATION_AVAILABLE"
        )
    }

    fun plausible(lat: Double, lon: Double) =
        lat in -90.0..90.0 && lon in -180.0..180.0 && !(lat == 0.0 && lon == 0.0)

    fun placeText(a: SavedAlertArea): String =
        LocationAccess.placeText(a.locality, a.district, a.state).let {
            if (it == "Unavailable") String.format(java.util.Locale.US, "%.2f, %.2f", a.latitude, a.longitude) else it
        }

    fun ageDays(a: SavedAlertArea, now: Long): Long = ((now - a.savedAtMillis).coerceAtLeast(0L)) / 86_400_000L

    fun isStale(a: SavedAlertArea, now: Long) = ageDays(a, now) >= STALE_DAYS

    fun staleNote(a: SavedAlertArea, now: Long): String? =
        if (isStale(a, now)) "This area was saved " + ageDays(a, now) + " days ago. If you have moved, tap Update area; alerts are still matched to the saved area." else null
}
