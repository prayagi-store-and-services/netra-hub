package com.example.sos

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.telephony.SmsManager
import androidx.core.content.ContextCompat
import com.example.data.service.OfficialLocationContextManager
import com.example.share.LocationShare
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Runs only while the user has switched SOS shake ON (a visible notification stays up). Three hard shakes start a
 * 10 second countdown with a Cancel button. If it is not cancelled, one SMS per saved contact is sent with the
 * current location link, or "Location Unavailable". Nothing runs when the switch is OFF.
 */
class SosService : Service(), SensorEventListener {
    private val handler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val detector = SosLogic.ShakeDetector()
    private var sm: SensorManager? = null
    private var counting = false
    private var left = 0
    private val tick = object : Runnable {
        override fun run() {
            if (!counting) return
            left -= 1
            if (left <= 0) { counting = false; sendNow(); show(idle(), ONGOING) } else { show(countdown(left), ONGOING); handler.postDelayed(this, 1000) }
        }
    }

    override fun onBind(i: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> { stopSelf(); return START_NOT_STICKY }
            ACTION_CANCEL -> { counting = false; handler.removeCallbacks(tick); detector.reset(); show(idle(), ONGOING); return START_STICKY }
        }
        ensureChannel()
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                val type = if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0
                startForeground(ONGOING, idle(), type)
            } else startForeground(ONGOING, idle())
        } catch (e: Exception) {
            SosStore.setEnabled(this, false); stopSelf(); return START_NOT_STICKY
        }
        if (sm == null) {
            sm = getSystemService(Context.SENSOR_SERVICE) as? SensorManager
            val acc = sm?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
            if (acc == null) { SosStore.setEnabled(this, false); stopSelf(); return START_NOT_STICKY }
            sm?.registerListener(this, acc, SensorManager.SENSOR_DELAY_GAME)
        }
        return START_STICKY
    }

    override fun onSensorChanged(e: SensorEvent) {
        if (counting) return
        if (!SosLogic.canTrigger(System.currentTimeMillis(), SosStore.lastTrigger(this))) return
        if (detector.onSample(e.values[0], e.values[1], e.values[2], System.currentTimeMillis())) {
            if (SosStore.contacts(this).isEmpty()) return
            counting = true; left = SosLogic.COUNTDOWN_S
            show(countdown(left), ONGOING); handler.postDelayed(tick, 1000)
        }
    }
    override fun onAccuracyChanged(s: Sensor?, a: Int) {}

    private fun sendNow() {
        SosStore.setLastTrigger(this, System.currentTimeMillis())
        scope.launch {
            val info = OfficialLocationContextManager(this@SosService).refreshLocation()
            val cal = java.util.Calendar.getInstance()
            val age = SosLogic.ageYears(SosStore.dob(this@SosService), cal.get(java.util.Calendar.YEAR), cal.get(java.util.Calendar.MONTH) + 1, cal.get(java.util.Calendar.DAY_OF_MONTH))
            val batt = registerReceiver(null, android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val lvl = batt?.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scl = batt?.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, -1) ?: -1
            val pct = if (lvl >= 0 && scl > 0) lvl * 100 / scl else null
            val body = SosLogic.smsBody(LocationShare.message(info.latitude, info.longitude, info.accuracy, info.timestamp), SosLogic.profileLine(SosStore.name(this@SosService), age, SosStore.blood(this@SosService)), pct)
            val hasSms = ContextCompat.checkSelfPermission(this@SosService, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED
            var ok = 0
            if (hasSms) {
                val mgr = if (Build.VERSION.SDK_INT >= 31) getSystemService(SmsManager::class.java) else @Suppress("DEPRECATION") SmsManager.getDefault()
                SosStore.contacts(this@SosService).forEach { n ->
                    try { mgr.sendMultipartTextMessage(n, null, mgr.divideMessage(body), null, null); ok++ } catch (e: Exception) { }
                }
            }
            val msg = if (!hasSms) "SOS not sent: SMS permission is not allowed" else "SOS SMS handed to the phone for $ok of ${SosStore.contacts(this@SosService).size} contacts. Delivery is not confirmed."
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).notify(RESULT, base(msg, "").build())
        }
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= 26) (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
            .createNotificationChannel(NotificationChannel(CH, "SOS shake", NotificationManager.IMPORTANCE_HIGH))
    }
    private fun base(title: String, text: String): Notification.Builder {
        val b = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, CH) else @Suppress("DEPRECATION") Notification.Builder(this)
        return b.setSmallIcon(android.R.drawable.ic_dialog_alert).setContentTitle(title).setContentText(text)
    }
    private fun pi(action: String) = PendingIntent.getService(this, action.hashCode(), Intent(this, SosService::class.java).setAction(action), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    private fun idle() = base("SOS shake is ON", "Shake the phone hard 3 times to send an SOS").setOngoing(true)
        .addAction(Notification.Action.Builder(null as android.graphics.drawable.Icon?, "Turn off", pi(ACTION_STOP)).build()).build()
    private fun countdown(s: Int) = base("SOS in $s seconds", "Sending your location by SMS. Tap Cancel to stop.").setOngoing(true)
        .addAction(Notification.Action.Builder(null as android.graphics.drawable.Icon?, "CANCEL", pi(ACTION_CANCEL)).build()).build()
    private fun show(n: Notification, id: Int) = (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).notify(id, n)

    override fun onDestroy() {
        sm?.unregisterListener(this); handler.removeCallbacksAndMessages(null); scope.cancel()
        SosStore.setEnabled(this, false)
        super.onDestroy()
    }

    companion object {
        const val CH = "sos_shake"; const val ONGOING = 7301; const val RESULT = 7302
        const val ACTION_STOP = "com.example.sos.STOP"; const val ACTION_CANCEL = "com.example.sos.CANCEL"
        fun start(c: Context) = ContextCompat.startForegroundService(c, Intent(c, SosService::class.java))
        fun stop(c: Context) { c.stopService(Intent(c, SosService::class.java)) }
    }
}
