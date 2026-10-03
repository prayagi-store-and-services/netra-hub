package com.example.sos

import android.content.Context

/** Emergency contacts and the on/off switch, kept only on this phone (SharedPreferences). */
object SosStore {
    private const val FILE = "sos_prefs"
    private fun p(c: Context) = c.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun enabled(c: Context) = p(c).getBoolean("enabled", false)
    fun setEnabled(c: Context, v: Boolean) = p(c).edit().putBoolean("enabled", v).apply()
    fun contacts(c: Context): List<String> = (p(c).getString("contacts", "") ?: "").split(",").filter { it.isNotBlank() }
    fun addContact(c: Context, raw: String): Boolean {
        val n = SosLogic.cleanNumber(raw) ?: return false
        val cur = contacts(c)
        if (n in cur || cur.size >= SosLogic.MAX_CONTACTS) return false
        p(c).edit().putString("contacts", (cur + n).joinToString(",")).apply()
        return true
    }
    fun removeContact(c: Context, n: String) = p(c).edit().putString("contacts", contacts(c).filter { it != n }.joinToString(",")).apply()
    fun lastTrigger(c: Context) = p(c).getLong("last", 0L)
    fun setLastTrigger(c: Context, t: Long) = p(c).edit().putLong("last", t).apply()
}
