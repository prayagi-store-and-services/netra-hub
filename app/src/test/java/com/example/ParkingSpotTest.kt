package com.example

import com.example.sos.ParkingSpot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ParkingSpotTest {
    @Test fun savesOnlyWhenDrivingEndsAndOptedIn() {
        assertTrue(ParkingSpot.shouldSave(true, false, true))
        assertFalse(ParkingSpot.shouldSave(true, false, false))
        assertFalse(ParkingSpot.shouldSave(false, false, true))
        assertFalse(ParkingSpot.shouldSave(true, true, true))
        assertFalse(ParkingSpot.shouldSave(false, true, true))
    }
    @Test fun noFixIsNotUsable() {
        val now = 1_000_000_000L
        assertFalse(ParkingSpot.usable(0.0, 0.0, now, now))
        assertFalse(ParkingSpot.usable(28.6, 77.2, 0L, now))
        assertFalse(ParkingSpot.usable(28.6, 77.2, now - 16 * 60 * 1000L, now))
        assertTrue(ParkingSpot.usable(28.6, 77.2, now - 60_000L, now))
    }
    @Test fun mapsUriHasCoordinates() {
        assertEquals("geo:28.600000,77.200000?q=28.600000,77.200000(Parked)", ParkingSpot.mapsUri(28.6, 77.2))
    }
}
