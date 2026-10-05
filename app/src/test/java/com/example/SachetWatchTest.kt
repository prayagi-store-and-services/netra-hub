package com.example

import com.example.data.engine.CapAlert
import com.example.data.engine.capMatchesPlace
import com.example.data.engine.parseCapAlert
import com.example.data.engine.parseSachetRss
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SachetWatchTest {
    private val cap = """<cap:alert xmlns:cap="urn:oasis:names:tc:emergency:cap:1.2"><cap:identifier>IN-1</cap:identifier><cap:sender>Uttar-Pradesh-SDMA</cap:sender><cap:status>Actual</cap:status>
<cap:info><cap:language>en-IN</cap:language><cap:event>Moderate Rain</cap:event><cap:severity>Severe</cap:severity><cap:expires>2026-10-05T21:10:00+05:30</cap:expires><cap:headline>Thunderstorm likely</cap:headline><cap:area><cap:areaDesc>Varanasi, Ballia</cap:areaDesc></cap:area></cap:info></cap:alert>"""

    @Test fun parsesCap() {
        val a = parseCapAlert(cap)
        assertNotNull(a)
        assertEquals("Moderate Rain", a!!.event)
        assertEquals("Varanasi, Ballia", a.areaDesc)
        assertTrue(a.expiresMillis > 0L)
    }

    @Test fun parsesRss() {
        val rss = "<rss><channel><item><guid>7</guid><link>https://x/7</link></item><item><guid></guid><link>https://x/8</link></item></channel></rss>"
        assertEquals(listOf("7" to "https://x/7"), parseSachetRss(rss))
    }

    @Test fun matchesByDistrictAndRejectsExpiredOrUnknown() {
        val a = parseCapAlert(cap)!!
        val before = a.expiresMillis - 3_600_000L
        assertTrue(capMatchesPlace(a, "Varanasi", "Location Unavailable", "Uttar Pradesh", before))
        assertTrue(capMatchesPlace(a, "Location Unavailable", "Location Unavailable", "Uttar Pradesh", before)) // issuing state body
        assertFalse(capMatchesPlace(a, "Pune", "Pune", "Maharashtra", before))
        assertFalse(capMatchesPlace(a, "Varanasi", "", "", a.expiresMillis + 1L))
        assertFalse(capMatchesPlace(a.copy(severity = "Minor"), "Varanasi", "", "", before))
        assertFalse(capMatchesPlace(a, "Location Unavailable", "Location Unavailable", "Location Unavailable", before))
    }
}
