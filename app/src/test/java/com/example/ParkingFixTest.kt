package com.example

import com.example.sos.ParkingSpot
import com.example.sos.StopTracker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ParkingFixTest {
    private val base = 1_000_000L
    private fun StopTracker.fix(sec: Int, lat: Double, lon: Double, kmh: Float?, acc: Float = 8f) =
        onFix(lat, lon, acc, base + sec * 1000L, 5_000_000L + sec * 1000L, kmh)

    private fun parkedAfterDrive(t: StopTracker) {
        t.fix(0, 25.1000, 82.0, 40f); t.fix(5, 25.1050, 82.0, 35f)
        t.fix(10, 25.1100, 82.0, 1f); t.fix(15, 25.1100, 82.0, 0f); t.fix(20, 25.1100, 82.0, 0f)
    }

    @Test fun savesTheSustainedStopNotTheRoadAhead() {
        val t = StopTracker(); parkedAfterDrive(t)
        val d = ParkingSpot.decide(t.candidate, t.last, base + 100_000)
        assertEquals(25.1100, d.fix!!.lat, 1e-9)
    }

    @Test fun singleSlowSampleIsNotAStop() {
        val t = StopTracker()
        t.fix(0, 25.1, 82.0, 40f); t.fix(5, 25.2, 82.0, 1f); t.fix(10, 25.3, 82.0, 30f)
        assertNull(t.candidate)
    }

    @Test fun crawlAboveThreeKmhIsNotAStop() {
        val t = StopTracker()
        t.fix(0, 25.1, 82.0, 40f)
        for (i in 1..8) t.fix(i * 5, 25.1 + i * 0.0001, 82.0, 4f)
        assertNull(t.candidate)
    }

    @Test fun tenSecondsStillOnAccurateFixesIsRequired() {
        val t = StopTracker()
        t.fix(0, 25.1, 82.0, 40f); t.fix(5, 25.2, 82.0, 0f); t.fix(8, 25.2, 82.0, 0f)
        assertNull(t.candidate)          // only 3 s
        t.fix(16, 25.2, 82.0, 0f)
        assertNotNull(t.candidate)       // 11 s
    }

    @Test fun inaccurateFixesNeverConfirmAStop() {
        val t = StopTracker()
        t.fix(0, 25.1, 82.0, 40f)
        for (i in 1..6) t.fix(i * 5, 25.2, 82.0, 0f, acc = 300f)
        assertNull(t.candidate)
    }

    @Test fun outlierFixIsRejectedByTheMedian() {
        val t = StopTracker()
        t.fix(0, 25.1, 82.0, 40f)
        t.fix(5, 25.2000, 82.0000, 0f); t.fix(10, 25.2000, 82.0000, 0f)
        t.fix(15, 25.2100, 82.0000, 0f)  // one wild fix about 1 km away
        t.fix(20, 25.2000, 82.0000, 0f); t.fix(25, 25.2000, 82.0000, 0f)
        assertEquals(25.2000, t.candidate!!.lat, 1e-6)
    }

    @Test fun walkingAwayDoesNotMoveTheSpot() {
        val t = StopTracker(); parkedAfterDrive(t)
        val spot = t.candidate!!
        for (i in 1..10) t.fix(20 + i * 5, 25.1100 + i * 0.0001, 82.0, 5f) // walking 5 km/h away
        assertEquals(spot.lat, t.candidate!!.lat, 0.0)
    }

    @Test fun drivingOffClearsTheOldStop() {
        val t = StopTracker(); parkedAfterDrive(t)
        t.fix(30, 25.12, 82.0, 25f)
        assertNull(t.candidate)
    }

    @Test fun laterStopReplacesEarlierOne() {
        val t = StopTracker(); parkedAfterDrive(t)
        t.fix(30, 25.12, 82.0, 25f)
        t.fix(40, 25.13, 82.0, 0f); t.fix(46, 25.13, 82.0, 0f); t.fix(52, 25.13, 82.0, 0f)
        assertEquals(25.13, t.candidate!!.lat, 1e-9)
    }

    @Test fun standingAtHomeWithoutMovingIsNotAStop() {
        val t = StopTracker()
        for (i in 0..6) t.fix(i * 5, 25.1, 82.0, 0f)
        assertNull(t.candidate)
    }

    @Test fun unknownSpeedZeroAndRepeatedFixesAreIgnored() {
        val t = StopTracker()
        t.onFix(0.0, 0.0, 5f, base, base, 40f); assertNull(t.last)
        t.fix(1, 25.1, 82.0, 40f); t.fix(6, 25.2, 82.0, null)
        assertNull(t.candidate)
        t.onFix(25.9, 82.9, 8f, base + 6000L, base, 0f) // not newer than the last fix
        assertEquals(25.2, t.last!!.lat, 1e-9)
    }

    @Test fun savedTimeIsTheFixTimeAndAccuracyIsKept() {
        val t = StopTracker(); parkedAfterDrive(t)
        val d = ParkingSpot.decide(t.candidate, t.last, base + 100_000)
        assertTrue(d.fix!!.wallAt in 5_000_000L..5_100_000L)
        assertEquals(8f, d.fix!!.accuracyM, 0f)
    }

    @Test fun staleOrFutureOrPoorStopIsRefusedWithReason() {
        val old = StopTracker.Fix(25.1, 82.0, 10f, base)
        assertTrue(ParkingSpot.decide(old, old, base + 11 * 60_000L).status.contains("too old"))
        val fut = StopTracker.Fix(25.1, 82.0, 10f, base + 60_000L)
        assertTrue(ParkingSpot.decide(fut, fut, base).status.contains("future"))
        val poor = StopTracker.Fix(25.1, 82.0, 80f, base)
        val d = ParkingSpot.decide(poor, poor, base + 1000L)
        assertNull(d.fix); assertTrue(d.status.contains("80 m"))
    }

    @Test fun noConfirmedStopKeepsEarlierSpotWithReason() {
        val last = StopTracker.Fix(25.1, 82.0, 5f, base)
        val d = ParkingSpot.decide(null, last, base + 5000L)
        assertNull(d.fix); assertTrue(d.status.contains("no confirmed stop"))
        assertTrue(ParkingSpot.decide(null, null, base).status.contains("no GPS fix"))
    }

    @Test fun accuracyWording() {
        assertTrue(ParkingSpot.accuracyText(12f).contains("about 12 m"))
        assertTrue(ParkingSpot.accuracyText(40f).contains("fair"))
        assertTrue(ParkingSpot.accuracyText(-1f).contains("unknown"))
    }
}
