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
    const val PREFS = "official_alert_announcer"

    /** Minimal engine surface so tests can drive a deterministic fake TTS (threads, timing, failures). */
    internal interface TtsEngine {
        fun setLanguage(locale: Locale): Int
        fun setAudioAttributes(attrs: AudioAttributes): Int
        fun speak(text: String, queueMode: Int, params: Bundle, utteranceId: String): Int
        fun setOnUtteranceProgressListener(listener: UtteranceProgressListener)
        fun shutdown()
    }
    internal fun interface TtsInit { fun create(context: Context, onInit: (Int) -> Unit): TtsEngine }
    private class RealTts(context: Context, onInit: (Int) -> Unit) : TtsEngine {
        private val tts = TextToSpeech(context) { status -> onInit(status) }
        override fun setLanguage(locale: Locale): Int = tts.setLanguage(locale)
        override fun setAudioAttributes(attrs: AudioAttributes): Int = tts.setAudioAttributes(attrs)
        override fun speak(text: String, queueMode: Int, params: Bundle, utteranceId: String): Int = tts.speak(text, queueMode, params, utteranceId)
        override fun setOnUtteranceProgressListener(listener: UtteranceProgressListener) { tts.setOnUtteranceProgressListener(listener) }
        override fun shutdown() { tts.shutdown() }
    }
    @Volatile internal var ttsInit: TtsInit = TtsInit { c, cb -> RealTts(c, cb) }

    /** Serialization: a single OWNER (the main loop) holds all speak/finish transitions, so there is no
     * cross-thread race at all. Every accepted announcement is a genuine event (a committed driving edge,
     * a quake/SACHET alert) and plays exactly once, in arrival order; nothing is ever superseded or dropped
     * here. The queue is unbounded by design: dropping a safety announcement is worse than queueing it. */
    private val speakBusy = java.util.concurrent.atomic.AtomicBoolean(false)
    private data class QueuedSpeak(val context: Context, val text: String, val utteranceId: String, val onComplete: ((Boolean, String?) -> Unit)?)
    private val speakQueue = java.util.concurrent.LinkedBlockingQueue<QueuedSpeak>()

    @androidx.annotation.VisibleForTesting
    internal fun resetAnnouncerForTests() { speakBusy.set(false); speakQueue.clear() }
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
        // Single-owner rule: every transition happens on the main loop. Anything else is re-posted.
        if (Looper.myLooper() != Looper.getMainLooper()) {
            Handler(Looper.getMainLooper()).post { speak(context, text, utteranceId, onComplete) }
            return
        }
        // One announcement at a time: a busy one keeps playing, later requests queue in FIFO order.
        if (speakBusy.get()) {
            speakQueue.offer(QueuedSpeak(app, text, utteranceId, onComplete))
            return
        }
        startSpeakOnMain(app, text, utteranceId, onComplete)
        return
    }

    private fun amOf(app: Context) = app.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    /** Runs on the main loop only; keeps the busy flag held through the whole utterance lifetime and hands
     * it directly to the next queued request on completion, so a reentrant speak from inside onComplete
     * always lands BEHIND the already-queued work. */
    private fun startSpeakOnMain(app: Context, text: String, utteranceId: String, onComplete: ((Boolean, String?) -> Unit)?) {
        speakBusy.set(true)
        val am = amOf(app)
        // Armed BEFORE construction: a hung TTS init also reports failure instead of waiting forever.
        val done = java.util.concurrent.atomic.AtomicBoolean(false)
        val finish: (Boolean, String?) -> Unit = { ok, err ->
            // Callbacks arrive on the TTS binder thread: marshal completion to the owner (main loop),
            // so release -> callback -> drain can never interleave with a new arrival.
            if (done.compareAndSet(false, true)) {
                Handler(Looper.getMainLooper()).post {
                    // Drain BEFORE the callback: busy stays held through the handoff, so a reentrant speak
                    // from inside onComplete queues behind, never overtakes. The queue is only released
                    // (busy=false) when there is genuinely nothing left to play.
                    val next = speakQueue.poll()
                    if (next == null) speakBusy.set(false)
                    try { onComplete?.invoke(ok, err) } catch (_: Exception) { }
                    if (next != null) startSpeakOnMain(next.context, next.text, next.utteranceId, next.onComplete)
                }
            }
        }
        var engineRef: TtsEngine? = null
        Handler(Looper.getMainLooper()).postDelayed({
            // Stale-timer guard: if this utterance already finished, this old watchdog must NOT touch the
            // (possibly reused) engine instance - only a genuinely still-running attempt is killed.
            if (!done.get()) {
                try { engineRef?.shutdown() } catch (_: Exception) { }
                finish(false, "TTS init or engine callback missing within 60 s")
            }
        }, 60_000L)
        engineRef = ttsInit.create(app) { status ->
            if (done.get()) {
                // Init arrived after the watchdog fired: the announcement is already reported failed.
                // Shut the engine down immediately so a stale ON can never speak after the timeout/OFF.
                try { engineRef?.shutdown() } catch (_: Exception) { }
                return@create
            }
            if (status == TextToSpeech.SUCCESS) {
                val engine = engineRef
                val lang = engine?.setLanguage(Locale.ENGLISH) ?: TextToSpeech.LANG_MISSING_DATA
                if (lang != TextToSpeech.LANG_MISSING_DATA && lang != TextToSpeech.LANG_NOT_SUPPORTED && engine != null) {
                    val attrs = AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                    engine.setAudioAttributes(attrs)
                    // Pause other media for the announcement; resume them when it ends. If focus is not
                    // granted the alert still plays (an official warning must not be silenced for it).
                    val focus = requestFocus(am, attrs)
                    val doneSpeak = java.util.concurrent.atomic.AtomicBoolean(false)
                    fun finishSpeak(ok: Boolean, err: String?) {
                        if (doneSpeak.compareAndSet(false, true)) {
                            abandonFocus(am, focus)
                            try { engine.shutdown() } catch (_: Exception) { }
                            finish(ok, err)
                        }
                    }
                    engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) {}
                        override fun onDone(utteranceId: String?) { finishSpeak(true, null) }
                        @Deprecated("Deprecated in Java")
                        override fun onError(utteranceId: String?) { finishSpeak(false, "TTS engine error") }
                    })
                    // Watchdog: focus is never held open even if the engine drops the utterance callbacks.
                    // Stale-timer guard: an already-finished utterance ignores it (see doneSpeak in finishSpeak).
                    Handler(Looper.getMainLooper()).postDelayed({ if (!done.get()) finishSpeak(false, "no engine callback within 60 s") }, 60_000L)
                    val params = Bundle()
                    params.putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, AudioManager.STREAM_ALARM)
                    val accepted = engine.speak(text, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
                    if (accepted != TextToSpeech.SUCCESS) finishSpeak(false, "TTS engine rejected the utterance")
                    return@create
                }
            }
            // No usable TTS voice: fall back to the alarm ringtone for a few seconds, and say so truthfully.
            try { engineRef?.shutdown() } catch (_: Exception) { }
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
