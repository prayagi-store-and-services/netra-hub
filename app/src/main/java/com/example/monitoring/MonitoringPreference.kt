package com.example.monitoring

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/** Explicit sensor-monitoring choice, independent of permissions and automatic recovery.
 * Only a deliberate UI action may change this value; startup paths only read it.
 * Default preserves existing installations until a user pauses monitoring.
 */
object MonitoringPreference {
    internal const val PREFS = "netra_monitoring_control"
    internal const val KEY_ENABLED = "sensors_enabled"

    fun isEnabled(context: Context): Boolean = context.applicationContext
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ENABLED, true)

    /** Registration precedes the first read, so a resume cannot be lost while entering a wait. */
    fun enabledFlow(context: Context) = callbackFlow {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == KEY_ENABLED) trySend(isEnabled(context))
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        trySend(isEnabled(context))
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }.distinctUntilChanged()

    /** Persist before any start/stop side effect. A failed write must not report success. */
    @Synchronized
    fun setByUser(context: Context, enabled: Boolean): Boolean = context.applicationContext
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
        .putBoolean(KEY_ENABLED, enabled).commit()
    /** Short publication-only action, serialized with user choice writes. Never call sensor locks here. */
    @Synchronized
    fun publishIfEnabled(context: Context, publication: () -> Unit): Boolean {
        if (!isEnabled(context)) return false
        publication()
        return true
    }
}
