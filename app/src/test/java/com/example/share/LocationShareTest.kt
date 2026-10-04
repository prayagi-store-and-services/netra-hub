package com.example.share

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocationShareTest {
    @Test fun noFixIsRefused() {
        assertFalse(LocationShare.hasFix(0.0, 0.0, 1000L))
        assertFalse(LocationShare.hasFix(26.9, 75.8, 0L))
        assertFalse(LocationShare.hasFix(95.0, 75.8, 1000L))
        assertNull(LocationShare.message(0.0, 0.0, 10f, 0L))
    }
    @Test fun oldFixIsRefused() {
        val t = 1_700_000_000_000L
        assertTrue(LocationShare.hasFix(26.9, 75.8, t, t + 14 * 60_000L))
        assertFalse(LocationShare.hasFix(26.9, 75.8, t, t + 16 * 60_000L))
        assertNull(LocationShare.message(26.9, 75.8, 10f, t, t + 3_600_000L))
    }
    @Test fun futureFixIsRefused() {
        val t = 1_700_000_000_000L
        assertFalse(LocationShare.hasFix(26.9, 75.8, t + 10 * 60_000L, t))
    }
    @Test fun linkHasSixDecimals() {
        assertEquals("https://maps.google.com/?q=26.912400,75.787300", LocationShare.mapsLink(26.9124, 75.7873))
    }
    @Test fun messageSaysSnapshotAndUnavailableAccuracy() {
        val m = LocationShare.message(26.9124, 75.7873, 0f, 1_700_000_000_000L, 1_700_000_060_000L)!!
        assertTrue(m.contains("maps.google.com/?q=26.912400,75.787300"))
        assertTrue(m.contains("Accuracy: Unavailable"))
        assertTrue(m.contains("not a live track"))
    }
}
