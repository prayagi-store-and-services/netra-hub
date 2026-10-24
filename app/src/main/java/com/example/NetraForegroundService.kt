package com.example

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.data.repository.NetraSafetyRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class NetraForegroundService : Service() {

    private val CHANNEL_ID = "NetraForegroundServiceChannel"
    private var repository: NetraSafetyRepository? = null
    private val serviceJob = Job()
    private var wasDriving = false
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)


    private var isStarted = false
    private var heartbeatJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        // Do not construct the sensor repository from a queued restart while paused.
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!com.example.monitoring.MonitoringPreference.isEnabled(this)) {
            stopSelf(startId)
            return START_NOT_STICKY
        }
        repository = NetraSafetyRepository.get(applicationContext)
        val notification = createNotification()
        val hasLocationPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED ||
            checkSelfPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                if (hasLocationPermission) {
                    startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
                } else {
                    startForeground(1, notification)
                }
            } catch (e: Exception) {
                try {
                    startForeground(1, notification)
                } catch (_: Exception) {}
            }
        } else {
            startForeground(1, notification)
        }

        if (!isStarted) {
            isStarted = true
            com.example.data.engine.EngineCoordinator.startAllEngines()
        }
        // A new start command owns a fresh heartbeat. A completed pre-pause loop cannot
        // strand a rapid resume behind the one-time engine-start flag.
        heartbeatJob?.cancel()
        heartbeatJob = startHeartbeatLoop()
        
        Log.d("NetraForegroundService", "NetraForegroundService started with START_STICKY heartbeat active.")
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    internal fun monitoringOwnerForTests() = repository?.sensorManager

    private val drivingServiceOwner = java.util.UUID.randomUUID().toString()
    /** The actual heartbeat entry point, shared by service execution and deterministic lifecycle tests. */
    internal fun reconcileDrivingMonitoring(sensorMgr: com.example.data.sensor.SensorManager) {
        if (!com.example.monitoring.MonitoringPreference.isEnabled(this)) {
            // Only the explicit pause owner may tear down this shared manager. A paused
            // observation from an old heartbeat must never stop a later resumed owner.
            return
        }
        sensorMgr.noteServiceOwner(drivingServiceOwner)
        sensorMgr.startMonitoring()
    }

    /** Publication has no teardown authority. The optional boundary is used by the
     * deterministic regression to resume exactly after a rejected old publication. */
    internal fun publishHeartbeatDriving(drivingNow: Boolean, afterRejected: () -> Unit = {}): Boolean {
        val published = com.example.monitoring.MonitoringPreference.publishIfEnabled(this) {
            com.example.driving.DrivingSignal.publish(applicationContext, drivingNow)
        }
        if (!published) afterRejected()
        return published
    }

    internal fun startHeartbeatLoop(
        beforePublication: () -> Unit = {},
        afterRejected: () -> Unit = {}
    ): Job =
        serviceScope.launch {
            while (isActive && com.example.monitoring.MonitoringPreference.isEnabled(this@NetraForegroundService)) {
                try {
                    // Perform heartbeat health check & sensor monitoring check
                    Log.d("NetraForegroundService", "Heartbeat tick: Background monitoring active, sensors and AI engine running.")
                    
                    if (repository == null) {
                        repository = NetraSafetyRepository.get(applicationContext)
                    }
                    
                    repository?.sensorManager?.let { sensorMgr ->
                        // Always route through startMonitoring: it reconciles the driving trip even when
                        // monitoring is already active (a service teardown can leave tripOFF under gateON),
                        // and early-returns internally when everything is running.
                        if (!sensorMgr.isMonitoringActive.value) {
                            Log.w("NetraForegroundService", "Heartbeat detected sensor manager inactive. Restoring monitoring...")
                        }
                        reconcileDrivingMonitoring(sensorMgr)
                    }

                    // Adaptive Power Intelligence Engine (NAPIE) context analysis
                    val powerManager = getSystemService(android.content.Context.POWER_SERVICE) as android.os.PowerManager
                    val isScreenOn = powerManager.isInteractive
                    val isCharging = repository?.batteryManager?.batteryState?.value?.isCharging ?: false
                    val isMotionDetected = repository?.sensorManager?.fusionState?.value?.isDrivingConfirmed ?: false
                    
                    val drivingNow = isMotionDetected || com.example.data.sensor.DrivingState.driving.value
                    // Serialize the last publish with the preference write. A pre-pause heartbeat
                    // cannot overwrite the explicit not-driving signal after pause returns.
                    beforePublication()
                    if (!publishHeartbeatDriving(drivingNow, afterRejected)) break
                    // Rejected/cancelled heartbeats exit without owning service or sensor teardown.
                    if (!isActive) break
                    wasDriving = drivingNow
                    val newMode = repository?.powerManagerEngine?.determineMode(isScreenOn, isCharging, isMotionDetected)
                    newMode?.let { repository?.powerManagerEngine?.updateMode(it) }

                } catch (e: Exception) {
                    Log.e("NetraForegroundService", "Error in heartbeat loop: ${e.message}", e)
                }
                delay(30_000L) // 30 second heartbeat interval
            }
        }

    override fun onDestroy() {
        super.onDestroy()
        serviceJob.cancel()
        // Repository is process-persistent and owns monitoring/trip lifetime independently. Destroying
        // this service cancels only its heartbeat; it must not stop a trip while repository streams/gate
        // remain active. Explicit monitoring/state shutdown belongs to the repository/manager owner.
        Log.d("NetraForegroundService", "NetraForegroundService destroyed. Attempting cleanup.")
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Netra Background Service",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Netra System Sentinel")
            .setContentText("Continuous AI Sensor Hub & Background Protection Active")
            .setSmallIcon(android.R.drawable.ic_menu_report_image)
            .setOngoing(true)
            .build()
    }
}
