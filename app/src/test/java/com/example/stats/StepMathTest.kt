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

class StepTargetTest {
    @Test fun parsesValidDob() {
        assertEquals(Triple(2000, 2, 29), StepTarget.parseDob("29-02-2000", 2026, 10, 5))
    }
    @Test fun rejectsBadDates() {
        assertEquals(null, StepTarget.parseDob("31-04-2000", 2026, 10, 5))
        assertEquals(null, StepTarget.parseDob("29-02-2001", 2026, 10, 5))
        assertEquals(null, StepTarget.parseDob("06-10-2026", 2026, 10, 5))
        assertEquals(null, StepTarget.parseDob("abc", 2026, 10, 5))
    }
    @Test fun ageBeforeAndOnBirthday() {
        assertEquals(25, StepTarget.ageYears(Triple(2000, 10, 6), 2026, 10, 5))
        assertEquals(26, StepTarget.ageYears(Triple(2000, 10, 5), 2026, 10, 5))
    }
    @Test fun targetsOnlyWhereSourced() {
        assertEquals(null, StepTarget.forAge(5))
        assertEquals(12000, StepTarget.forAge(6))
        assertEquals(12000, StepTarget.forAge(19))
        assertEquals(10000, StepTarget.forAge(20))
        assertEquals(10000, StepTarget.forAge(64))
        assertEquals(null, StepTarget.forAge(65))
    }
}
