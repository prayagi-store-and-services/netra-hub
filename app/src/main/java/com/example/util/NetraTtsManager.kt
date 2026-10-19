package com.example.util

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Calendar
import java.util.Locale

class NetraTtsManager(private val context: Context) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = TextToSpeech(context.applicationContext, this)
    private var isInitialized = false
    private var speakingFinishedCallback: (() -> Unit)? = null

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale.US)
            isInitialized = (result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED)
            
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {}
                
                override fun onDone(utteranceId: String?) {
                    utteranceId?.let { releaseFocusFor(it) }
                    speakingFinishedCallback?.invoke()
                }
                
                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    utteranceId?.let { releaseFocusFor(it) }
                }
            })
        }
    }

    fun setSpeakingFinishedCallback(callback: () -> Unit) {
        this.speakingFinishedCallback = callback
    }

    private fun isCallActive(): Boolean {
        return try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return false
            audioManager.mode == AudioManager.MODE_IN_CALL || audioManager.mode == AudioManager.MODE_IN_COMMUNICATION
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Checks whether active audio output (music, media playback, or phone call) is occurring.
     */
    fun isAudioInterruptionActive(): Boolean {
        return try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return false
            val isCallActive = audioManager.mode == AudioManager.MODE_IN_CALL || audioManager.mode == AudioManager.MODE_IN_COMMUNICATION
            val isMediaActive = audioManager.isMusicActive
            isCallActive || isMediaActive
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Checks if current time is within Night Mode (10:00 PM to 6:00 AM).
     */
    fun isNightModeActive(now: Long = System.currentTimeMillis()): Boolean {
        val cal = Calendar.getInstance().apply { timeInMillis = now }
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        return hour >= 22 || hour < 6
    }

    fun speakAlert(message: String, isCriticalSafety: Boolean = false) {
        if (!isInitialized) return

        // ABSOLUTE BAN: Security Hub / Netra must NEVER announce battery percentage,
        // charger connected/disconnected, or charging rates via voice.
        val lower = message.lowercase()
        if (lower.contains("battery percent") || lower.contains("charger connected") || 
            lower.contains("charger disconnected") || lower.contains("charging at") ||
            lower.contains("battery target") || lower.contains("charging milestone") ||
            lower.contains("battery level")) {
            return // Silently drop prohibited battery voice announcements
        }

        // Never speak into a phone call. Media is NOT suppressed here: the standing ecosystem rule
        // (19 Oct 2026) is that other media pauses via audio focus while we speak, then resumes.
        if (isCallActive()) {
            return
        }

        // In Night Mode, suppress non-critical announcements
        if (isNightModeActive() && !isCriticalSafety) {
            return
        }

        // Critical safety alerts interrupt whatever is being spoken. Everything else waits its turn,
        // so an informational alert can never cut a critical warning off mid-sentence.
        val id = "NetraAlert-" + System.nanoTime()
        val params = android.os.Bundle()
        params.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, id)
        val mode = if (isCriticalSafety) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
        val focus = requestSpeechFocus()
        pendingFocus[id] = focus
        if (focus == null) tts?.speak(message, mode, params, id) else {
            // Watchdog: focus is released even if the utterance callbacks never arrive.
            Handler(Looper.getMainLooper()).postDelayed({ releaseFocusFor(id) }, 60_000L)
            tts?.speak(message, mode, params, id)
        }
    }

    private val pendingFocus = java.util.concurrent.ConcurrentHashMap<String, Any?>()

    private fun requestSpeechFocus(): Any? {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return null
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT).build()
                if (am.requestAudioFocus(req) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) req else null
            } else {
                @Suppress("DEPRECATION")
                if (am.requestAudioFocus(null, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) Any() else null
            }
        } catch (_: Exception) { null }
    }

    private fun releaseFocusFor(id: String) {
        val handle = pendingFocus.remove(id) ?: return
        val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && handle is AudioFocusRequest) am.abandonAudioFocusRequest(handle)
            else if (handle != null) @Suppress("DEPRECATION") am.abandonAudioFocus(null)
        } catch (_: Exception) { }
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
    }
}
