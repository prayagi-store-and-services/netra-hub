package com.example

import com.example.driving.TripRecorder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TripRecorderTest {
    private data class Ev(val kind: String, val at: Long, val kmh: Float?, val text: String)

    private fun rec(out: MutableList<Ev>) = TripRecorder { k, a, s, t -> out.add(Ev(k, a, s, t)) }

    @Test fun hardBrakingIsLoggedWithTimeSpeedsAndPlace() {
        val out = mutableListOf<Ev>(); val r = rec(out)
        r.start(60f, 0L)
        r.onSpeed(60f, 25.1f, 82.1f, 1_000L)
        r.onSpeed(40f, 25.2f, 82.2f, 3_000L) // 20 km/h in 2 s = 2.8 m/s2
        assertEquals(1, out.count { it.kind == "BRAKE" })
        val b = out.first { it.kind == "BRAKE" }
        assertEquals(3_000L, b.at); assertTrue(b.text.contains("60 to 40")); assertTrue(b.text.contains("25.20000"))
    }

    @Test fun gentleSlowdownIsASpeedChangeNotBraking() {
        val out = mutableListOf<Ev>(); val r = rec(out)
        r.start(60f, 0L)
        r.onSpeed(58f, 0f, 0f, 2_000L)
        r.onSpeed(50f, 0f, 0f, 8_000L) // 10 km/h below the logged 60, but only 1.4 km/h per second
        assertEquals(0, out.count { it.kind == "BRAKE" })
        assertEquals(1, out.count { it.kind == "SPEED" })
        assertTrue(out.first { it.kind == "SPEED" }.text.startsWith("Speed dropped"))
        assertTrue(out.first { it.kind == "SPEED" }.text.contains("location unavailable"))
    }

    @Test fun speedIncreaseIsLogged() {
        val out = mutableListOf<Ev>(); val r = rec(out)
        r.start(20f, 0L)
        r.onSpeed(32f, 0f, 0f, 6_000L)
        assertTrue(out.single().text.startsWith("Speed increased"))
    }

    @Test fun unknownSpeedAndRepeatedFixAreNotInvented() {
        val out = mutableListOf<Ev>(); val r = rec(out)
        r.start(60f, 0L)
        r.onSpeed(null, 0f, 0f, 2_000L)
        r.onSpeed(60f, 1f, 1f, 3_000L)
        r.onSpeed(60f, 1f, 1f, 5_000L) // same cached fix again
        assertTrue(out.isEmpty())
    }

    private fun feedAccel(r: TripRecorder, t0: Long, jolt: Float) {
        var t = t0
        repeat(60) { r.onAccel(0f, 0f, 9.81f, t); t += 20 }          // flat, settles gravity
        r.onAccel(0f, 0f, 9.81f + jolt, t); t += 20                    // jolt
        repeat(20) { r.onAccel(0f, 0f, 9.81f, t); t += 20 }
    }

    @Test fun bumpAtSpeedIsLoggedAsLikelyPothole() {
        val out = mutableListOf<Ev>(); val r = rec(out)
        r.start(40f, 0L)
        r.onSpeed(40f, 0f, 0f, 500L)
        feedAccel(r, 1_000L, 6f)
        val b = out.single { it.kind == "BUMP" }
        assertTrue(b.text.contains("pothole or rough patch likely")); assertTrue(b.text.contains("Estimate"))
    }

    @Test fun bumpAfterSpeedDropIsLikelySpeedBreaker() {
        val out = mutableListOf<Ev>(); val r = rec(out)
        r.start(40f, 0L)
        r.onSpeed(40f, 0f, 0f, 500L)
        r.onSpeed(30f, 0f, 0f, 1_500L) // slowed down; also reaches the speed log, ignore it
        feedAccel(r, 2_000L, 6f)
        assertTrue(out.single { it.kind == "BUMP" }.text.contains("speed breaker likely"))
    }

    @Test fun joltWhileStandingIsIgnored() {
        val out = mutableListOf<Ev>(); val r = rec(out)
        r.start(0f, 0L)
        r.onSpeed(2f, 0f, 0f, 500L)
        feedAccel(r, 1_000L, 8f)
        assertEquals(0, out.count { it.kind == "BUMP" })
    }

    @Test fun smallVibrationIsIgnored() {
        val out = mutableListOf<Ev>(); val r = rec(out)
        r.start(40f, 0L); r.onSpeed(40f, 0f, 0f, 500L)
        feedAccel(r, 1_000L, 1.5f)
        assertEquals(0, out.count { it.kind == "BUMP" })
    }

    @Test fun brakingOverAFiveSecondGpsGapIsStillSeen() {
        val out = mutableListOf<Ev>(); val r = rec(out)
        r.start(70f, 0L)
        r.onSpeed(70f, 1f, 1f, 1_000L)
        r.onSpeed(20f, 1f, 1.1f, 6_000L) // 50 km/h in 5 s = 2.8 m/s2 average
        assertEquals(1, out.count { it.kind == "BRAKE" })
        assertTrue(out.first { it.kind == "BRAKE" }.text.contains("average"))
    }

    @Test fun newTripDoesNotReuseOldCoordinates() {
        val out = mutableListOf<Ev>(); val r = rec(out)
        r.start(60f, 0L); r.onSpeed(60f, 25.5f, 82.5f, 1_000L)
        r.start(60f, 100_000L)
        r.onSpeed(40f, 0f, 0f, 107_000L)
        assertTrue(out.last().text.contains("location unavailable"))
    }

    @Test fun sameFixTimeIsOneSample() {
        val out = mutableListOf<Ev>(); val r = rec(out)
        r.start(60f, 0L)
        r.onSpeed(60f, 1f, 1f, 1_000L)
        r.onSpeed(30f, 1f, 1f, 1_000L) // same fix time again (cached position): ignored
        assertTrue(out.isEmpty())
    }

    @Test fun firstBrakeAfterStartIsNotSuppressed() {
        val out = mutableListOf<Ev>(); val r = rec(out)
        r.start(60f, 0L)
        r.onSpeed(60f, 1f, 1f, 1_000L)
        r.onSpeed(40f, 1f, 1f, 3_000L)
        assertEquals(3_000L, out.first { it.kind == "BRAKE" }.at)
    }
}
