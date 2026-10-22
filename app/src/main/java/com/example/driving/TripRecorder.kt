package com.example.driving

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Turns GPS speed and accelerometer readings into driving log entries while driving mode is on: speed changes,
 * hard braking, and bumps (speed breaker or pothole, always labelled "likely": the phone cannot see the road).
 * Pure logic, no Android classes, so it is unit tested without a phone.
 */
class TripRecorder(private val emit: (kind: String, at: Long, speedKmH: Float?, text: String) -> Unit) {
    companion object {
        const val SPEED_STEP_KMH = 10f
        const val SPEED_MIN_GAP_MS = 2_000L
        const val BRAKE_DECEL_MS2 = 2.5f
        const val BRAKE_MIN_SPEED_KMH = 15f
        const val BRAKE_COOLDOWN_MS = 5_000L
        const val BRAKE_MAX_GAP_S = 8f
        const val BUMP_THRESHOLD_MS2 = 4.0f
        const val BUMP_WINDOW_MS = 250L
        const val BUMP_COOLDOWN_MS = 2_000L
        const val BUMP_MIN_SPEED_KMH = 8f
        const val SPEED_FRESH_MS = 10_000L
        const val BREAKER_LOOKBACK_MS = 6_000L
        const val BREAKER_DROP_KMH = 5f

        fun where(lat: Float, lon: Float): String =
            if (lat == 0f && lon == 0f) "location unavailable" else "at %.5f, %.5f".format(lat, lon)
    }

    private var lastLoggedKmh = -1f
    private var lastLoggedAt = 0L
    private var prevKmh = -1f
    private var prevAt = 0L
    private var prevLat = 0f
    private var prevLon = 0f
    private var lastBrakeAt = Long.MIN_VALUE / 2
    private var lastBumpAt = Long.MIN_VALUE / 2
    private var lat = 0f
    private var lon = 0f
    private val recent = ArrayDeque<Pair<Long, Float>>()
    private var gx = 0f; private var gy = 0f; private var gz = 0f; private var gravityReady = false
    private var windowStart = 0L
    private var windowPeak = 0f

    @Synchronized fun start(startKmh: Float?, now: Long) {
        lastLoggedKmh = startKmh ?: -1f; lastLoggedAt = now
        prevKmh = -1f; prevAt = 0L; lastBrakeAt = Long.MIN_VALUE / 2; lastBumpAt = Long.MIN_VALUE / 2
        lat = 0f; lon = 0f; prevLat = 0f; prevLon = 0f
        recent.clear(); gravityReady = false; windowStart = 0L; windowPeak = 0f
        if (startKmh != null) recent.addLast(now to startKmh)
    }

    /** One GPS reading. kmh null = no usable speed (nothing is guessed). */
    @Synchronized fun onSpeed(kmh: Float?, latitude: Float, longitude: Float, now: Long) {
        if (latitude != 0f || longitude != 0f) { lat = latitude; lon = longitude }
        if (kmh == null) return
        // The same fix can arrive twice (cached position plus live update): it is one sample, not two. now = the fix's own time.
        if (prevAt != 0L && (now <= prevAt || (kmh == prevKmh && latitude == prevLat && longitude == prevLon))) return
        recent.addLast(now to kmh)
        while (recent.isNotEmpty() && now - recent.first().first > BREAKER_LOOKBACK_MS) recent.removeFirst()
        var braked = false
        if (prevKmh >= BRAKE_MIN_SPEED_KMH && prevAt != 0L) {
            val dt = (now - prevAt) / 1000f
            if (dt in 0.8f..BRAKE_MAX_GAP_S) {
                val decel = (prevKmh - kmh) / 3.6f / dt
                if (decel >= BRAKE_DECEL_MS2 && now - lastBrakeAt >= BRAKE_COOLDOWN_MS) {
                    lastBrakeAt = now; braked = true
                    emit("BRAKE", now, kmh, "Hard braking: %.0f to %.0f km/h in %.0f s (average over the GPS interval, about %.1f m/s2; shorter braking between fixes is not visible), %s".format(prevKmh, kmh, dt, decel, where(lat, lon)))
                    lastLoggedKmh = kmh; lastLoggedAt = now
                }
            }
        }
        if (!braked && lastLoggedKmh >= 0f && abs(kmh - lastLoggedKmh) >= SPEED_STEP_KMH && now - lastLoggedAt >= SPEED_MIN_GAP_MS) {
            val word = if (kmh < lastLoggedKmh) "Speed dropped" else "Speed increased"
            emit("SPEED", now, kmh, "%s: %.0f to %.0f km/h, %s".format(word, lastLoggedKmh, kmh, where(lat, lon)))
            lastLoggedKmh = kmh; lastLoggedAt = now
        }
        if (lastLoggedKmh < 0f) { lastLoggedKmh = kmh; lastLoggedAt = now }
        prevKmh = kmh; prevAt = now; prevLat = latitude; prevLon = longitude
    }

    /** One accelerometer sample (m/s2, device axes, gravity included). tMs is wall-clock milliseconds. */
    @Synchronized fun onAccel(x: Float, y: Float, z: Float, tMs: Long) {
        if (!gravityReady) { gx = x; gy = y; gz = z; gravityReady = true; return }
        gx = 0.92f * gx + 0.08f * x; gy = 0.92f * gy + 0.08f * y; gz = 0.92f * gz + 0.08f * z
        val g = sqrt(gx * gx + gy * gy + gz * gz)
        if (g < 1f) return
        val vertical = ((x - gx) * gx + (y - gy) * gy + (z - gz) * gz) / g
        val mag = abs(vertical)
        if (windowStart != 0L) {
            if (mag > windowPeak) windowPeak = mag
            if (tMs - windowStart >= BUMP_WINDOW_MS) { finishBump(windowPeak, tMs); windowStart = 0L; windowPeak = 0f }
            return
        }
        if (mag >= BUMP_THRESHOLD_MS2 && tMs - lastBumpAt >= BUMP_COOLDOWN_MS) { windowStart = tMs; windowPeak = mag }
    }

    private fun finishBump(peak: Float, now: Long) {
        lastBumpAt = now
        val latest = recent.lastOrNull()
        val speed = if (latest != null && now - latest.first <= SPEED_FRESH_MS) latest.second else null
        if (speed == null || speed < BUMP_MIN_SPEED_KMH) return // standing or walking: a handled phone, not a road
        val before = recent.firstOrNull { now - it.first <= BREAKER_LOOKBACK_MS }?.second ?: speed
        val label = if (before - speed >= BREAKER_DROP_KMH) "speed breaker likely (speed dropped just before)" else "pothole or rough patch likely"
        emit("BUMP", now, speed, "Bump: %.1f m/s2 up-down jolt at %.0f km/h, %s, %s. Estimate from the phone's motion sensor, not a measured road feature.".format(peak, speed, label, where(lat, lon)))
    }
}
