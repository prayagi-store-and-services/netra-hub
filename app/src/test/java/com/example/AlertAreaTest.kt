package com.example

import com.example.data.engine.AlertArea
import com.example.data.engine.SavedAlertArea
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AlertAreaTest {
    private val day = 86_400_000L
    private val a = SavedAlertArea(25.43, 81.84, "Naini", "Prayagraj", "Uttar Pradesh", 1_000_000L)

    @Test fun plausibleRejectsZeroAndOutOfRange() {
        assertTrue(AlertArea.plausible(25.43, 81.84))
        assertFalse(AlertArea.plausible(0.0, 0.0))
        assertFalse(AlertArea.plausible(91.0, 10.0))
        assertFalse(AlertArea.plausible(10.0, 181.0))
    }

    @Test fun placeTextUsesNamesOrCoordinates() {
        assertEquals("Naini, Prayagraj, Uttar Pradesh", AlertArea.placeText(a))
        assertEquals("25.43, 81.84", AlertArea.placeText(a.copy(locality = "", district = "", state = "")))
    }

    @Test fun staleOnlyAfterFourteenDays() {
        assertFalse(AlertArea.isStale(a, a.savedAtMillis + 13 * day))
        assertTrue(AlertArea.isStale(a, a.savedAtMillis + 14 * day))
        assertNull(AlertArea.staleNote(a, a.savedAtMillis + day))
        assertNotNull(AlertArea.staleNote(a, a.savedAtMillis + 20 * day))
        assertEquals(0L, AlertArea.ageDays(a, a.savedAtMillis - day))
    }
}
