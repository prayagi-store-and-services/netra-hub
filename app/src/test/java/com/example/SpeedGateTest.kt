package com.example

import com.example.data.sensor.SpeedGate
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeedGateTest {
    @Test fun walkingNeverOpensTheGate() {
        val g = SpeedGate()
        for (t in 0..100) g.update(6f, t * 1000L)
        assertFalse(g.isOpen)
    }
    @Test fun opensAtThresholdAndStaysOpenThroughAShortStop() {
        val g = SpeedGate()
        assertTrue(g.update(15f, 0L))
        g.update(0f, 1000L)
        g.update(0f, 60_000L)
        assertTrue(g.isOpen)
    }
    @Test fun closesAfterNinetySecondsBelowThreshold() {
        val g = SpeedGate()
        g.update(50f, 0L)
        g.update(10f, 1000L)
        assertTrue(g.update(10f, 91_000L))
        assertFalse(g.isOpen)
    }
    @Test fun speedBackAboveThresholdResetsTheTimer() {
        val g = SpeedGate()
        g.update(50f, 0L)
        g.update(10f, 1000L)
        g.update(40f, 20_000L)
        g.update(10f, 25_000L)
        g.update(10f, 50_000L)
        assertTrue(g.isOpen)
    }
    @Test fun slowCityRidingAboveFifteenOpensTheGate() {
        val g = SpeedGate()
        assertTrue(g.update(22f, 0L))
    }
    @Test fun unknownSpeedNeverOpens() {
        val g = SpeedGate()
        g.update(null, 0L)
        assertFalse(g.isOpen)
    }
    @Test fun unknownSpeedDoesNotAdvanceTheCloseTimer() {
        val g = SpeedGate()
        g.update(40f, 0L)
        for (t in 1..200) g.update(null, t * 1000L) // 200 s blind, under the 300 s limit
        assertTrue(g.isOpen)
    }
    @Test fun longBlindSpellClosesTheGate() {
        val g = SpeedGate()
        g.update(40f, 0L)
        g.update(null, 1000L)
        assertTrue(g.update(null, 1000L + SpeedGate.UNKNOWN_CLOSE_MS))
        assertFalse(g.isOpen)
    }
    @Test fun blindGapRestartsTheLowSpeedTimer() {
        val g = SpeedGate()
        g.update(40f, 0L)
        g.update(5f, 1000L)       // low run starts
        g.update(null, 40_000L)   // blind
        g.update(5f, 80_000L)     // 79 s after the first low reading, but a gap in between: timer restarts here
        assertTrue(g.isOpen)
        assertTrue(g.update(5f, 80_000L + SpeedGate.DEFAULT_CLOSE_DELAY_MS))
    }
    @Test fun resetClearsEverything() {
        val g = SpeedGate()
        g.update(40f, 0L); g.reset()
        assertFalse(g.isOpen)
    }
}
