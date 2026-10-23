package com.example

import com.example.data.sensor.SpeedGate
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeedGateTest {
    /** Feed a sustained speed at/above the threshold for the full open window, one reading every second. */
    private fun openGate(g: SpeedGate, speed: Float = 40f, startMs: Long = 0L): Boolean {
        var changed = false
        for (t in 0..SpeedGate.DEFAULT_OPEN_DELAY_MS step 1000L) changed = g.update(speed, startMs + t) || changed
        return changed
    }

    @Test fun walkingNeverOpensTheGate() {
        val g = SpeedGate()
        for (t in 0..100) g.update(6f, t * 1000L)
        assertFalse(g.isOpen)
    }
    @Test fun singleSpeedSpikeDoesNotOpen() {
        // The reported defect: one GPS spike on a still phone must never turn driving mode on.
        val g = SpeedGate()
        assertFalse(g.update(18f, 0L))
        assertFalse(g.isOpen)
        assertFalse(g.update(120f, 1000L))
        assertFalse(g.isOpen)
    }
    @Test fun spikeThenQuietThenSpikeStillDoesNotOpen() {
        val g = SpeedGate()
        g.update(30f, 0L)
        g.update(0f, 1000L)   // quiet resets the sustained window
        g.update(30f, 2000L)
        g.update(30f, 7000L)  // only 5 s sustained since the second spike
        assertFalse(g.isOpen)
    }
    @Test fun opensOnlyAfterSpeedSustainedForTheOpenWindow() {
        val g = SpeedGate()
        assertFalse(g.update(15f, 0L))
        assertFalse(g.update(15f, 5_000L))
        assertTrue(g.update(15f, SpeedGate.DEFAULT_OPEN_DELAY_MS))
        assertTrue(g.isOpen)
    }
    @Test fun opensAtThresholdAndStaysOpenThroughAShortStop() {
        val g = SpeedGate()
        assertTrue(openGate(g, 15f))
        g.update(0f, 20_000L)
        g.update(0f, 60_000L)
        assertTrue(g.isOpen)
    }
    @Test fun closesAfterNinetySecondsBelowThreshold() {
        val g = SpeedGate()
        openGate(g, 50f)
        g.update(10f, 11_000L)
        assertTrue(g.update(10f, 101_000L))
        assertFalse(g.isOpen)
    }
    @Test fun speedBackAboveThresholdResetsTheCloseTimer() {
        val g = SpeedGate()
        openGate(g, 50f)
        g.update(10f, 11_000L)
        g.update(40f, 20_000L)  // fast again: close timer clears at once, no new open wait while already open
        g.update(10f, 25_000L)
        g.update(10f, 50_000L)
        assertTrue(g.isOpen)
    }
    @Test fun slowCityRidingAboveFifteenOpensTheGate() {
        val g = SpeedGate()
        assertTrue(openGate(g, 22f))
    }
    @Test fun unknownSpeedNeverOpens() {
        val g = SpeedGate()
        g.update(null, 0L)
        assertFalse(g.isOpen)
    }
    @Test fun unknownSpeedDoesNotAdvanceTheCloseTimer() {
        val g = SpeedGate()
        openGate(g, 40f)
        for (t in 1..200) g.update(null, 10_000L + t * 1000L) // 200 s blind, under the 300 s limit
        assertTrue(g.isOpen)
    }
    @Test fun longBlindSpellClosesTheGate() {
        val g = SpeedGate()
        openGate(g, 40f)
        g.update(null, 11_000L)
        assertTrue(g.update(null, 11_000L + SpeedGate.UNKNOWN_CLOSE_MS))
        assertFalse(g.isOpen)
    }
    @Test fun blindGapRestartsTheLowSpeedTimer() {
        val g = SpeedGate()
        openGate(g, 40f)
        g.update(5f, 11_000L)     // low run starts
        g.update(null, 40_000L)   // blind
        g.update(5f, 80_000L)     // 69 s after the first low reading, but a gap in between: timer restarts here
        assertTrue(g.isOpen)
        assertTrue(g.update(5f, 80_000L + SpeedGate.DEFAULT_CLOSE_DELAY_MS))
    }
    @Test fun blindGapRestartsTheHighSpeedWindow() {
        val g = SpeedGate()
        g.update(50f, 0L)          // high run starts
        g.update(null, 5_000L)     // even a short blind gap: not consecutive fresh evidence
        g.update(50f, 6_000L)      // window restarts here
        g.update(50f, 11_000L)
        assertFalse(g.isOpen)
        assertTrue(g.update(50f, 6_000L + SpeedGate.DEFAULT_OPEN_DELAY_MS))
    }
    @Test fun longSilenceThenOneSpikeDoesNotOpen() {
        // No readings for 2 minutes, then a single fast reading: the old high run is stale.
        val g = SpeedGate()
        g.update(50f, 0L)
        assertFalse(g.update(50f, 120_000L))
        assertFalse(g.isOpen)
        assertTrue(g.update(50f, 120_000L + SpeedGate.DEFAULT_OPEN_DELAY_MS))
    }
    @Test fun sparseButValidStreamOpensWhenSustained() {
        // Valid readings every 5 s (typical update interval): opens once the window fills.
        val g = SpeedGate()
        g.update(20f, 0L)
        g.update(20f, 5_000L)
        assertFalse(g.isOpen)
        assertTrue(g.update(20f, 10_000L))
    }
    @Test fun verySparseValidStreamOpensLater() {
        // Valid readings every 8 s: two readings is only 8 s of sustained evidence, so it opens on the third.
        val g = SpeedGate()
        g.update(20f, 0L)
        g.update(20f, 8_000L)
        assertFalse(g.isOpen)
        assertTrue(g.update(20f, 16_000L))
    }
    @Test fun validGapUnderThirtySecondsKeepsTheWindow() {
        val g = SpeedGate()
        g.update(20f, 0L)
        g.update(20f, 25_000L)  // sparse but still fresh: window continues
        assertFalse(g.isOpen)
        assertTrue(g.update(20f, 25_000L + SpeedGate.DEFAULT_OPEN_DELAY_MS))
    }
    @Test fun validGapOverThirtySecondsRestartsTheWindow() {
        val g = SpeedGate()
        g.update(20f, 0L)
        g.update(20f, 31_000L)  // too sparse: not consecutive evidence
        assertFalse(g.isOpen)
        g.update(20f, 36_000L)
        assertFalse(g.isOpen)
        assertTrue(g.update(20f, 31_000L + SpeedGate.DEFAULT_OPEN_DELAY_MS))
    }
    @Test fun resetClearsEverything() {
        val g = SpeedGate()
        openGate(g, 40f); g.reset()
        assertFalse(g.isOpen)
        g.update(50f, 100_000L)
        assertFalse(g.isOpen)      // the old high run is gone: a spike after reset cannot open
    }
}
