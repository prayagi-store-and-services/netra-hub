package com.example

import com.example.driving.DerivedSpeed
import com.example.driving.DrivingLog
import com.example.ui.screens.drivingStatusLines
import com.example.data.sensor.DrivingState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DrivingLogTest {
    @Test fun encodeDecodeRoundTrip() {
        val l = listOf(DrivingLog.Entry(2000L, DrivingLog.ENDED, null, "a|b\nc"), DrivingLog.Entry(1000L, DrivingLog.STARTED, 42.5f, "GPS speed reached 42 km/h"))
        val back = DrivingLog.decode(DrivingLog.encode(l))
        assertEquals(2, back.size)
        assertEquals(42.5f, back[1].speedKmH)
        assertNull(back[0].speedKmH)
        assertEquals("a/b c", back[0].reason)
    }
    @Test fun capKeepsNewest() {
        var l = emptyList<DrivingLog.Entry>()
        val total = DrivingLog.MAX + 50
        for (i in 1..total) l = DrivingLog.append(l, DrivingLog.Entry(i.toLong(), DrivingLog.NOTE, null, "n$i"))
        assertEquals(DrivingLog.MAX, l.size)
        assertEquals(total.toLong(), l.first().at)
    }
    @Test fun badLinesAreSkipped() = assertTrue(DrivingLog.decode("junk\n\nx|y").isEmpty())

    @Test fun derivedSpeedFromTwoFixes() {
        // about 111 m north in 10 s = about 40 km/h
        val v = DerivedSpeed.kmh(25.0, 82.0, 0L, 25.001, 82.0, 10_000L, 8f)
        assertNotNull(v); assertTrue(v!! in 38f..42f)
    }
    @Test fun derivedSpeedRefusesBadInput() {
        assertNull(DerivedSpeed.kmh(25.0, 82.0, 0L, 25.001, 82.0, 1_000L, 8f))
        assertNull(DerivedSpeed.kmh(25.0, 82.0, 0L, 25.001, 82.0, 120_000L, 8f))
        assertNull(DerivedSpeed.kmh(25.0, 82.0, 0L, 25.001, 82.0, 10_000L, 200f))
        assertEquals(0f, DerivedSpeed.kmh(25.0, 82.0, 0L, 25.00001, 82.0, 10_000L, 20f)!!, 0f)
    }
    @Test fun gpsJumpIsRejected() {
        // about 11 km in 10 s = 4000 km/h: not a real speed
        assertNull(DerivedSpeed.kmh(25.0, 82.0, 0L, 25.1, 82.0, 10_000L, 8f))
    }
    @Test fun announcementTextAndReasonLifecycle() {
        assertTrue(com.example.driving.DrivingEvents.startedText().contains("on"))
        assertTrue(com.example.driving.DrivingEvents.endedText().contains("off"))
        assertTrue(com.example.driving.DrivingEvents.reasonFor(true, 42f).contains("42 km/h"))
        assertTrue(com.example.driving.DrivingEvents.reasonFor(true, 42f).contains("15 km/h"))
        assertTrue(com.example.driving.DrivingEvents.reasonFor(false, null).contains("90 seconds"))
        val blind = com.example.driving.DrivingEvents.reasonFor(false, 55f, blindClose = true)
        assertTrue(blind.contains("no speed") && blind.contains("55 km/h") && !blind.contains("90 seconds"))
        assertTrue(com.example.driving.DrivingEvents.TEST_TEXT.lowercase().contains("only a test"))
    }
    @Test fun statusLinesAreHonest() {
        assertTrue(drivingStatusLines(false, DrivingState.SpeedInfo(), 1000L)[1].contains("Unavailable"))
        assertTrue(drivingStatusLines(true, DrivingState.SpeedInfo(30f, 900L, 900L), 1000L)[0].contains("ON"))
        assertTrue(drivingStatusLines(false, DrivingState.SpeedInfo(30f, 0L + 1L, 1L), 120_000L)[1].contains("Unavailable now"))
    }
}
