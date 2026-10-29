package com.example.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.data.model.SafetyRiskLevel

class NetraNotificationManager(private val context: Context) {

    companion object {
        const val CHANNEL_ID_EMERGENCY = "netra_emergency_channel"
        const val NOTIFICATION_ID_EMERGENCY = 1001
        const val NOTIFICATION_ID_TEST = 1002

        /** Clearly labelled test wording: a test is never titled like a real alert. */
        const val TEST_TITLE = "NETRA TEST: emergency alert channel"
        const val TEST_TEXT = "This is only a test notification. There is no emergency. Real alerts say NETRA ALERT."
    }

    init {
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager: NotificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            // Emergency Channel
            val nameEmergency = "Netra Emergency & Critical Alerts"
            val descriptionTextEmergency = "High-priority alerts that bypass Do Not Disturb for critical sensor risks"
            val importanceEmergency = NotificationManager.IMPORTANCE_HIGH
            val channelEmergency = NotificationChannel(CHANNEL_ID_EMERGENCY, nameEmergency, importanceEmergency).apply {
                description = descriptionTextEmergency
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 250, 500, 250, 1000)
                setBypassDnd(true)
                setShowBadge(true)
            }
            notificationManager.createNotificationChannel(channelEmergency)
        }
    }

    /**
     * Posts the clearly labelled TEST notification through the same high-priority emergency
     * channel a real alert uses, so the user can verify sound, vibration and DND behaviour
     * without ever seeing fake emergency wording. Returns false when notification permission
     * is missing so the UI can say so honestly.
     */
    fun sendTestNotification(): Boolean {
        val builder = NotificationCompat.Builder(context, CHANNEL_ID_EMERGENCY)
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setContentTitle(TEST_TITLE)
            .setContentText(TEST_TEXT)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setVibrate(longArrayOf(0, 500, 250, 500, 250, 1000))

        return try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_TEST, builder.build())
            true
        } catch (e: SecurityException) {
            false
        }
    }

    fun canPostEmergency(): Boolean {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (nm.getNotificationChannel(CHANNEL_ID_EMERGENCY)?.importance == NotificationManager.IMPORTANCE_NONE) return false
        }
        return true
    }

    fun sendEmergencyAlert(title: String, message: String, riskLevel: SafetyRiskLevel): Boolean {
        if (!canPostEmergency()) return false
        val builder = NotificationCompat.Builder(context, CHANNEL_ID_EMERGENCY)
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setContentTitle("NETRA ALERT: $title")
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setVibrate(longArrayOf(0, 500, 250, 500, 250, 1000))

        try {
            NotificationManagerCompat.from(context).notify(title.hashCode() and 0x7fffffff, builder.build())
            return true
        } catch (e: SecurityException) {
            return false
        }
    }
}
