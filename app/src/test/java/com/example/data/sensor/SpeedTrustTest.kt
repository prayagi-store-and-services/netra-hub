package com.example.data.sensor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeedTrustTest {
    @Test fun accurateFixWithGoodSpeedErrorIsTrusted() {
        assertTrue(SpeedTrust.trusted(10f, true, 1.5f))
    }
    @Test fun accurateFixWithoutSpeedErrorEstimateIsTrusted() {
        assertTrue(SpeedTrust.trusted(20f, false, 0f))
    }
    @Test fun coarseFixIsNotTrusted() {
        // Indoors a coarse fix can report a big speed on a still phone; it must read as unknown.
        assertFalse(SpeedTrust.trusted(80f, true, 1f))
    }
    @Test fun missingAccuracyIsNotTrusted() {
        assertFalse(SpeedTrust.trusted(0f, false, 0f))
    }
    @Test fun highSpeedErrorEstimateIsNotTrusted() {
        assertFalse(SpeedTrust.trusted(10f, true, 9f))
    }
    @Test fun boundaryValues() {
        assertTrue(SpeedTrust.trusted(SpeedTrust.MAX_ACCURACY_M, true, SpeedTrust.MAX_SPEED_ACCURACY_MPS))
        assertFalse(SpeedTrust.trusted(SpeedTrust.MAX_ACCURACY_M + 0.1f, true, 1f))
        assertFalse(SpeedTrust.trusted(10f, true, SpeedTrust.MAX_SPEED_ACCURACY_MPS + 0.1f))
    }
    @Test fun cleanSpeedDropsNonFiniteAndImplausible() {
        assertEquals(null, SpeedTrust.cleanSpeed(Float.NaN))
        assertEquals(null, SpeedTrust.cleanSpeed(Float.POSITIVE_INFINITY))
        assertEquals(null, SpeedTrust.cleanSpeed(250f))   // beyond plausible driving speed: not evidence
        assertEquals(null, SpeedTrust.cleanSpeed(-3f))
        assertEquals(18f, SpeedTrust.cleanSpeed(18f))
        assertEquals(0f, SpeedTrust.cleanSpeed(0f))
        assertEquals(null, SpeedTrust.cleanSpeed(null))
    }
}
