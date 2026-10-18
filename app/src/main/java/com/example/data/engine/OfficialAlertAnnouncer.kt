package com.example.data.engine

import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.RingtoneManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

/**
 * Speaks official/government alerts (NDMA SACHET, USGS quake watch) aloud even during silent time.
 * Sound plays on the alarm audio stream, which Android lets through a silent ringer and through
 * Do Not Disturb in its default and "alarms only" modes. No app can override alarm volume zero or
 * a DND mode that blocks alarms; limitsStatus() says so honestly. Without a TTS voice the phone's
 * alarm ringtone plays instead. Controlled by a user-visible toggle (default ON).
 */
object OfficialAlertAnnouncer {
    const val PREFS = "official_alert_announcer"
    const val KEY_ENABLED = "announce_enabled"

    /** Official categories only. Anything else never announces. */
    fun shouldAnnounce(category: String): Boolean = category == "sachet" || category == "quake"

    fun isEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ENABLED, true)

    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    /** The spoken sentence: source and area are always named. */
    fun announcementText(category: String, event: String, headline: String, areaDesc: String): String {
        val source = if (category == "quake") "USGS earthquake feed" else "NDMA SACHET, the official India alert feed"
        val area = areaDesc.trim()
        return ("Official alert from " + source + ". " + event.trim() + ". " + headline.trim() +
            (if (area.isNotEmpty()) " Area: $area." else ""))
    }

    /**
     * Honest limit text, or null when an announcement can play.
     * interruptionFilter: NotificationManager current filter. alarmVolume: STREAM_ALARM volume.
     */
    fun limitsText(interruptionFilter: Int, alarmVolume: Int): String? = when {
        alarmVolume <= 0 -> "Announcement cannot play: alarm volume is zero. Raise it in Sound settings; no app can override this."
        interruptionFilter == NotificationManager.INTERRUPTION_FILTER_NONE ->
            "Announcement cannot play: Do Not Disturb is set to total silence and blocks even alarms. No app can override this."
        else -> null
    }

    /** True when DND access would let the app read policy state (we never change DND ourselves). */
    fun hasPolicyAccess(context: Context): Boolean {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return false
        return nm.isNotificationPolicyAccessGranted
    }

    /** Announce aloud if enabled and the category is official. Silently does nothing otherwise. */
    fun announce(context: Context, category: String, event: String, headline: String, areaDesc: String) {
        if (!shouldAnnounce(category) || !isEnabled(context)) return
        val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        val limit = limitsText(
            nm?.currentInterruptionFilter ?: NotificationManager.INTERRUPTION_FILTER_ALL,
            am?.getStreamVolume(AudioManager.STREAM_ALARM) ?: 1
        )
        if (limit != null) return // the honest limit is shown on the status card; nothing can override it
        val text = announcementText(category, event, headline, areaDesc)
        // TextToSpeech needs a Looper; workers run on plain background threads.
        Handler(Looper.getMainLooper()).post { speak(context, text) }
    }

    private fun speak(context: Context, text: String) {
        val app = context.applicationContext
        var tts: TextToSpeech? = null
        tts = TextToSpeech(app) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val lang = tts?.setLanguage(Locale.ENGLISH)
                if (lang != TextToSpeech.LANG_MISSING_DATA && lang != TextToSpeech.LANG_NOT_SUPPORTED) {
                    val attrs = AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                    tts?.setAudioAttributes(attrs)
                    val engine = tts
                    engine?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) {}
                        override fun onDone(utteranceId: String?) { engine.shutdown() }
                        @Deprecated("Deprecated in Java")
                        override fun onError(utteranceId: String?) { engine.shutdown() }
                    })
                    val params = Bundle()
                    params.putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, AudioManager.STREAM_ALARM)
                    engine?.speak(text, TextToSpeech.QUEUE_FLUSH, params, "official_alert")
                    return@TextToSpeech
                }
            }
            // No usable TTS voice: fall back to the alarm ringtone for a few seconds.
            tts?.shutdown()
            playAlarmFallback(app)
        }
    }

    private fun playAlarmFallback(context: Context) {
        try {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION) ?: return
            val ringtone = RingtoneManager.getRingtone(context, uri) ?: return
            ringtone.audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            ringtone.play()
            Handler(Looper.getMainLooper()).postDelayed({ try { ringtone.stop() } catch (_: Exception) {} }, 8000L)
        } catch (_: Exception) {
        }
    }
}
