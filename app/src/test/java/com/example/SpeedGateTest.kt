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
    @Test fun opensAtThirtyAndStaysOpenThroughAShortStop() {
        val g = SpeedGate()
        assertTrue(g.update(30f, 0L))
        g.update(0f, 1000L)
        g.update(0f, 20_000L)
        assertTrue(g.isOpen)
    }
    @Test fun closesAfterThirtySecondsBelowThreshold() {
        val g = SpeedGate()
        g.update(50f, 0L)
        g.update(10f, 1000L)
        assertTrue(g.update(10f, 31_000L))
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
    @Test fun unknownSpeedNeverOpens() {
        val g = SpeedGate()
        g.update(null, 0L)
        assertFalse(g.isOpen)
    }
}
