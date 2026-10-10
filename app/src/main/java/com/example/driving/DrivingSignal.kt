package com.example.driving

import android.content.Context

/**
 * The driving flag other Netra apps on the same phone can read. It holds only "driving or not" and when it was set.
 * It is kept in this app's private storage and is never sent over the network. If Hub stops updating it, it goes stale
 * after [FRESH_MS] and reads as "not driving", so a stuck flag can never pause another app for long.
 */
object DrivingSignal {
    const val PREFS = "netra_driving_signal"
    const val FRESH_MS = 2L * 60 * 1000

    fun publish(context: Context, driving: Boolean, now: Long = System.currentTimeMillis()) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean("driving", driving).putLong("at", now).apply()
    }

    fun isDriving(context: Context, now: Long = System.currentTimeMillis()): Boolean {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return effective(p.getBoolean("driving", false), p.getLong("at", 0L), now)
    }

    fun since(context: Context): Long = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong("at", 0L)

    fun effective(driving: Boolean, at: Long, now: Long): Boolean = driving && at > 0L && now - at in 0..FRESH_MS
}
