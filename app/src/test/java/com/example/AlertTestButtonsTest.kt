package com.example

import com.example.data.engine.OfficialAlertAnnouncer
import com.example.util.NetraNotificationManager
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The clearly-labelled TEST paths (ecosystem rule, 19 Oct 2026): a test must exercise the real
 * alert machinery while its wording can never be mistaken for a real emergency.
 */
class AlertTestButtonsTest {

    @Test
    fun testAnnouncementIsClearlyLabelled() {
        val text = OfficialAlertAnnouncer.TEST_TEXT.lowercase()
        assertTrue(text.contains("test"))
        assertTrue(text.contains("no emergency"))
        // A drill never carries real alert wording.
        assertFalse(text.startsWith("official alert from"))
    }

    @Test
    fun testAnnouncementUsesTheRealPathButNotRealWording() {
        // The real alert sentence always names its official source; the test line must not.
        val real = OfficialAlertAnnouncer.announcementText("quake", "M5.1", "Epicentre near X", "Varanasi")
        assertTrue(real.startsWith("Official alert from"))
        assertFalse(OfficialAlertAnnouncer.TEST_TEXT.startsWith("Official alert from"))
    }

    @Test
    fun testNotificationIsClearlyLabelled() {
        assertTrue(NetraNotificationManager.TEST_TITLE.startsWith("NETRA TEST"))
        assertFalse(NetraNotificationManager.TEST_TITLE.startsWith("NETRA ALERT"))
        assertTrue(NetraNotificationManager.TEST_TEXT.contains("no emergency") || NetraNotificationManager.TEST_TEXT.contains("no emergency", ignoreCase = true))
    }

    @Test
    fun testNotificationHasItsOwnId() {
        assertTrue(NetraNotificationManager.NOTIFICATION_ID_TEST != NetraNotificationManager.NOTIFICATION_ID_EMERGENCY)
    }
}
