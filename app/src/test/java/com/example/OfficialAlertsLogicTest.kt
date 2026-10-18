package com.example

import com.example.data.engine.OfficialAlertAnnouncer
import com.example.data.engine.SafetyCheckTrigger
import com.example.data.engine.noWarningStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OfficialAlertsLogicTest {

    @Test fun onlyOfficialCategoriesAnnounce() {
        assertTrue(OfficialAlertAnnouncer.shouldAnnounce("sachet"))
        assertTrue(OfficialAlertAnnouncer.shouldAnnounce("quake"))
        assertFalse(OfficialAlertAnnouncer.shouldAnnounce("promo"))
        assertFalse(OfficialAlertAnnouncer.shouldAnnounce(""))
    }

    @Test fun announcementNamesSourceAndArea() {
        val t = OfficialAlertAnnouncer.announcementText("sachet", "Heavy Rainfall", "Orange warning", "Prayagraj")
        assertTrue(t.contains("NDMA SACHET"))
        assertTrue(t.contains("Prayagraj"))
        val q = OfficialAlertAnnouncer.announcementText("quake", "Earthquake magnitude 5.1", "Hindu Kush", "210 km from your area")
        assertTrue(q.contains("USGS"))
        assertTrue(q.contains("210 km"))
    }

    @Test fun alarmVolumeZeroBlocksWithHonestText() {
        val t = OfficialAlertAnnouncer.limitsText(android.app.NotificationManager.INTERRUPTION_FILTER_ALL, 0)
        assertNotNull(t)
        assertTrue(t!!.contains("alarm volume is zero"))
    }

    @Test fun totalSilenceDndBlocksWithHonestText() {
        val t = OfficialAlertAnnouncer.limitsText(android.app.NotificationManager.INTERRUPTION_FILTER_NONE, 5)
        assertNotNull(t)
        assertTrue(t!!.contains("Do Not Disturb"))
    }

    @Test fun normalStateHasNoLimit() {
        assertNull(OfficialAlertAnnouncer.limitsText(android.app.NotificationManager.INTERRUPTION_FILTER_ALL, 5))
        assertNull(OfficialAlertAnnouncer.limitsText(android.app.NotificationManager.INTERRUPTION_FILTER_ALARMS, 5))
    }

    @Test fun noWarningStatusNamesDistrictAndSource() {
        val s = noWarningStatus("Prayagraj", "Civil Lines", "Uttar Pradesh", 3, 0)
        assertTrue(s.startsWith("No official warning for Prayagraj"))
        assertTrue(s.contains("NDMA SACHET"))
        assertTrue(s.contains("IMD"))
    }

    @Test fun noWarningStatusFallsBackToStateThenGeneric() {
        assertTrue(noWarningStatus("Location Unavailable", "", "Bihar", 1, 0).contains("Bihar"))
        assertTrue(noWarningStatus("", "", "", 1, 0).contains("your area"))
    }

    @Test fun matchedStatusSaysNotified() {
        val s = noWarningStatus("Prayagraj", "", "Uttar Pradesh", 2, 1)
        assertTrue(s.startsWith("Notified: 1"))
    }

    @Test fun freshFixTriggerIsThrottled() {
        assertFalse(SafetyCheckTrigger.due(100_000L, 0L + 100_000L - 1))
        assertTrue(SafetyCheckTrigger.due(SafetyCheckTrigger.MIN_INTERVAL_MS, 0L))
        assertFalse(SafetyCheckTrigger.due(SafetyCheckTrigger.MIN_INTERVAL_MS - 1, 0L))
    }
}
