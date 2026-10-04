package com.example.stats

import org.junit.Assert.assertEquals
import org.junit.Test

class StepMathTest {
    @Test fun firstReadingStartsAtZero() {
        val s = StepMath.advance(null, "2026-10-05", 1000f, 5L)
        assertEquals(0L, StepMath.today(s))
    }
    @Test fun countsGrowthWithinDay() {
        var s = StepMath.advance(null, "2026-10-05", 1000f, 5L)
        s = StepMath.advance(s, "2026-10-05", 1250f, 6L)
        assertEquals(250L, StepMath.today(s))
    }
    @Test fun newDayResets() {
        var s = StepMath.advance(null, "2026-10-05", 1000f, 5L)
        s = StepMath.advance(s, "2026-10-05", 1500f, 6L)
        s = StepMath.advance(s, "2026-10-06", 1600f, 7L)
        assertEquals(0L, StepMath.today(s))
    }
    @Test fun rebootKeepsEarlierSteps() {
        var s = StepMath.advance(null, "2026-10-05", 1000f, 5L)
        s = StepMath.advance(s, "2026-10-05", 1400f, 6L)
        s = StepMath.advance(s, "2026-10-05", 30f, 7L)
        assertEquals(430L, StepMath.today(s))
        s = StepMath.advance(s, "2026-10-05", 80f, 8L)
        assertEquals(480L, StepMath.today(s))
    }
}
