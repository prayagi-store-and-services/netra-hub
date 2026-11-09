package com.example

import com.example.data.sensor.GpsAdaptivePolicy
import com.example.data.sensor.SpeedGate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GpsAdaptivePolicyTest {
    @Test fun drivingKeepsFastSampling() {
        assertEquals(5_000L, GpsAdaptivePolicy.intervalMs(true))
        assertEquals(3_000L, GpsAdaptivePolicy.minIntervalMs(true))
    }
    @Test fun stationarySlowsButNeverBreaksTheSpeedGate() {
        // The whole point of the cap: a slower stationary interval must stay within the gate's
        // maximum evidence gap, otherwise driving mode could never turn on again.
        assertTrue(GpsAdaptivePolicy.intervalMs(false) <= SpeedGate.OPEN_GAP_MAX_MS)
        assertTrue(GpsAdaptivePolicy.preservesDrivingDetection())
        assertTrue(GpsAdaptivePolicy.intervalMs(false) > GpsAdaptivePolicy.intervalMs(true))
    }
}
