package com.example.data.engine

import android.content.Context

/**
 * Runs the official-alert checks (NDMA SACHET + USGS quake watch) immediately instead of waiting
 * for the 30-minute cadence: when the phone gets a fresh location fix, and right after location
 * permission is granted. Throttled so a stream of fixes cannot start a storm of checks.
 * Location never leaves the phone; the checks themselves read the public official feeds.
 */
object SafetyCheckTrigger {
    const val PREFS = "safety_check_trigger"
    const val KEY_LAST_TRIGGER = "last_trigger_ms"

    /** At most one immediate round every 10 minutes from location fixes. */
    const val MIN_INTERVAL_MS: Long = 10L * 60_000L

    /** Pure: is an immediate round due given the last trigger time? */
    fun due(nowMillis: Long, lastTriggerMillis: Long): Boolean = nowMillis - lastTriggerMillis >= MIN_INTERVAL_MS

    /** Fresh fix arrived (from the live location observer). Throttled. */
    fun onFreshLocation(context: Context) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        if (!due(now, prefs.getLong(KEY_LAST_TRIGGER, 0L))) return
        prefs.edit().putLong(KEY_LAST_TRIGGER, now).apply()
        enqueueNow(context.applicationContext)
    }

    /** Permission just granted (or app started with it): first check at once, no throttle. */
    fun onPermissionGranted(context: Context) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit().putLong(KEY_LAST_TRIGGER, System.currentTimeMillis()).apply()
        enqueueNow(context.applicationContext)
    }

    private fun enqueueNow(context: Context) {
        SachetWatchWorker.enqueueNow(context)
        QuakeWatchWorker.enqueueNow(context)
    }
}
