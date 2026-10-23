package com.example.data.engine

import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
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
 * Standing ecosystem rule (19 Oct 2026): while an announcement plays, other media must pause (audio
 * focus, not mute) and resume when it ends. Focus is requested before playback and abandoned when the
 * utterance finishes, errors, or the watchdog fires - never held open.
 */
object OfficialAlertAnnouncer {
    /** Serializes announcements so a quick ON/OFF pair can never play out of order. */
    private val speakLock = java.util.concurrent.locks.ReentrantLock()
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

    private object LegacyFocus

    /** Requests transient audio focus so other media pauses for the announcement. Null when not granted. */
    private fun requestFocus(am: AudioManager?, attrs: AudioAttributes): Any? {
        if (am == null) return null
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT).setAudioAttributes(attrs).build()
                if (am.requestAudioFocus(req) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) req else null
            } else {
                @Suppress("DEPRECATION")
                if (am.requestAudioFocus(null, AudioManager.STREAM_ALARM, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) LegacyFocus else null
            }
        } catch (_: Exception) { null }
    }

    private fun abandonFocus(am: AudioManager?, handle: Any?) {
        if (am == null || handle == null) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && handle is AudioFocusRequest) am.abandonAudioFocusRequest(handle)
            else @Suppress("DEPRECATION") am.abandonAudioFocus(null)
        } catch (_: Exception) { }
    }

    /** Clearly labelled test line: unmistakably a drill, never emergency wording. */
    const val TEST_TEXT = "This is a test announcement from Netra Hub. This is only a test. There is no emergency."

    /**
     * Plays the clearly labelled TEST announcement through the exact same path a real official
     * alert uses (alarm stream, TTS, transient audio focus with watchdog, ringtone fallback).
     * Runs even when the announce switch is off, because the user explicitly asked for a test.
     * Returns an honest status line for the UI; when a hard limit blocks playback, that limit
     * text is returned instead and nothing plays.
     */
    fun sendTestAnnouncement(context: Context): String {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        val limit = limitsText(
            nm?.currentInterruptionFilter ?: NotificationManager.INTERRUPTION_FILTER_ALL,
            am?.getStreamVolume(AudioManager.STREAM_ALARM) ?: 1
        )
        if (limit != null) return "Test announcement could not play. $limit"
        Handler(Looper.getMainLooper()).post { speak(context, TEST_TEXT) }
        return "Playing a test announcement now. It is only a test."
    }

    /**
     * Speaks a short Netra notice (driving mode on/off, test lines) with the same audio focus and watchdog as alerts.
     * Returns the limit text when nothing could play, null when the utterance was submitted. utteranceId identifies
     * the request; onComplete reports success/failure (also when the engine never calls back, via the watchdog).
     */
    fun speakNotice(context: Context, text: String, utteranceId: String = "netra_notice", onComplete: ((Boolean, String?) -> Unit)? = null): String? {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        val limit = limitsText(nm?.currentInterruptionFilter ?: NotificationManager.INTERRUPTION_FILTER_ALL, am?.getStreamVolume(AudioManager.STREAM_ALARM) ?: 1)
        if (limit != null) return limit
        Handler(Looper.getMainLooper()).post { speak(context, text, utteranceId, onComplete) }
        return null
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

    private fun speak(context: Context, text: String, utteranceId: String = "official_alert", onComplete: ((Boolean, String?) -> Unit)? = null) {
        val app = context.applicationContext
        val am = app.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        // One announcement at a time: two engines speaking over each other would play ON/OFF out of order.
        if (!speakLock.tryLock()) {
            try { onComplete?.invoke(false, "another announcement is still playing") } catch (_: Exception) { }
            return
        }
        // Armed BEFORE construction: a hung TTS init also reports failure instead of waiting forever.
        val done = java.util.concurrent.atomic.AtomicBoolean(false)
        val finish: (Boolean, String?) -> Unit = { ok, err ->
            if (done.compareAndSet(false, true)) {
                speakLock.unlock()
                try { onComplete?.invoke(ok, err) } catch (_: Exception) { }
            }
        }
        var tts: TextToSpeech? = null
        Handler(Looper.getMainLooper()).postDelayed({
            // Timed out: kill whatever init/engine is still hanging so a late callback cannot speak after OFF.
            try { tts?.shutdown() } catch (_: Exception) { }
            finish(false, "TTS init or engine callback missing within 60 s")
        }, 60_000L)
        tts = TextToSpeech(app) { status ->
            if (done.get()) {
                // Init arrived after the watchdog fired: the announcement is already reported failed.
                // Shut the engine down immediately so a stale ON can never speak after the timeout/OFF.
                try { tts?.shutdown() } catch (_: Exception) { }
                return@TextToSpeech
            }
            if (status == TextToSpeech.SUCCESS) {
                val lang = tts?.setLanguage(Locale.ENGLISH)
                if (lang != TextToSpeech.LANG_MISSING_DATA && lang != TextToSpeech.LANG_NOT_SUPPORTED) {
                    val attrs = AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                    tts?.setAudioAttributes(attrs)
                    val engine = tts
                    // Pause other media for the announcement; resume them when it ends. If focus is not
                    // granted the alert still plays (an official warning must not be silenced for it).
                    val focus = requestFocus(am, attrs)
                    val doneSpeak = java.util.concurrent.atomic.AtomicBoolean(false)
                    fun finishSpeak(ok: Boolean, err: String?) {
                        if (doneSpeak.compareAndSet(false, true)) {
                            abandonFocus(am, focus)
                            try { engine?.shutdown() } catch (_: Exception) { }
                            finish(ok, err)
                        }
                    }
                    engine?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) {}
                        override fun onDone(utteranceId: String?) { finishSpeak(true, null) }
                        @Deprecated("Deprecated in Java")
                        override fun onError(utteranceId: String?) { finishSpeak(false, "TTS engine error") }
                    })
                    // Watchdog: focus is never held open even if the engine drops the utterance callbacks.
                    Handler(Looper.getMainLooper()).postDelayed({ finishSpeak(false, "no engine callback within 60 s") }, 60_000L)
                    val params = Bundle()
                    params.putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, AudioManager.STREAM_ALARM)
                    val accepted = engine?.speak(text, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
                    if (accepted != TextToSpeech.SUCCESS) finishSpeak(false, "TTS engine rejected the utterance")
                    return@TextToSpeech
                }
            }
            // No usable TTS voice: fall back to the alarm ringtone for a few seconds, and say so truthfully.
            tts?.shutdown()
            val played = playAlarmFallback(app)
            finish(false, if (played) "no usable TTS voice (init failed or language missing); ringtone fallback played"
                else "no usable TTS voice and the ringtone fallback also failed")
        }
    }

    private fun playAlarmFallback(context: Context): Boolean {
        return try {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION) ?: return false
            val ringtone = RingtoneManager.getRingtone(context, uri) ?: return false
            val attrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            ringtone.audioAttributes = attrs
            val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            val focus = requestFocus(am, attrs)
            ringtone.play()
            Handler(Looper.getMainLooper()).postDelayed({
                try { ringtone.stop() } catch (_: Exception) {}
                abandonFocus(am, focus)
            }, 8000L)
            true
        } catch (_: Exception) {
            false
        }
    }
}
