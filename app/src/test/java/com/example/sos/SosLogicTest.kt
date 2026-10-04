package com.example.sos

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SosLogicTest {
    @Test fun gentleMovementNeverTriggers() {
        val d = SosLogic.ShakeDetector()
        for (i in 0 until 100) assertFalse(d.onSample(0f, 0f, 9.81f, i * 20L))
    }
    @Test fun threeHardShakesTrigger() {
        val d = SosLogic.ShakeDetector()
        assertFalse(d.onSample(30f, 0f, 0f, 0L))
        assertFalse(d.onSample(-30f, 0f, 0f, 400L))
        assertTrue(d.onSample(30f, 0f, 0f, 800L))
    }
    @Test fun slowShakesDoNotTrigger() {
        val d = SosLogic.ShakeDetector()
        assertFalse(d.onSample(30f, 0f, 0f, 0L))
        assertFalse(d.onSample(30f, 0f, 0f, 1000L))
        assertFalse(d.onSample(30f, 0f, 0f, 2000L))
    }
    @Test fun oneShakeCountedOnce() {
        val d = SosLogic.ShakeDetector()
        assertFalse(d.onSample(30f, 0f, 0f, 0L))
        assertFalse(d.onSample(30f, 0f, 0f, 20L))
        assertFalse(d.onSample(30f, 0f, 0f, 40L))
    }
    @Test fun numberCleaning() {
        assertEquals("+919876543210", SosLogic.cleanNumber(" +91 98765-43210 "))
        assertEquals("9876543210", SosLogic.cleanNumber("98765 43210"))
        assertNull(SosLogic.cleanNumber("123"))
        assertNull(SosLogic.cleanNumber("abc"))
    }
    @Test fun smsSaysUnavailableWithoutFix() {
        assertTrue(SosLogic.smsBody(null).endsWith("Location Unavailable"))
        assertTrue(SosLogic.smsBody("link").contains("link"))
    }
    @Test fun cooldown() {
        assertTrue(SosLogic.canTrigger(1000L, 0L))
        assertFalse(SosLogic.canTrigger(10_000L, 1000L))
        assertTrue(SosLogic.canTrigger(70_000L, 1000L))
    }

    @Test fun ageCountsWholeYears() {
        assertEquals(30, SosLogic.ageYears("1996-10-04", 2026, 10, 4))
        assertEquals(29, SosLogic.ageYears("1996-10-05", 2026, 10, 4))
        assertEquals(null, SosLogic.ageYears("2027-01-01", 2026, 10, 4))
        assertEquals(null, SosLogic.ageYears("1996-02-30", 2026, 10, 4))
        assertEquals(null, SosLogic.ageYears("abc", 2026, 10, 4))
        assertEquals(null, SosLogic.ageYears("1800-01-01", 2026, 10, 4))
    }
    @Test fun bloodGroupIsOptionalAndChecked() {
        assertEquals("", SosLogic.cleanBlood("  "))
        assertEquals("AB+", SosLogic.cleanBlood("ab+"))
        assertEquals(null, SosLogic.cleanBlood("C+"))
    }
    @Test fun profileLineUsesOnlyWhatIsKnown() {
        assertEquals(null, SosLogic.profileLine("", null, ""))
        assertEquals("Name: Ravi, Age: 30, Blood group: O+", SosLogic.profileLine(" Ravi ", 30, "O+"))
        assertEquals("Age: 30", SosLogic.profileLine("", 30, ""))
    }
    @Test fun smsBodyAddsProfileAndBatteryBeforeLocation() {
        val b = SosLogic.smsBody("link", "Name: Ravi", 42)
        assertTrue(b.contains("Name: Ravi"))
        assertTrue(b.contains("Phone battery: 42%"))
        assertTrue(b.endsWith("link"))
        assertTrue(!SosLogic.smsBody("link", null, null).contains("battery"))
        assertTrue(!SosLogic.smsBody("link", null, 150).contains("battery"))
    }
}
