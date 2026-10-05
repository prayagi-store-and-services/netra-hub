package com.example.push

import android.content.Intent
import androidx.core.content.ContextCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/** Receives push alerts for the topics this phone subscribed to. Ignores everything when the user has not opted in. */
class HubFcmService : FirebaseMessagingService() {
    override fun onMessageReceived(message: RemoteMessage) {
        if (!PushAlerts.isEnabled(this)) return
        val alert = PushAlerts.parse(message.data, System.currentTimeMillis()) ?: return
        if (!PushAlerts.firstTime(this, alert.id)) return
        val i = Intent(this, AlertSpeechService::class.java)
            .putExtra(AlertSpeechService.EXTRA_TITLE, alert.title)
            .putExtra(AlertSpeechService.EXTRA_BODY, alert.body)
            .putExtra(AlertSpeechService.EXTRA_LANG, alert.lang)
            .putExtra(AlertSpeechService.EXTRA_EXPIRES, alert.expiresMillis)
        try { ContextCompat.startForegroundService(this, i) } catch (e: Exception) { /* Android refused a background start: nothing is shown, nothing is invented */ }
    }

    // The install token is not stored or sent anywhere by this app: delivery uses topics only.
    override fun onNewToken(token: String) {}
}
