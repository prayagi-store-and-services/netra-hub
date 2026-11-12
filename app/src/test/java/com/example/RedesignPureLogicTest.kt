package com.example

import com.example.data.db.SafetyEventEntity
import com.example.data.engine.IntelligentSafetyStatusEngine
import com.example.driving.DrivingLog
import com.example.ui.screens.ReportsSummaryLogic
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

    @Test fun `reports driving stats pair starts with stops inside the window`() {
        val t0 = 1_000_000L
        val entries = listOf(
            DrivingLog.Entry(t0 + 50_000, DrivingLog.ENDED, null, ""),
            DrivingLog.Entry(t0 + 10_000, DrivingLog.STARTED, null, ""),
            DrivingLog.Entry(t0 + 70_000, DrivingLog.STARTED, null, ""),
            DrivingLog.Entry(t0 + 90_000, DrivingLog.ENDED, null, "")
        )
        val s = ReportsSummaryLogic.drivingStats(entries, t0, t0 + 100_000)
        assertEquals(2, s.trips)
        assertEquals(2, s.stops)
        assertEquals(60_000L, s.drivingMs)
        assertFalse(s.ongoing)
    }

    @Test fun `reports driving stats clip a trip that started before the window`() {
        val t0 = 1_000_000L
        val entries = listOf(
            DrivingLog.Entry(t0 - 60_000, DrivingLog.STARTED, null, ""),
            DrivingLog.Entry(t0 + 30_000, DrivingLog.ENDED, null, "")
        )
        val s = ReportsSummaryLogic.drivingStats(entries, t0, t0 + 100_000)
        assertEquals(0, s.trips)              // trip did not start inside the window
        assertEquals(1, s.stops)
        assertEquals(30_000L, s.drivingMs)    // clipped to the window, never extrapolated
        assertFalse(s.ongoing)
    }

    @Test fun `reports driving stats close an ongoing trip at now`() {
        val t0 = 1_000_000L
        val entries = listOf(DrivingLog.Entry(t0 + 10_000, DrivingLog.STARTED, null, ""))
        val s = ReportsSummaryLogic.drivingStats(entries, t0, t0 + 40_000)
        assertTrue(s.ongoing)
        assertEquals(30_000L, s.drivingMs)
        assertEquals(1, s.trips)
        assertEquals(0, s.stops)
    }

    @Test fun `reports summary counts reuse the alerts classifier`() {
        val t0 = 1_000_000L
        fun ev(domain: String, type: String, at: Long) = SafetyEventEntity(domain = domain, eventType = type, timestamp = at)
        val events = listOf(
            ev("SOS", "sos_triggered", t0 + 1),
            ev("WEATHER", "sachet_report", t0 + 2),
            ev("QUAKE", "usgs_quake", t0 + 3),
            ev("THERMAL", "battery_temp", t0 + 4),
            ev("SOS", "sos_triggered", t0 - 500)   // outside the window: must not count
        )
        val sum = ReportsSummaryLogic.summary(events, emptyList(), t0, t0 + 10_000)
        assertEquals(4, sum.safetyEvents)
        assertEquals(1, sum.sosEvents)
        assertEquals(1, sum.weatherEvents)
        assertEquals(1, sum.quakeEvents)
    }

    @Test fun `duration format is plain and bounded`() {
        assertEquals("0 min", ReportsSummaryLogic.formatDuration(0L))
        assertEquals("<1 min", ReportsSummaryLogic.formatDuration(30_000L))
        assertEquals("42 min", ReportsSummaryLogic.formatDuration(42 * 60_000L))
        assertEquals("1 h 24 min", ReportsSummaryLogic.formatDuration(84 * 60_000L))
    }
}
