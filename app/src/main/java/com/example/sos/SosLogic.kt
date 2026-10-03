package com.example.sos

import kotlin.math.sqrt

/** Pure logic for SOS shake: shake detection, phone number checks and the SMS text. No Android classes here so it can be unit tested. */
object SosLogic {
    const val SHAKE_G = 2.7f          // strength of one hard shake, in g
    const val SHAKES_NEEDED = 3       // hard shakes needed ...
    const val WINDOW_MS = 1500L       // ... inside this time
    const val MIN_GAP_MS = 150L       // two samples closer than this count as the same shake
    const val COUNTDOWN_S = 10        // seconds the user has to cancel before any SMS goes out
    const val COOLDOWN_MS = 60_000L   // after an SOS, ignore shakes for a minute
    const val MAX_CONTACTS = 5

    /** Counts hard shakes. Feed it every accelerometer sample; it returns true once when the pattern is complete. */
    class ShakeDetector {
        private val times = ArrayDeque<Long>()
        fun onSample(x: Float, y: Float, z: Float, tMs: Long): Boolean {
            val g = sqrt(x * x + y * y + z * z) / 9.81f
            if (g < SHAKE_G) return false
            if (times.isNotEmpty() && tMs - times.last() < MIN_GAP_MS) return false
            times.addLast(tMs)
            while (times.isNotEmpty() && tMs - times.first() > WINDOW_MS) times.removeFirst()
            if (times.size >= SHAKES_NEEDED) { times.clear(); return true }
            return false
        }
        fun reset() = times.clear()
    }

    /** Keeps digits and one leading plus. Returns null unless it has 7 to 15 digits. */
    fun cleanNumber(raw: String): String? {
        val t = raw.trim()
        val plus = t.startsWith("+")
        val digits = t.filter { it.isDigit() }
        if (digits.length !in 7..15) return null
        return (if (plus) "+" else "") + digits
    }

    /** The emergency SMS. locationMessage is the LocationShare text, or null when there is no fix. */
    fun smsBody(locationMessage: String?): String {
        val loc = locationMessage ?: "Location Unavailable"
        return "SOS from Netra Hub: I may be in danger and need help. Please call me now.\n" + loc
    }

    fun canTrigger(nowMs: Long, lastTriggerMs: Long): Boolean = lastTriggerMs <= 0L || nowMs - lastTriggerMs >= COOLDOWN_MS
}
