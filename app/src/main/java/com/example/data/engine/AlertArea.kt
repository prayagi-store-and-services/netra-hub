package com.example.data.engine

import android.content.Context
import com.example.data.service.LocationAccess
import com.example.data.service.LocationContextInfo

/**
 * The area the user chose for official alerts (NDMA SACHET, USGS), saved on this phone only.
 * It is kept on this phone only. When no area is saved the workers keep their earlier behaviour (a fresh
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

    @PublishedApi internal val lock = Any()

    fun read(ctx: Context): SavedAlertArea? {
        val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val at = p.getLong("at", 0L)
        if (at == 0L) return null
        val lat = java.lang.Double.longBitsToDouble(p.getLong("lat", 0L))
        val lon = java.lang.Double.longBitsToDouble(p.getLong("lon", 0L))
        if (!plausible(lat, lon)) return null
        return SavedAlertArea(lat, lon, p.getString("loc", "") ?: "", p.getString("dist", "") ?: "", p.getString("state", "") ?: "", at)
    }

    /**
     * Final step of a worker run: under the same lock as save/clear, check the area version is still the one
     * the run started with, then dispatch, notify and commit state/status. If the area changed the block does
     * not run at all. Callers must do location and network work BEFORE this, never inside it. A notification
     * or sound already handed to the OS before an area change can still arrive afterwards.
     */
    inline fun commitIfCurrent(ctx: Context, started: Long, block: () -> Unit): Boolean =
        commitIfCurrent(started, { version(ctx) }, block)

    /** Same coordination with an injectable version source, so the orderings can be tested without Android. */
    inline fun commitIfCurrent(started: Long, current: () -> Long, block: () -> Unit): Boolean = synchronized(lock) {
        if (changedSince(started, current())) false else { block(); true }
    }

    /** The one lock every area mutation (save, clear) and every worker commit goes through. */
    inline fun <T> underLock(block: () -> T): T = synchronized(lock) { block() }

    /** Bumped on every save/clear so a worker that started under an older area can tell and stop. */
    fun version(ctx: Context): Long = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong("ver", 0L)

    /** True when the area changed after a worker run started: that run must not announce or write state. */
    fun changedSince(started: Long, current: Long) = started != current

    /**
     * Seen/active alert memory is scoped to the area version. A save or clear publishes a new version
     * in one prefs edit, so the new area starts with empty memory and a worker still running for the
     * old area can only write under the old version's keys, never into the new area's memory.
     * Version 0 (no area ever saved) keeps the original key names.
     */
    fun scoped(baseKey: String, version: Long): String = if (version == 0L) baseKey else baseKey + "_a" + version

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

    /** The user's explicit choice: official alert checks use the saved area (true) or a fresh location each check (false, the default). */
    fun useSaved(ctx: Context): Boolean = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("use_saved", false)

    /** Switch the choice. The basis of matching changes, so alert memory restarts (new version), like a save or clear. */
    fun setUseSaved(ctx: Context, on: Boolean): Boolean = synchronized(lock) {
        if (on && read(ctx) == null) return@synchronized false
        if (useSaved(ctx) == on) return@synchronized true
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putLong("ver", version(ctx) + 1).putBoolean("use_saved", on).apply()
        true
    }

    /** One line that names exactly what the automatic checks use. */
    fun modeText(area: SavedAlertArea?, useSaved: Boolean): String = when {
        useSaved && area != null -> "Automatic official alert checks use: your saved area (" + placeText(area) + ")."
        else -> "Automatic official alert checks use: your current location, read fresh at each check."
    }

    /** Saving a new area from a successful foreground fix also selects it. */
    fun save(ctx: Context, a: SavedAlertArea) = synchronized(lock) {
        val ver = version(ctx) + 1
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putLong("ver", ver).putBoolean("use_saved", true)
            .putLong("lat", java.lang.Double.doubleToRawLongBits(a.latitude))
            .putLong("lon", java.lang.Double.doubleToRawLongBits(a.longitude))
            .putString("loc", a.locality).putString("dist", a.district).putString("state", a.state)
            .putLong("at", a.savedAtMillis).apply()
    }

    fun clear(ctx: Context) = synchronized(lock) {
        val ver = version(ctx) + 1
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().putLong("ver", ver).apply()
    }

    /** A saved area as the location info the workers already understand, or null when none is saved. */
    fun asLocation(ctx: Context): LocationContextInfo? {
        if (!useSaved(ctx)) return null
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

    fun staleNote(a: SavedAlertArea, now: Long, useSaved: Boolean = true): String? = when {
        !isStale(a, now) -> null
        useSaved -> "This area was saved " + ageDays(a, now) + " days ago. If you have moved, tap Update area; alerts are matched to this saved area."
        else -> "The saved area is " + ageDays(a, now) + " days old and is not in use right now: automatic checks read your current location."
    }
}
