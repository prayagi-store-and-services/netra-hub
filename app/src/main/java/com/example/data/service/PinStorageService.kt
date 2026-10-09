package com.example.data.service

import com.example.util.SecurityUtils
import android.util.Base64
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

interface PinStorageService {
    fun generateSalt(): String
    fun hashPin(pin: String, salt: String): String
    fun verifyPin(enteredPin: String, storedHash: String, storedSalt: String): Boolean
    fun hashPin(pin: String, salt: String, iterations: Int): String
    fun verifyPin(enteredPin: String, storedHash: String, storedSalt: String, iterations: Int): Boolean
}

class Pbkdf2PinStorageService : PinStorageService {
    companion object {
        private const val ITERATIONS = PinGate.LEGACY_ITERATIONS // count used by PINs saved before 1.1.23
        private const val KEY_LENGTH = 256
        private const val ALGORITHM = "PBKDF2WithHmacSHA256"
    }

    override fun generateSalt(): String {
        val random = SecureRandom()
        val saltBytes = ByteArray(32) // Strong 256-bit salt
        random.nextBytes(saltBytes)
        return Base64.encodeToString(saltBytes, Base64.NO_WRAP)
    }

    override fun hashPin(pin: String, salt: String): String = hashPin(pin, salt, ITERATIONS)

    override fun hashPin(pin: String, salt: String, iterations: Int): String {
        return try {
            val saltBytes = Base64.decode(salt, Base64.NO_WRAP)
            val spec = PBEKeySpec(pin.toCharArray(), saltBytes, iterations, KEY_LENGTH)
            val factory = SecretKeyFactory.getInstance(ALGORITHM)
            val hashBytes = factory.generateSecret(spec).encoded
            Base64.encodeToString(hashBytes, Base64.NO_WRAP)
        } catch (e: Exception) {
            // Fallback to SHA-256 with strong salt in case of platform crypto issues
            val md = java.security.MessageDigest.getInstance("SHA-256")
            val input = pin + salt
            val hashBytes = md.digest(input.toByteArray(Charsets.UTF_8))
            Base64.encodeToString(hashBytes, Base64.NO_WRAP)
        }
    }

    override fun verifyPin(enteredPin: String, storedHash: String, storedSalt: String): Boolean =
        verifyPin(enteredPin, storedHash, storedSalt, ITERATIONS)

    override fun verifyPin(enteredPin: String, storedHash: String, storedSalt: String, iterations: Int): Boolean {
        val enteredHash = hashPin(enteredPin, storedSalt, iterations)
        // Constant-time comparison to prevent timing attacks
        return constantTimeAreEqual(enteredHash, storedHash)
    }

    private fun constantTimeAreEqual(a: String, b: String): Boolean {
        val aBytes = a.toByteArray(Charsets.UTF_8)
        val bBytes = b.toByteArray(Charsets.UTF_8)
        if (aBytes.size != bBytes.size) return false
        var result = 0
        for (i in aBytes.indices) {
            result = result or (aBytes[i].toInt() xor bBytes[i].toInt())
        }
        return result == 0
    }
}


/**
 * The one rule for "is this PIN accepted". Pure logic, no Android, so it is unit tested.
 *
 * - Before the stored PIN has been read from the phone, nothing is accepted. A missing value must never be
 *   mistaken for "no custom PIN yet" (that let the default PIN in).
 * - The default PIN works only when no custom PIN has ever been saved.
 * - Once a custom PIN is saved, only that PIN works.
 */
object PinGate {
    const val DEFAULT_PIN = "000000"
    /** Iteration count used by every PIN saved before 1.1.23. Hashes without a stored count use this. */
    const val LEGACY_ITERATIONS = 12000
    /** Iteration count for newly saved PINs. Existing PINs are re-hashed to this after the next correct entry. */
    const val CURRENT_ITERATIONS = 310000

    enum class Result { OK, WRONG, NOT_READY }

    fun check(
        entered: String,
        ready: Boolean,
        storedHash: String?,
        storedSalt: String?,
        verifyStored: (String) -> Boolean
    ): Result {
        if (!ready) return Result.NOT_READY
        if (storedHash == null || storedSalt == null) {
            return if (entered == DEFAULT_PIN) Result.OK else Result.WRONG
        }
        return if (verifyStored(entered)) Result.OK else Result.WRONG
    }

    /** True when a correct PIN should be saved again with the stronger setting. */
    fun needsUpgrade(storedIterations: Int?, matchedLegacySha256: Boolean): Boolean =
        matchedLegacySha256 || (storedIterations ?: LEGACY_ITERATIONS) < CURRENT_ITERATIONS
}



/** What is stored for the PIN. A null snapshot means "not read from the phone yet". */
data class PinSnapshot(val hash: String?, val salt: String?, val iterations: Int?)

/** Checks an entered PIN against the stored one and says whether the stored hash should be made stronger. */
class PinChecker(private val storage: PinStorageService = Pbkdf2PinStorageService()) {
    data class Outcome(val result: PinGate.Result, val upgrade: Boolean)

    fun check(entered: String, snapshot: PinSnapshot?): Outcome {
        var matchedLegacySha = false
        val result = PinGate.check(entered, snapshot != null, snapshot?.hash, snapshot?.salt) { pin ->
            val hash = snapshot!!.hash!!
            val salt = snapshot.salt!!
            val iterations = snapshot.iterations ?: PinGate.LEGACY_ITERATIONS
            when {
                storage.verifyPin(pin, hash, salt, iterations) -> true
                SecurityUtils.hashPin(pin, salt) == hash -> { matchedLegacySha = true; true }
                else -> false
            }
        }
        val customPinSaved = snapshot?.hash != null && snapshot.salt != null
        val upgrade = result == PinGate.Result.OK && customPinSaved &&
            PinGate.needsUpgrade(snapshot?.iterations, matchedLegacySha)
        return Outcome(result, upgrade)
    }

    /** New salt and hash at the current strength for a PIN that was just entered correctly. */
    fun stronger(pin: String): Pair<String, String> {
        val salt = storage.generateSalt()
        return storage.hashPin(pin, salt, PinGate.CURRENT_ITERATIONS) to salt
    }
}
