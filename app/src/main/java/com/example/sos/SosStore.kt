package com.example.sos

import android.content.Context
import android.util.Log
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/**
 * Emergency contacts and the on/off switch, kept only on this phone.
 *
 * Sensitive fields (name, date of birth, blood group, contacts) are stored AES-GCM encrypted
 * under a device-only Android Keystore key ("<field>_enc"); the on/off, crash-alert and
 * last-trigger flags stay plaintext because they are not personal data. Values saved by older
 * versions in plaintext are migrated to the sealed form on first read.
 *
 * Failure policy (kept deliberately conservative because SOS is an emergency path): if the
 * Keystore is unusable, writes fall back to the pre-1.5.11 plaintext behaviour with a warning
 * instead of crashing or dropping the user's data; unreadable sealed values read back as empty.
 */
object SosStore {
    private const val FILE = "sos_prefs"
    private const val KEY_ALIAS = "sos_store_aes_v1"
    private const val TAG = "SosStore"

    private fun p(c: Context) = c.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    private fun secretKey(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val kg = KeyGenerator.getInstance("AES", "AndroidKeyStore")
        kg.init(
            android.security.keystore.KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                android.security.keystore.KeyProperties.PURPOSE_ENCRYPT or android.security.keystore.KeyProperties.PURPOSE_DECRYPT
            ).setBlockModes(android.security.keystore.KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(android.security.keystore.KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return kg.generateKey()
    }

    private fun getSecure(c: Context, key: String): String {
        val prefs = p(c)
        prefs.getString(key + "_enc", null)?.let { sealed ->
            return try {
                SosCrypto.open(secretKey(), sealed)
            } catch (e: Exception) {
                Log.e(TAG, "sealed $key unreadable (key lost or value tampered)", e)
                ""
            }
        }
        val legacy = prefs.getString(key, null) ?: return ""
        // One-time migration of a plaintext value written by an older version.
        return try {
            val sealed = SosCrypto.seal(secretKey(), legacy)
            if (prefs.edit().putString(key + "_enc", sealed).remove(key).commit()) legacy
            else legacy
        } catch (e: Exception) {
            Log.w(TAG, "keystore unavailable; $key stays plaintext for now", e)
            legacy
        }
    }

    private fun putSecure(c: Context, key: String, v: String) {
        try {
            p(c).edit().putString(key + "_enc", SosCrypto.seal(secretKey(), v)).remove(key).apply()
        } catch (e: Exception) {
            Log.w(TAG, "keystore unavailable; saving $key without encryption", e)
            p(c).edit().putString(key, v).apply()
        }
    }

    fun enabled(c: Context) = p(c).getBoolean("enabled", false)
    fun setEnabled(c: Context, v: Boolean) = p(c).edit().putBoolean("enabled", v).apply()
    fun crashAlert(c: Context) = p(c).getBoolean("crash_alert", false)
    fun setCrashAlert(c: Context, v: Boolean) = p(c).edit().putBoolean("crash_alert", v).apply()
    fun contacts(c: Context): List<String> = getSecure(c, "contacts").split(",").filter { it.isNotBlank() }
    fun addContact(c: Context, raw: String): Boolean {
        val n = SosLogic.cleanNumber(raw) ?: return false
        val cur = contacts(c)
        if (n in cur || cur.size >= SosLogic.MAX_CONTACTS) return false
        putSecure(c, "contacts", (cur + n).joinToString(","))
        return true
    }
    fun removeContact(c: Context, n: String) = putSecure(c, "contacts", contacts(c).filter { it != n }.joinToString(","))
    fun name(c: Context) = getSecure(c, "name")
    fun dob(c: Context) = getSecure(c, "dob")
    fun blood(c: Context) = getSecure(c, "blood")
    fun saveProfile(c: Context, name: String, dob: String, blood: String) {
        putSecure(c, "name", name.trim()); putSecure(c, "dob", dob.trim()); putSecure(c, "blood", blood)
    }
    fun lastTrigger(c: Context) = p(c).getLong("last", 0L)
    fun setLastTrigger(c: Context, t: Long) = p(c).edit().putLong("last", t).apply()
}
