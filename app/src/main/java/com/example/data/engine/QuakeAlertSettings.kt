package com.example.data.engine

import android.content.Context

/** Device-local filters. No preferences or changes are uploaded. */
data class QuakeAlertSettings(val enabled: Boolean = true, val distanceKm: Int = 200, val minimumMagnitude: Double = 2.5) {
    fun accepts(distance: Double, magnitude: Double): Boolean =
        enabled && distance.isFinite() && distance >= 0 && distance <= distanceKm &&
            magnitude.isFinite() && magnitude >= minimumMagnitude

    companion object {
        val distances = listOf(100, 200, 250, 500, 1000)
        val magnitudes = (0..11).map { it / 2.0 }
        fun validated(enabled: Boolean, distance: Int, magnitude: Double) = QuakeAlertSettings(
            enabled, distance.takeIf { it in distances } ?: 200,
            magnitude.takeIf { it.isFinite() && it in magnitudes } ?: 2.5
        )
        fun read(context: Context): QuakeAlertSettings {
            val p = context.getSharedPreferences(QuakeWatchWorker.PREFS, Context.MODE_PRIVATE)
            return validated(p.getBoolean("alerts_enabled", true), p.getInt("distance_km", 200),
                p.getFloat("minimum_magnitude", 2.5f).toDouble())
        }
        fun write(context: Context, settings: QuakeAlertSettings) {
            val s = validated(settings.enabled, settings.distanceKm, settings.minimumMagnitude)
            context.getSharedPreferences(QuakeWatchWorker.PREFS, Context.MODE_PRIVATE).edit()
                .putBoolean("alerts_enabled", s.enabled).putInt("distance_km", s.distanceKm)
                .putFloat("minimum_magnitude", s.minimumMagnitude.toFloat()).apply()
        }
    }
}
