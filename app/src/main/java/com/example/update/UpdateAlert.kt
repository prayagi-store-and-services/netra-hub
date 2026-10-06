package com.example.update

import android.Manifest
import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Update alert: every 6 hours (network required) asks the repo's latest release whether a newer version exists and
 * posts one notification per new version. Tapping it opens the app, downloads the verified build and starts the install.
 */
object UpdateAlert {
    const val EXTRA_AUTO_UPDATE = "netra_auto_update"
    private const val CHANNEL_ID = "netra_app_update"
    private const val NOTIFICATION_ID = 7431
    private const val WORK_NAME = "netra_update_alert"
    private const val PREFS = "netra_update_alert"

    fun start(activity: Activity) {
        schedule(activity.applicationContext)
        handle(activity, activity.intent)
    }

    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<UpdateAlertWorker>(6, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    /** Runs when the notification was tapped: download, verify, hand to the installer. */
    fun handle(activity: Activity, intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_AUTO_UPDATE, false) != true) return
        intent.removeExtra(EXTRA_AUTO_UPDATE)
        val app = activity.applicationContext
        Toast.makeText(app, "Downloading the update...", Toast.LENGTH_SHORT).show()
        Thread {
            try {
                val result = AppUpdater.checkNow(app)
                if (result is AppUpdater.CheckResult.Newer) {
                    val file = AppUpdater.download(app, result.release)
                    activity.runOnUiThread {
                        try { AppUpdater.install(app, file) } catch (e: Exception) {
                            Toast.makeText(app, e.message ?: "Update failed.", Toast.LENGTH_LONG).show()
                        }
                    }
                } else {
                    activity.runOnUiThread { Toast.makeText(app, "No newer version found.", Toast.LENGTH_LONG).show() }
                }
            } catch (e: Exception) {
                activity.runOnUiThread { Toast.makeText(app, e.message ?: "Update failed.", Toast.LENGTH_LONG).show() }
            }
        }.start()
    }

    internal fun notifyIfNew(c: Context, release: AppRelease, appName: String) {
        val prefs = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (release.versionCode <= prefs.getLong("notified_code", 0L)) return
        if (Build.VERSION.SDK_INT >= 33 &&
            c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val nm = c.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL_ID, "App updates", NotificationManager.IMPORTANCE_DEFAULT))
        }
        val launch = c.packageManager.getLaunchIntentForPackage(c.packageName) ?: return
        launch.putExtra(EXTRA_AUTO_UPDATE, true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val open = PendingIntent.getActivity(c, 0, launch, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = NotificationCompat.Builder(c, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("Update available")
            .setContentText("A new $appName (" + release.versionName + ") is out. Tap to update.")
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        nm.notify(NOTIFICATION_ID, n)
        prefs.edit().putLong("notified_code", release.versionCode).apply()
    }
}

class UpdateAlertWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val r = AppUpdater.checkNow(applicationContext)
            if (r is AppUpdater.CheckResult.Newer) UpdateAlert.notifyIfNew(applicationContext, r.release, AppUpdater.APP_LABEL)
        } catch (_: Exception) {
            // Try again at the next run.
        }
        Result.success()
    }
}
