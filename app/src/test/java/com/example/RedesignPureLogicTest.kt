package com.example

import com.example.data.engine.IntelligentSafetyStatusEngine
import com.example.ui.screens.alertFilterOf
import com.example.ui.screens.greetingForHour
import com.example.ui.screens.heroStatusLine
import com.example.ui.screens.thermalVerdict
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure-logic guards for the redesign screens (greeting, hero line, alert filters, thermal verdict). */
class RedesignPureLogicTest {

    @Test fun `greeting follows time of day`() {
        assertEquals("Good Morning", greetingForHour(9))
        assertEquals("Good Afternoon", greetingForHour(14))
        assertEquals("Good Evening", greetingForHour(19))
        assertEquals("Good Night", greetingForHour(2))
    }

    @Test fun `hero line never hides critical status`() {
        assertEquals("All Systems Normal", heroStatusLine(IntelligentSafetyStatusEngine.SystemSafetyStatus.NORMAL).first)
        assertFalse(heroStatusLine(IntelligentSafetyStatusEngine.SystemSafetyStatus.CRITICAL).second)
        assertEquals("Attention needed", heroStatusLine(IntelligentSafetyStatusEngine.SystemSafetyStatus.CRITICAL).first)
    }

    @Test fun `alert filter maps domains honestly`() {
        assertEquals("Weather", alertFilterOf("WEATHER", ""))
        assertEquals("Earthquake", alertFilterOf("", "usgs_quake_detected"))
        assertEquals("SOS", alertFilterOf("SOS", ""))
        assertEquals("All", alertFilterOf("THERMAL", "overheat"))
    }

    @Test fun `thermal verdict respects the user threshold and unknown readings`() {
        assertEquals("Unavailable", thermalVerdict(0f, 45))
        assertEquals("Normal", thermalVerdict(37f, 45))
        assertEquals("Above your 45°C alert level", thermalVerdict(46f, 45))
        assertTrue(thermalVerdict(50f, 42).contains("42"))
    }
}
