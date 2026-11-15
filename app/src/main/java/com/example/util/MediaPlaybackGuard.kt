package com.example.util

import android.content.Context
import android.media.AudioManager
import android.os.Build

/**
 * Standing rule (Deepak, 15 Nov 2026): when any media (video or audio) is playing, every TTS
 * announcement is SKIPPED - the app must never interrupt, pause, duck or request audio focus
 * over the user's media.
 *
 * Detection combines AudioManager.isMusicActive with (API 26+) any active playback
 * configuration, and FAILS CLOSED: when playback state cannot be determined the answer is
 * "media is playing", so the announcement is skipped rather than risk talking over it.
 *
 * Honest limits (no API can see these without a new permission, which is not allowed):
 * silent or muted video, cast/remote playback and OEM-hidden playback sessions can be
 * invisible to both signals.
 */
object MediaPlaybackGuard {

    /** True when media is playing or playback state cannot be determined (fail closed). */
    fun isMediaPlaying(context: Context): Boolean {
        return try {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return true
            if (am.isMusicActive) return true
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                am.activePlaybackConfigurations?.any { it.isActive } == true
        } catch (_: Exception) {
            true
        }
    }
}
