package com.example

import com.example.data.engine.AlertArea
import com.example.data.engine.CapAlert
import com.example.data.engine.SachetAlertEntry
import com.example.data.engine.SachetAlertState
import com.example.data.engine.SachetDecision
import com.example.data.engine.SavedAlertArea
import com.example.data.engine.capMatchesPlace
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

    private fun poly(expires: Long) = CapAlert(
        id = "IN-9", sender = "SDMA", event = "Heavy Rain", severity = "Severe", headline = "Heavy rain",
        areaDesc = "Region B", expiresMillis = expires,
        polygon = listOf(25.0 to 82.0, 25.0 to 83.0, 26.0 to 83.0, 26.0 to 82.0), circleKm = null
    )

    @Test fun coordsOnlyAreaMatchesPolygonWithoutAnyNames() {
        val c = poly(10_000_000L)
        val now = 5_000_000L
        assertTrue(capMatchesPlace(c, "Location Unavailable", "Location Unavailable", "Location Unavailable", 25.5, 82.5, now))
        assertFalse(capMatchesPlace(c, "Location Unavailable", "Location Unavailable", "Location Unavailable", 24.0, 82.5, now))
    }

    @Test fun changedSinceDetectsAreaChangeDuringRun() {
        assertFalse(AlertArea.changedSince(3L, 3L))
        assertTrue(AlertArea.changedSince(3L, 4L))
    }

    @Test fun afterAreaChangeAlertsInNewAreaAnnounceOnceAndOldWarningNotSpoken() {
        // Before: area A had a seen alert and an active warning that has expired.
        val oldActive = mapOf(SachetAlertState.identityKey("Heavy Rain", "Region A") to SachetAlertEntry("Severe", 1_000L))
        assertEquals(1, SachetAlertState.expiredKeys(oldActive, 2_000L).size)
        // The area change forgets seen ids and active entries (AlertArea.save/clear).
        val seenAfter = emptySet<String>()
        val activeAfter = emptyMap<String, SachetAlertEntry>()
        // No "warning over" for the old area is spoken in the new one.
        assertTrue(SachetAlertState.expiredKeys(activeAfter, 2_000L).isEmpty())
        // An alert for area B that was in the feed before is no longer skipped as seen, and is announced.
        val feed = listOf("guid-B" to "link")
        assertEquals(1, feed.filter { it.first !in seenAfter }.size)
        val key = SachetAlertState.identityKey("Heavy Rain", "Region B")
        assertEquals(SachetDecision.FIRST_ALERT, SachetAlertState.decide(activeAfter[key], "Severe"))
    }

    @Test fun usgsFeedUsesNoCoordinatesAndFitsMinimumMagnitude() {
        assertTrue(AlertArea.usgsDayFeedUrl(2.5).endsWith("/2.5_day.geojson"))
        assertTrue(AlertArea.usgsDayFeedUrl(4.5).endsWith("/4.5_day.geojson"))
        assertTrue(AlertArea.usgsDayFeedUrl(3.0).endsWith("/2.5_day.geojson"))
        assertTrue(AlertArea.usgsDayFeedUrl(1.2).endsWith("/1.0_day.geojson"))
        assertTrue(AlertArea.usgsDayFeedUrl(0.0).endsWith("/all_day.geojson"))
        assertFalse(AlertArea.usgsDayFeedUrl(2.5).contains("latitude"))
    }
}
