package com.example.push

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import java.util.Locale

/** One alert received by push. Built only from the fields the sender put in the message. */
data class PushAlert(val id: String, val title: String, val body: String, val expiresMillis: Long, val lang: String)

/**
 * Instant alerts by Firebase Cloud Messaging topics. Opt-in, default OFF.
 * The phone works out its own state and district name, turns them into public topic names and subscribes to them.
 * No place name, coordinates or ID is sent to any server of ours.
 */
object PushAlerts {
    const val PREFS = "push_alerts"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_TOPICS = "topics"
    private const val KEY_SEEN = "seen_ids"
    private const val KEY_STATUS = "status"

    fun isEnabled(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ENABLED, false)
    fun status(c: Context): String? = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_STATUS, null)
    fun topics(c: Context): Set<String> = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getStringSet(KEY_TOPICS, emptySet()) ?: emptySet()

    /** True only when this build carries a Firebase client configuration. Without it nothing can be received. */
    fun isConfigured(c: Context): Boolean = try { FirebaseApp.getApps(c).isNotEmpty() } catch (e: Exception) { false }

    fun setEnabled(c: Context, on: Boolean, state: String, district: String) {
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_ENABLED, on).apply()
        sync(c, state, district)
    }

    private fun setStatus(c: Context, s: String) { c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_STATUS, s).apply() }

    /** Makes the topic subscriptions match the setting and the phone's current state and district. */
    fun sync(c: Context, state: String, district: String) {
        val prefs = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val old = topics(c)
        val want: Set<String> = if (isEnabled(c)) topicsFor(state, district).toSet() else emptySet()
        if (!isConfigured(c)) {
            setStatus(c, "Unavailable: instant alerts are not set up in this build.")
            return
        }
        if (isEnabled(c) && want.isEmpty()) {
            setStatus(c, "Unavailable: the phone's state or district is not known yet.")
            return
        }
        try {
            val fm = FirebaseMessaging.getInstance()
            (old - want).forEach { fm.unsubscribeFromTopic(it) }
            (want - old).forEach { fm.subscribeToTopic(it) }
            prefs.edit().putStringSet(KEY_TOPICS, want).apply()
            setStatus(c, if (want.isEmpty()) "Off." else "On for " + want.size + " area group(s). Subscription requested; Android and Google decide when it is active.")
        } catch (e: Exception) {
            setStatus(c, "Unavailable: could not update the area subscription.")
        }
    }

    /** Returns true the first time an alert id is seen (and remembers it). */
    fun firstTime(c: Context, id: String): Boolean {
        val prefs = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val seen = prefs.getStringSet(KEY_SEEN, emptySet()) ?: emptySet()
        if (id in seen) return false
        prefs.edit().putStringSet(KEY_SEEN, (seen + id).toList().takeLast(200).toSet()).apply()
        return true
    }

    /** Topic-safe name: lower case letters and digits, other runs become one underscore. Null when nothing is left. */
    fun slug(s: String): String? {
        val t = s.lowercase(Locale.ROOT).replace(Regex("[^a-z0-9]+"), "_").trim('_')
        return if (t.isEmpty() || t.contains("unavailable")) null else t.take(40)
    }

    /** "in_<state>" and "in_<state>__<district>". Empty when the state is not known. */
    fun topicsFor(state: String, district: String): List<String> {
        val st = slug(state) ?: return emptyList()
        val out = mutableListOf("in_$st")
        slug(district)?.let { out.add("in_${st}__$it") }
        return out
    }

    /** Reads the data fields of a push. Null when required fields are missing or the alert has expired. */
    fun parse(data: Map<String, String>, nowMillis: Long): PushAlert? {
        val id = data["id"]?.trim().orEmpty()
        val title = data["title"]?.trim().orEmpty()
        val body = data["body"]?.trim().orEmpty()
        if (id.isEmpty() || title.isEmpty() || body.isEmpty()) return null
        val exp = data["expires"]?.toLongOrNull() ?: 0L
        if (exp != 0L && exp < nowMillis) return null
        return PushAlert(id.take(80), title.take(120), body.take(400), exp, data["lang"]?.trim().orEmpty())
    }
}
