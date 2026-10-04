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
