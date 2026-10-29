package com.example

import com.example.data.engine.*
import org.junit.Assert.*
import org.junit.Test

@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [34])
class ImdWarningReportTest {
    private val now = 1793241000000L // 29 Oct 2026 Indian date
    private fun data(colour: Int = 3, date: String = "2026-10-29", hole: Boolean = false): String {
        val holes = if (hole) ",[[82.3,25.3],[82.7,25.3],[82.7,25.7],[82.3,25.7],[82.3,25.3]]" else ""
        return """{"type":"FeatureCollection","features":[{"geometry":{"type":"Polygon","coordinates":[[[82,25],[83,25],[83,26],[82,26],[82,25]]$holes]},"properties":{"Date":"$date","Day1_Color":$colour,"SUBDIV":"Test subdivision fixture","Day_1":"2,42"}}]}"""
    }
    @Test fun colourMappingMatchesOfficialRenderer() {
        assertEquals("Red", imdColour(1)); assertEquals("Orange", imdColour(2)); assertEquals("Yellow", imdColour(3)); assertEquals("Green", imdColour(4)); assertNull(imdColour(0))
    }
    @Test fun currentGeometrySelectsReportWithoutSendingLocation() {
        val r = parseImdWarning(data(),25.5,82.5,now)!!
        assertEquals("Yellow",r.colour); assertTrue(r.hazards.contains("Heavy rain")); assertTrue(r.hazards.contains("Thunderstorm")); assertNull(parseImdWarning(data(),24.0,82.5,now))
    }
    @Test fun staleUnknownColourAndPolygonHoleRejected() {
        assertNull(parseImdWarning(data(date="2026-10-28"),25.5,82.5,now))
        assertNull(parseImdWarning(data(colour=0),25.5,82.5,now))
        assertNull(parseImdWarning(data(hole=true),25.5,82.5,now))
    }
    @Test fun greenUpdatesSilentlyAndSameYellowDoesNotRepeat() {
        val r = parseImdWarning(data(),25.5,82.5,now)!!
        assertTrue(imdShouldSpeak(null,r)); assertFalse(imdShouldSpeak("Yellow",r)); assertTrue(imdShouldSpeak("Orange",r))
        assertFalse(imdShouldSpeak("Yellow",parseImdWarning(data(4),25.5,82.5,now)!!))
    }
}
