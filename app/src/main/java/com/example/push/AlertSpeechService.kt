package com.example.push

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.tts.TextToSpeech
import androidx.core.app.NotificationCompat
import java.util.Locale

/**
 * Speaks a push alert and repeats it every 20 seconds until the user taps Stop (on the notification) or the alert expires.
 * Normal speech speed. Works with no server involved after the alert has arrived.
 */
class AlertSpeechService : Service(), TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = null
    private var ready = false
    private var text = ""
    private var lang = ""
    private var expires = 0L
    private val handler = Handler(Looper.getMainLooper())
    private val loop = object : Runnable {
        override fun run() {
            if (expires != 0L && System.currentTimeMillis() > expires) { stopSelf(); return }
            if (ready) tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "alert")
            handler.postDelayed(this, REPEAT_MS)
        }
    }

    override fun onBind(i: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) { stopSelf(); return START_NOT_STICKY }
        val title = intent?.getStringExtra(EXTRA_TITLE).orEmpty()
        val body = intent?.getStringExtra(EXTRA_BODY).orEmpty()
        lang = intent?.getStringExtra(EXTRA_LANG).orEmpty()
        expires = intent?.getLongExtra(EXTRA_EXPIRES, 0L) ?: 0L
        text = "$title. $body"
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(NotificationChannel(CHANNEL, "Instant alerts", NotificationManager.IMPORTANCE_HIGH))
        val stop = PendingIntent.getService(this, 1, Intent(this, AlertSpeechService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val open = packageManager.getLaunchIntentForPackage(packageName)?.let { PendingIntent.getActivity(this, 2, it, PendingIntent.FLAG_IMMUTABLE) }
        val n: Notification = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setContentTitle(title).setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_MAX).setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC).setOngoing(true)
            .setContentIntent(open).addAction(0, "Stop", stop).build()
        if (Build.VERSION.SDK_INT >= 29) startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK) else startForeground(NOTIF_ID, n)
        handler.removeCallbacks(loop)
        if (tts == null) tts = TextToSpeech(applicationContext, this) else handler.post(loop)
        return START_NOT_STICKY
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val loc = if (lang.isNotEmpty()) Locale.forLanguageTag(lang) else Locale.getDefault()
            val r = tts?.setLanguage(loc)
            ready = r != TextToSpeech.LANG_MISSING_DATA && r != TextToSpeech.LANG_NOT_SUPPORTED
            tts?.setSpeechRate(1.0f)
        }
        handler.post(loop)
    }

    override fun onDestroy() {
        handler.removeCallbacks(loop)
        tts?.stop(); tts?.shutdown(); tts = null
        super.onDestroy()
    }

    companion object {
        const val EXTRA_TITLE = "title"
        const val EXTRA_BODY = "body"
        const val EXTRA_LANG = "lang"
        const val EXTRA_EXPIRES = "expires"
        const val ACTION_STOP = "com.example.push.STOP"
        private const val CHANNEL = "netra_instant_alert"
        private const val NOTIF_ID = 2001
        private const val REPEAT_MS = 20_000L
    }
}
