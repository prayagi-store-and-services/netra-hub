package com.example.sos

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import javax.crypto.KeyGenerator

@RunWith(RobolectricTestRunner::class)
class SosCryptoTest {
    private fun key(id: Int = 1): javax.crypto.SecretKey {
        // Deterministic software AES keys for the pure-JVM crypto path (device uses Android Keystore).
        val kg = KeyGenerator.getInstance("AES")
        kg.init(128, java.security.SecureRandom(byteArrayOf(id.toByte())))
        return kg.generateKey()
    }

    @Test fun roundtripPlainAscii() {
        assertEquals("+919812345678,+919876543210", SosCrypto.open(key(), SosCrypto.seal(key(), "+919812345678,+919876543210")))
    }

    @Test fun roundtripUnicodeAndEmpty() {
        val k = key()
        assertEquals("दीपक O+ 1985-04-12", SosCrypto.open(k, SosCrypto.seal(k, "दीपक O+ 1985-04-12")))
        assertEquals("", SosCrypto.open(k, SosCrypto.seal(k, "")))
    }

    @Test fun sameInputSealsDifferently() {
        val k = key()
        assertNotEquals(SosCrypto.seal(k, "O+"), SosCrypto.seal(k, "O+"))
    }

    @Test fun tamperedBlobIsRejected() {
        val k = key()
        val sealed = SosCrypto.seal(k, "secret")
        val chars = sealed.toCharArray()
        chars[sealed.length - 2] = if (chars[sealed.length - 2] == 'A') 'B' else 'A'
        assertThrows(Exception::class.java) { SosCrypto.open(k, String(chars)) }
    }

    @Test fun wrongKeyIsRejected() {
        val sealed = SosCrypto.seal(key(1), "secret")
        assertThrows(Exception::class.java) { SosCrypto.open(key(2), sealed) }
    }

    @Test fun garbageBlobIsRejected() {
        assertThrows(Exception::class.java) { SosCrypto.open(key(), "not-base64!!!") }
    }
}
