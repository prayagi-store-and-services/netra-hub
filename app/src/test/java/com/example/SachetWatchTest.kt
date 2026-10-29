package com.example

import com.example.data.engine.CapAlert
import com.example.data.engine.SachetAlertEntry
import com.example.data.engine.SachetAlertState
import com.example.data.engine.SachetDecision
import com.example.data.engine.capMatchesPlace
import com.example.data.engine.haversineKm
import com.example.data.engine.parseCapAlert
import com.example.data.engine.parseSachetRss
import com.example.data.engine.pointInPolygon
import com.example.data.engine.wordMatch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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

    @Test fun moderateWarningsMatchOnlyTheirAreaAndValidity() {
        val a = parseCapAlert(cap.replace("<cap:severity>Severe", "<cap:severity>Moderate"))!!
        assertTrue(capMatchesPlace(a, "Varanasi", "", "", nowMillis = a.expiresMillis - 1))
        assertFalse(capMatchesPlace(a, "Pune", "", "", nowMillis = a.expiresMillis - 1))
        assertFalse(capMatchesPlace(a, "Varanasi", "", "", nowMillis = a.expiresMillis + 1))
    }

    @Test fun moderateToSevereAndBackAreChangesNotRepeats() {
        assertEquals(SachetDecision.SEVERITY_CHANGED, SachetAlertState.decide(SachetAlertEntry("Moderate", 100), "Severe"))
        assertEquals(SachetDecision.SEVERITY_CHANGED, SachetAlertState.decide(SachetAlertEntry("Severe", 100), "Moderate"))
        assertEquals(SachetDecision.SAME_SEVERITY_SKIP, SachetAlertState.decide(SachetAlertEntry("Moderate", 100), "Moderate"))
    }

    @Test fun parsesRss() {
        val rss = "<rss><channel><item><guid>7</guid><link>https://x/7</link></item><item><guid></guid><link>https://x/8</link></item></channel></rss>"
        assertEquals(listOf("7" to "https://x/7"), parseSachetRss(rss))
    }

    @Test fun matchesByDistrictAndRejectsExpiredOrUnknown() {
        val a = parseCapAlert(cap)!!
        val before = a.expiresMillis - 3_600_000L
        assertTrue(capMatchesPlace(a, "Varanasi", "Location Unavailable", "Uttar Pradesh", nowMillis = before))
        assertFalse(capMatchesPlace(a, "Pune", "Pune", "Maharashtra", nowMillis = before))
        assertFalse(capMatchesPlace(a, "Varanasi", "", "", nowMillis = a.expiresMillis + 1L))
        assertTrue(capMatchesPlace(a.copy(severity = "Minor"), "Varanasi", "", "", nowMillis = before))
        assertFalse(capMatchesPlace(a, "Location Unavailable", "Location Unavailable", "Location Unavailable", nowMillis = before))
    }

    @Test fun stateSenderAloneNeverMatches_farAlertRegression19Oct() {
        // The 19 Oct false alert: a warning for Varanasi/Ballia issued by the UP state body alerted a
        // phone in Prayagraj (~70-80 km away) because the sender name contained the state name.
        val a = parseCapAlert(cap)!!
        val before = a.expiresMillis - 3_600_000L
        assertFalse(capMatchesPlace(a, "Prayagraj", "Prayagraj", "Uttar Pradesh", nowMillis = before))
        assertFalse(capMatchesPlace(a, "Location Unavailable", "Location Unavailable", "Uttar Pradesh", nowMillis = before))
    }

    @Test fun substringOfLongerNameNeverMatches() {
        val a = parseCapAlert(cap)!! // area "Varanasi, Ballia"
        val before = a.expiresMillis - 3_600_000L
        assertFalse(capMatchesPlace(a, "Nasi", "Nasi", "Nasi", nowMillis = before))
        assertFalse(capMatchesPlace(a, "Balli", "", "", nowMillis = before))
        assertTrue(capMatchesPlace(a, "Ballia", "", "", nowMillis = before))
    }

    @Test fun stateNamedInAreaMatchesWholeStateWarning() {
        val a = parseCapAlert(cap.replace("Varanasi, Ballia", "Uttar Pradesh"))!!
        val before = a.expiresMillis - 3_600_000L
        assertTrue(capMatchesPlace(a, "Prayagraj", "Prayagraj", "Uttar Pradesh", nowMillis = before))
    }

    @Test fun wordMatchBasics() {
        assertTrue(wordMatch("varanasi, ballia", "varanasi"))
        assertFalse(wordMatch("varanasi, ballia", "nasi"))
        assertFalse(wordMatch("varanasi, ballia", ""))
    }

    private val square = listOf(25.0 to 82.0, 25.0 to 83.0, 26.0 to 83.0, 26.0 to 82.0)

    @Test fun polygonInsideAndOutside() {
        assertTrue(pointInPolygon(25.5, 82.5, square))
        assertFalse(pointInPolygon(24.0, 82.5, square))
    }

    @Test fun circleInsideAndOutside() {
        assertTrue(haversineKm(25.3, 83.0, 25.3, 83.0) < 1.0)
        assertTrue(haversineKm(25.3, 83.0, 26.0, 83.0) > 50.0)
    }

    @Test fun geometryDecidesWhenCoordinatesKnown() {
        val polyCap = cap.replace("<cap:areaDesc>Varanasi, Ballia</cap:areaDesc>",
            "<cap:areaDesc>Varanasi, Ballia</cap:areaDesc><cap:polygon>25.0,82.0 25.0,83.0 26.0,83.0 26.0,82.0</cap:polygon>")
        val a = parseCapAlert(polyCap)!!
        org.junit.Assert.assertEquals(4, a.polygon!!.size)
        val before = a.expiresMillis - 3_600_000L
        // Inside the polygon: matches even with an unrelated district name (geometry is authoritative).
        assertTrue(capMatchesPlace(a, "Prayagraj", "Prayagraj", "Uttar Pradesh", 25.5, 82.5, before))
        // Outside the polygon: no match, even though the district name appears in the area text.
        assertFalse(capMatchesPlace(a, "Varanasi", "Varanasi", "Uttar Pradesh", 24.0, 82.5, before))
        // Without coordinates: falls back to exact area text.
        assertTrue(capMatchesPlace(a, "Varanasi", "", "", null, null, before))
    }

    @Test fun circleAlertMatchesByRadius() {
        val circleCap = cap.replace("<cap:areaDesc>Varanasi, Ballia</cap:areaDesc>",
            "<cap:areaDesc>Area near Varanasi</cap:areaDesc><cap:circle>25.30,82.99 20</cap:circle>")
        val a = parseCapAlert(circleCap)!!
        org.junit.Assert.assertEquals(20.0, a.circleKm!!.third, 0.001)
        val before = a.expiresMillis - 3_600_000L
        assertTrue(capMatchesPlace(a, "Nowhere", "Nowhere", "Nowhere", 25.31, 83.0, before))
        assertFalse(capMatchesPlace(a, "Nowhere", "Nowhere", "Nowhere", 25.8, 83.5, before))
    }

    @Test fun noGeometryAndBlankAreaNeverAlerts() {
        val a = parseCapAlert(cap.replace("Varanasi, Ballia", ""))!!
        val before = a.expiresMillis - 3_600_000L
        assertFalse(capMatchesPlace(a, "Varanasi", "Varanasi", "Uttar Pradesh", nowMillis = before))
    }

    // SachetAlertState: the once-per-alert rule.
    @Test fun alertStateAnnouncesOncePerAlert() {
        val key = SachetAlertState.identityKey("Heavy Rainfall", "Prayagraj")
        assertEquals("heavy rainfall|prayagraj", key)
        assertEquals(SachetDecision.FIRST_ALERT, SachetAlertState.decide(null, "Severe"))
        val entry = SachetAlertEntry("Severe", 1000L)
        assertEquals(SachetDecision.SAME_SEVERITY_SKIP, SachetAlertState.decide(entry, "Severe"))
        assertEquals(SachetDecision.SEVERITY_CHANGED, SachetAlertState.decide(entry, "Extreme"))
        assertEquals(SachetDecision.SEVERITY_CHANGED, SachetAlertState.decide(entry, "Moderate"))
    }

    @Test fun alertStateSweepAndRoundTrip() {
        val active = mapOf(
            "rain|prayagraj" to SachetAlertEntry("Severe", 500L),
            "storm|varanasi" to SachetAlertEntry("Extreme", 5000L),
            "heat|lucknow" to SachetAlertEntry("Severe", 0L)
        )
        assertEquals(listOf("rain|prayagraj"), SachetAlertState.expiredKeys(active, 1000L))
        val round = SachetAlertState.decode(SachetAlertState.encode(active))
        assertEquals(active, round)
        assertEquals("rain, area prayagraj", SachetAlertState.readable("rain|prayagraj"))
    }
    @Test fun rejectsExternalEntitiesAndDtds() {
        val file = java.io.File.createTempFile("sachet-probe", ".txt")
        try {
            file.writeText("LOCAL_TEST_ENTITY_ONLY")
            val dtd = "<!DOCTYPE cap:alert [<!ENTITY probe SYSTEM '${file.toURI()}'>]>"
            assertNull(parseCapAlert(dtd + cap.replace("Thunderstorm likely", "&probe;")))
            val rss = "<!DOCTYPE rss [<!ENTITY probe SYSTEM '${file.toURI()}'>]><rss><channel><item><guid>7</guid><link>&probe;</link></item></channel></rss>"
            assertNull(parseSachetRss(rss))
        } finally { file.delete() }
    }

    @Test fun rejectsInternalEntityExpansionAndExternalDtd() {
        assertNull(parseSachetRss("<!DOCTYPE rss [<!ENTITY a 'many'><!ENTITY b '&a;&a;&a;'>]><rss><channel>&b;</channel></rss>"))
        assertNull(parseSachetRss("<!DOCTYPE rss SYSTEM 'https://example.invalid/never-fetch.dtd'><rss/>"))
    }

    @Test fun acceptsNormalXmlEntities() {
        assertEquals(listOf("7" to "https://sachet.ndma.gov.in/?a=1&b=2"), parseSachetRss("<rss><channel><item><guid>7</guid><link>https://sachet.ndma.gov.in/?a=1&amp;b=2</link></item></channel></rss>"))
    }
}
