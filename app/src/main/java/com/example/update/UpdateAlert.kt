package com.example.update

import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.work.CoroutineWorker
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.io.File

/** Migration only: never schedules, checks, downloads or installs an APK. */
object CentralUpdates {
    fun retireLegacy(context: Context) {
        try { WorkManager.getInstance(context).cancelUniqueWork("netra_update_alert").result.get(5, java.util.concurrent.TimeUnit.SECONDS) } catch (_: Exception) { }
        try { (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).apply {
            try { cancel(7431) } catch (_: Exception) { }
            try { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) deleteNotificationChannel("netra_app_update") } catch (_: Exception) { }
        } } catch (_: Exception) { }
        try { context.getSharedPreferences("netra_app_update",0).edit().clear().apply() } catch (_: Exception) { }
        try { context.getSharedPreferences("netra_update_alert",0).edit().clear().apply() } catch (_: Exception) { }
        try { File(context.cacheDir,"updates").deleteRecursively() } catch (_: Exception) { }
    }
}
/** Old persisted WorkManager requests resolve to a harmless terminal worker. */
class UpdateAlertWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context,params) {
    override suspend fun doWork(): Result = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { CentralUpdates.retireLegacy(applicationContext); Result.success() }
}
