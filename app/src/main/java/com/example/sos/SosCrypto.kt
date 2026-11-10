package com.example.sos

import android.util.Base64
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * AES-GCM seal/open for the small on-phone SOS secrets. Stored form: Base64( iv(12) | ciphertext+tag ).
 * Tampering or a wrong key makes [open] throw; callers decide the fallback. No data leaves the phone.
 */
internal object SosCrypto {
    private const val IV_LEN = 12
    private const val TAG_BITS = 128

    fun seal(key: SecretKey, plain: String): String {
        val iv = ByteArray(IV_LEN).also { SecureRandom().nextBytes(it) }
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
        return Base64.encodeToString(iv + c.doFinal(plain.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
    }

    fun open(key: SecretKey, blob: String): String {
        val raw = Base64.decode(blob, Base64.NO_WRAP)
        require(raw.size > IV_LEN) { "sealed value too short" }
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, raw.copyOfRange(0, IV_LEN)))
        return String(c.doFinal(raw.copyOfRange(IV_LEN, raw.size)), Charsets.UTF_8)
    }
}
