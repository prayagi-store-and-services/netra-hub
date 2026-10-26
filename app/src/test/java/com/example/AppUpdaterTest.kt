package com.example

import android.app.Application
import android.app.NotificationManager
import android.app.NotificationChannel
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.*
import androidx.work.testing.*
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import androidx.test.core.app.ApplicationProvider
import java.io.File
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(com.example.OwnedProcessTestRunner::class)
@Config(sdk=[24,34], application=Application::class)
class CentralizedUpdatesPolicyTest {
    @Test fun updaterCapabilityIsAbsent() {
        val c=ApplicationProvider.getApplicationContext<Application>()
        val info=c.packageManager.getPackageInfo(c.packageName,android.content.pm.PackageManager.GET_PERMISSIONS or android.content.pm.PackageManager.GET_PROVIDERS)
        assertFalse(info.requestedPermissions.orEmpty().contains("android.permission.REQUEST_INSTALL_PACKAGES"))
        assertFalse(info.providers.orEmpty().any { it.name.contains("UpdateFileProvider") })
        for(name in listOf("AppUpdater","UpdateFileProvider","PreDownload")) {
            try { Class.forName("com.example.update.$name"); fail("Removed capability still exists: $name") }
            catch (_: ClassNotFoundException) { }
        }
    }
    @Test fun legacyCacheAndPreferencesAreRetired() {
        val c=ApplicationProvider.getApplicationContext<Application>()
        val dir=File(c.cacheDir,"updates");dir.mkdirs();File(dir,"old.apk").writeText("legacy")
        for(n in listOf("netra_app_update","netra_update_alert")) c.getSharedPreferences(n,0).edit().putString("old","old").commit()
        WorkManagerTestInitHelper.initializeTestWorkManager(c, Configuration.Builder().setExecutor(androidx.work.testing.SynchronousExecutor()).build())
        val manager=WorkManager.getInstance(c)
        val request=PeriodicWorkRequestBuilder<com.example.update.UpdateAlertWorker>(6,TimeUnit.HOURS).setConstraints(Constraints.Builder().setRequiresCharging(true).build()).build()
        manager.enqueueUniquePeriodicWork("netra_update_alert",ExistingPeriodicWorkPolicy.KEEP,request).result.get()
        assertEquals(WorkInfo.State.ENQUEUED,manager.getWorkInfoById(request.id).get()!!.state)
        val notifications=c.getSystemService(NotificationManager::class.java)
        if(Build.VERSION.SDK_INT>=26) notifications.createNotificationChannel(NotificationChannel("netra_app_update","Legacy",NotificationManager.IMPORTANCE_DEFAULT))
        notifications.notify(7431,NotificationCompat.Builder(c,"netra_app_update").setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle("Old update").build())
        assertEquals(1,notifications.activeNotifications.size)
        com.example.update.CentralUpdates.retireLegacy(c)
        manager.getWorkInfoById(request.id).get().let { assertEquals(WorkInfo.State.CANCELLED,it!!.state) }
        assertTrue(notifications.activeNotifications.isEmpty())
        val worker=TestListenableWorkerBuilder<com.example.update.UpdateAlertWorker>(c).build()
        assertEquals(ListenableWorker.Result.success(),runBlocking { worker.doWork() })
        assertFalse(dir.exists())
        for(n in listOf("netra_app_update","netra_update_alert")) assertTrue(c.getSharedPreferences(n,0).all.isEmpty())
        if(Build.VERSION.SDK_INT>=26) assertNull(notifications.getNotificationChannel("netra_app_update"))
    }
}
