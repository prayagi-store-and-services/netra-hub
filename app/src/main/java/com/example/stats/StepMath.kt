package com.example.stats

/** Pure step logic. Android's step counter is a running total since boot, so "today" needs a baseline. */
data class StepState(val day: String, val base: Float, val carried: Float, val lastRaw: Float, val sinceMs: Long)

object StepMath {
    /** Moves the state forward with a new counter reading. A new day or a reboot (counter went down) is handled. */
    fun advance(s: StepState?, day: String, raw: Float, nowMs: Long): StepState {
        if (s == null || s.day != day) return StepState(day, raw, 0f, raw, nowMs)
        if (raw < s.lastRaw) {
            // The phone restarted and the counter began again from 0: keep what was counted before.
            return s.copy(base = 0f, carried = s.carried + (s.lastRaw - s.base), lastRaw = raw)
        }
        return s.copy(lastRaw = raw)
    }

    fun today(s: StepState): Long = (s.carried + (s.lastRaw - s.base)).coerceAtLeast(0f).toLong()
}

/** Age-based daily step target. Only ranges with a published source get a number; everything else is null (Unavailable). */
object StepTarget {
    /** Parses "DD-MM-YYYY". Returns Triple(year, month, day) or null if it is not a real past date. */
    fun parseDob(text: String, nowYear: Int, nowMonth: Int, nowDay: Int): Triple<Int, Int, Int>? {
        val p = text.trim().split("-", "/", ".")
        if (p.size != 3) return null
        val d = p[0].toIntOrNull() ?: return null
        val m = p[1].toIntOrNull() ?: return null
        val y = p[2].toIntOrNull() ?: return null
        if (y < 1900 || m !in 1..12 || d < 1) return null
        val leap = (y % 4 == 0 && y % 100 != 0) || y % 400 == 0
        val dim = intArrayOf(31, if (leap) 29 else 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)[m - 1]
        if (d > dim) return null
        if (y > nowYear || (y == nowYear && (m > nowMonth || (m == nowMonth && d > nowDay)))) return null
        return Triple(y, m, d)
    }

    fun ageYears(dob: Triple<Int, Int, Int>, nowYear: Int, nowMonth: Int, nowDay: Int): Int {
        var a = nowYear - dob.first
        if (nowMonth < dob.second || (nowMonth == dob.second && nowDay < dob.third)) a--
        return a
    }

    /**
     * 6 to 19 years: 12,000 steps/day (Colley et al. 2012 practical cut point for 60 min/day of moderate-to-vigorous activity,
     * 11,290 to 12,512 steps/day; Tudor-Locke et al. 2011 child and adolescent review).
     * 20 to 64 years: 10,000 steps/day (Tudor-Locke et al. 2011 adult review: "reasonable" for healthy adults).
     * Under 6 and 65 and over: no evidence-based single target in those reviews, so null.
     */
    fun forAge(age: Int): Int? = when (age) {
        in 6..19 -> 12000
        in 20..64 -> 10000
        else -> null
    }
}
