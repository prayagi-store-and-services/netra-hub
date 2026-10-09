package com.example

import com.example.data.service.PinChecker
import com.example.data.service.PinGate
import com.example.data.service.PinSnapshot
import com.example.data.service.Pbkdf2PinStorageService
import com.example.ui.PinChangeViewModel
import com.example.util.PinStrength
import com.example.util.PinStrengthAnalyzer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PinSecurityTest {

    private val pinStorageService = Pbkdf2PinStorageService()

    @Test
    fun testPbkdf2HashingAndVerification() {
        val pin = "937654"
        val salt = pinStorageService.generateSalt()
        
        // Ensure salt is generated and not empty
        assertNotNull(salt)
        assertTrue(salt.isNotEmpty())

        val hash = pinStorageService.hashPin(pin, salt)
        assertNotNull(hash)
        assertTrue(hash.isNotEmpty())

        // Verification of correct PIN
        val isValid = pinStorageService.verifyPin(pin, hash, salt)
        assertTrue("PBKDF2 verification should succeed for correct PIN", isValid)

        // Verification of incorrect PIN
        val isInvalid = pinStorageService.verifyPin("111111", hash, salt)
        assertFalse("PBKDF2 verification should fail for incorrect PIN", isInvalid)
    }

    @Test
    fun testPinStrengthAnalyzer() {
        // Weak PINs (repeating or sequential)
        assertEquals(PinStrength.WEAK, PinStrengthAnalyzer.analyze("1111"))
        assertEquals(PinStrength.WEAK, PinStrengthAnalyzer.analyze("1234"))
        assertEquals(PinStrength.WEAK, PinStrengthAnalyzer.analyze("000000"))
        assertEquals(PinStrength.WEAK, PinStrengthAnalyzer.analyze("121212"))
        assertEquals(PinStrength.WEAK, PinStrengthAnalyzer.analyze("9988")) // Too repeating/low diversity

        // Medium PINs (some duplicate characters or simple patterns)
        assertEquals(PinStrength.MEDIUM, PinStrengthAnalyzer.analyze("4821")) // Short but distinct
        assertEquals(PinStrength.MEDIUM, PinStrengthAnalyzer.analyze("9879")) // Length 4, 3 unique digits

        // Strong PINs (unique digits, no sequences, 6 digits)
        assertEquals(PinStrength.STRONG, PinStrengthAnalyzer.analyze("937654"))
        assertEquals(PinStrength.STRONG, PinStrengthAnalyzer.analyze("825471"))
    }
}



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



/** End to end: default PIN, forced change, own PIN only, and the upgrade of an old hash. */
@RunWith(RobolectricTestRunner::class)
class PinCheckerTest {
    private val storage = Pbkdf2PinStorageService()
    private val checker = PinChecker(storage)

    @Test fun fullFlow_defaultThenOwnPinOnly() {
        // 1. Fresh install: nothing saved, the default PIN opens developer mode.
        val fresh = PinSnapshot(null, null, null)
        assertEquals(PinGate.Result.OK, checker.check("000000", fresh).result)
        assertFalse(checker.check("000000", fresh).upgrade)

        // 2. The user sets their own PIN. It is saved at the current strength.
        val salt = storage.generateSalt()
        val hash = storage.hashPin("482915", salt, PinGate.CURRENT_ITERATIONS)
        val changed = PinSnapshot(hash, salt, PinGate.CURRENT_ITERATIONS)

        // 3. Only the user's PIN works now. The default does not.
        assertEquals(PinGate.Result.OK, checker.check("482915", changed).result)
        assertEquals(PinGate.Result.WRONG, checker.check("000000", changed).result)
        assertEquals(PinGate.Result.WRONG, checker.check("111111", changed).result)
        assertFalse(checker.check("482915", changed).upgrade)
    }

    @Test fun notReadyYet_neverAcceptsTheDefaultPin() {
        assertEquals(PinGate.Result.NOT_READY, checker.check("000000", null).result)
    }

    @Test fun oldPbkdf2PinStillWorksAndIsUpgraded() {
        val salt = storage.generateSalt()
        val oldHash = storage.hashPin("482915", salt) // 12,000 rounds, as saved before this version
        val snap = PinSnapshot(oldHash, salt, null)
        val ok = checker.check("482915", snap)
        assertEquals(PinGate.Result.OK, ok.result)
        assertTrue(ok.upgrade)
        assertEquals(PinGate.Result.WRONG, checker.check("000000", snap).result)

        // After the upgrade the new hash verifies with the new count and the old count no longer matches.
        val (newHash, newSalt) = checker.stronger("482915")
        val upgraded = PinSnapshot(newHash, newSalt, PinGate.CURRENT_ITERATIONS)
        assertEquals(PinGate.Result.OK, checker.check("482915", upgraded).result)
        assertFalse(checker.check("482915", upgraded).upgrade)
        assertFalse(storage.verifyPin("482915", newHash, newSalt, PinGate.LEGACY_ITERATIONS))
    }

    @Test fun veryOldSha256PinStillWorksAndIsUpgraded() {
        val salt = com.example.util.SecurityUtils.generateSalt()
        val shaHash = com.example.util.SecurityUtils.hashPin("482915", salt)
        val snap = PinSnapshot(shaHash, salt, null)
        val ok = checker.check("482915", snap)
        assertEquals(PinGate.Result.OK, ok.result)
        assertTrue(ok.upgrade)
        assertEquals(PinGate.Result.WRONG, checker.check("000000", snap).result)
    }
}
