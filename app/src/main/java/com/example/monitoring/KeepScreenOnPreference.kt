package com.example.monitoring

import android.app.Activity
import android.content.Context
import android.view.WindowManager

/**
 * User-controlled "keep screen on" choice. Default is OFF: keeping the screen on permanently drains the
 * battery, so the app never forces it. Only a deliberate UI action may enable it; startup paths only read it.
 */
object KeepScreenOnPreference {
    internal const val PREFS = "netra_keep_screen_on"
    internal const val KEY_ENABLED = "enabled"

    fun isEnabled(context: Context): Boolean = context.applicationContext
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ENABLED, false)

    /** Persist before any window-flag side effect. A failed write must not report success. */
    @Synchronized
    fun setByUser(context: Context, enabled: Boolean): Boolean = context.applicationContext
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
        .putBoolean(KEY_ENABLED, enabled).commit()

    /** Applies or clears the flag on the given activity's window to match the stored choice. */
    fun apply(activity: Activity) {
        if (isEnabled(activity)) activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
}
