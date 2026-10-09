package com.example

import com.example.data.service.PinGate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PinGateTest {
    private val customOk: (String) -> Boolean = { it == "482915" }

    @Test fun defaultPinWorksOnlyWhenNoCustomPinIsSaved() {
        assertEquals(PinGate.Result.OK, PinGate.check("000000", true, null, null, customOk))
        assertEquals(PinGate.Result.WRONG, PinGate.check("123456", true, null, null, customOk))
    }

    @Test fun defaultPinIsRejectedOnceACustomPinIsSaved() {
        assertEquals(PinGate.Result.WRONG, PinGate.check("000000", true, "hash", "salt", customOk))
        assertEquals(PinGate.Result.OK, PinGate.check("482915", true, "hash", "salt", customOk))
        assertEquals(PinGate.Result.WRONG, PinGate.check("111111", true, "hash", "salt", customOk))
    }

    @Test fun nothingIsAcceptedBeforeTheStoredPinIsRead() {
        // The bug: a value not read yet looked like "no custom PIN", so the default PIN got in.
        assertEquals(PinGate.Result.NOT_READY, PinGate.check("000000", false, null, null, customOk))
        assertEquals(PinGate.Result.NOT_READY, PinGate.check("482915", false, "hash", "salt", customOk))
    }

    @Test fun olderPinsAreUpgradedToTheStrongerSetting() {
        assertTrue(PinGate.needsUpgrade(null, false))
        assertTrue(PinGate.needsUpgrade(PinGate.LEGACY_ITERATIONS, false))
        assertTrue(PinGate.needsUpgrade(PinGate.CURRENT_ITERATIONS, true))
        assertFalse(PinGate.needsUpgrade(PinGate.CURRENT_ITERATIONS, false))
        assertTrue(PinGate.CURRENT_ITERATIONS > PinGate.LEGACY_ITERATIONS)
    }
}
