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

    val BLOOD_GROUPS = listOf("A+", "A-", "B+", "B-", "AB+", "AB-", "O+", "O-")

    /** Blank is allowed (blood group is optional). Otherwise one of the eight standard groups. */
    fun cleanBlood(raw: String): String? {
        val t = raw.trim().uppercase()
        if (t.isEmpty()) return ""
        return if (t in BLOOD_GROUPS) t else null
    }

    /** Whole years from a date of birth written yyyy-MM-dd to today. Null for a bad, future or over 120 year old date. */
    fun ageYears(dob: String, year: Int, month: Int, day: Int): Int? {
        val m = Regex("^(\\d{4})-(\\d{2})-(\\d{2})$").matchEntire(dob.trim()) ?: return null
        val y = m.groupValues[1].toInt(); val mo = m.groupValues[2].toInt(); val d = m.groupValues[3].toInt()
        if (mo !in 1..12 || d !in 1..31) return null
        val dim = intArrayOf(31, if ((y % 4 == 0 && y % 100 != 0) || y % 400 == 0) 29 else 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)
        if (d > dim[mo - 1]) return null
        var age = year - y
        if (month < mo || (month == mo && day < d)) age--
        return if (age in 0..120) age else null
    }

    /** One line about the person, using only what was filled in. Null when nothing was filled in. */
    fun profileLine(name: String, age: Int?, blood: String): String? {
        val parts = mutableListOf<String>()
        if (name.isNotBlank()) parts.add("Name: " + name.trim())
        if (age != null) parts.add("Age: $age")
        if (blood.isNotBlank()) parts.add("Blood group: $blood")
        return if (parts.isEmpty()) null else parts.joinToString(", ")
    }

    /** The emergency SMS. locationMessage is the LocationShare text, or null when there is no fix. Profile and battery are left out when unknown. */
    fun smsBody(locationMessage: String?, profile: String? = null, batteryPct: Int? = null): String {
        val loc = locationMessage ?: "Location Unavailable"
        val sb = StringBuilder("SOS from Netra Hub: I may be in danger and need help. Please call me now.\n")
        if (profile != null) sb.append(profile).append('\n')
        if (batteryPct != null && batteryPct in 0..100) sb.append("Phone battery: ").append(batteryPct).append("%\n")
        return sb.append(loc).toString()
    }

    fun canTrigger(nowMs: Long, lastTriggerMs: Long): Boolean = lastTriggerMs <= 0L || nowMs - lastTriggerMs >= COOLDOWN_MS
}
