package com.example

import com.example.sos.ParkingSpot
import com.example.sos.StopTracker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ParkingFixTest {
    private val now = 10_000_000L

    @Test fun savesTheStopNotTheRoadAhead() {
        val t = StopTracker()
        t.onFix(25.1000, 82.0000, 8f, now - 300_000, 40f)
        t.onFix(25.1050, 82.0000, 8f, now - 200_000, 35f)   // still moving
        t.onFix(25.1100, 82.0000, 8f, now - 150_000, 2f)    // the stop
        t.onFix(25.1100, 82.0000, 8f, now - 100_000, 0f)    // still stopped
        val d = ParkingSpot.decide(t.candidate, t.last, now)
        assertEquals(25.1100, d.fix!!.lat, 0.0)
        assertEquals(now - 150_000, d.fix!!.at) // the fix's own time
    }

    @Test fun movingAgainClearsTheStopCandidate() {
        val t = StopTracker()
        t.onFix(25.1, 82.0, 8f, 1_000, 40f)
        t.onFix(25.2, 82.0, 8f, 6_000, 1f)   // red light
        t.onFix(25.3, 82.0, 8f, 11_000, 30f) // moving again
        assertNull(t.candidate)
        t.onFix(25.4, 82.0, 8f, 16_000, 0f)  // real stop
        assertEquals(25.4, t.candidate!!.lat, 0.0)
    }

    @Test fun noStopBeforeAnyMovement() {
        val t = StopTracker()
        t.onFix(25.1, 82.0, 8f, 1_000, 0f) // standing at home, never moved
        assertNull(t.candidate)
    }

    @Test fun unknownSpeedIsNotAStop() {
        val t = StopTracker()
        t.onFix(25.1, 82.0, 8f, 1_000, 40f)
        t.onFix(25.2, 82.0, 8f, 6_000, null)
        assertNull(t.candidate)
    }

    @Test fun repeatedAndZeroFixesAreIgnored() {
        val t = StopTracker()
        t.onFix(0.0, 0.0, 5f, 1_000, 40f)
        assertNull(t.last)
        t.onFix(25.1, 82.0, 8f, 2_000, 40f)
        t.onFix(25.9, 82.9, 8f, 2_000, 0f) // same fix time again: ignored
        assertEquals(25.1, t.last!!.lat, 0.0)
    }

    @Test fun poorFixIsRefusedWithAnHonestReason() {
        val bad = StopTracker.Fix(25.1, 82.0, 900f, now - 10_000)
        val d = ParkingSpot.decide(bad, bad, now)
        assertNull(d.fix)
        assertTrue(d.status.startsWith("Not saved") && d.status.contains("900 m"))
    }

    @Test fun roughFixIsSavedButLabelled() {
        val rough = StopTracker.Fix(25.1, 82.0, 120f, now - 10_000)
        val d = ParkingSpot.decide(rough, rough, now)
        assertNotNull(d.fix)
        assertTrue(ParkingSpot.accuracyText(120f).contains("rough"))
        assertTrue(ParkingSpot.accuracyText(12f).contains("about 12 m"))
        assertTrue(ParkingSpot.accuracyText(-1f).contains("unknown"))
    }

    @Test fun oldLatestFixIsNotUsedWithoutAStop() {
        val old = StopTracker.Fix(25.1, 82.0, 10f, now - 5 * 60_000)
        assertNull(ParkingSpot.decide(null, old, now).fix)          // 5 min old, no stop
        val fresh = StopTracker.Fix(25.1, 82.0, 10f, now - 30_000)
        assertNotNull(ParkingSpot.decide(null, fresh, now).fix)     // 30 s old
    }

    @Test fun stopFixOlderThanFifteenMinutesIsRefused() {
        val stop = StopTracker.Fix(25.1, 82.0, 10f, now - 16 * 60_000)
        assertNull(ParkingSpot.decide(stop, null, now).fix)
    }

    @Test fun noFixAtAll() {
        val d = ParkingSpot.decide(null, null, now)
        assertNull(d.fix); assertTrue(d.status.contains("no GPS fix"))
    }
}
