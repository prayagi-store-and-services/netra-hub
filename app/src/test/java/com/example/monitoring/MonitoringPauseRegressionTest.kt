package com.example.monitoring

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.example.BootReceiver
import com.example.NetraForegroundService
import com.example.data.sensor.SensorManager
import com.example.data.service.BatteryManager
import com.example.driving.DrivingSignal
import com.example.driving.DrivingTrip
import com.example.data.sensor.DrivingState
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class MonitoringPauseRegressionTest {
    private lateinit var context: Context
    private var manager: SensorManager? = null
    @Before fun setup() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences(MonitoringPreference.PREFS, Context.MODE_PRIVATE).edit().clear().commit()
        DrivingTrip.stop()
    }
    @After fun cleanup() {
        manager?.stopMonitoring()
        DrivingTrip.stop()
        context.getSharedPreferences(MonitoringPreference.PREFS, Context.MODE_PRIVATE).edit().clear().commit()
    }
    @Test fun existingInstallDefaultsEnabledAndRecreatedContextKeepsPause() {
        assertTrue(MonitoringPreference.isEnabled(context))
        assertTrue(MonitoringPreference.setByUser(context, false))
        val other = context.createConfigurationContext(context.resources.configuration)
        assertFalse(MonitoringPreference.isEnabled(other))
        assertTrue(MonitoringPreference.setByUser(other, true))
        assertTrue(MonitoringPreference.isEnabled(context))
    }
    @Test fun pausedManagerRejectsLifecycleAndRecoveryStarts() {
        assertTrue(MonitoringPreference.setByUser(context, false))
        val owned = SensorManager(context).also { manager = it }
        repeat(3) { owned.startMonitoring() } // all lifecycle/recovery callers converge here
        assertFalse(owned.isMonitoringActive.value)
        assertFalse(DrivingState.driving.value)
        assertFalse(DrivingTrip.isRecording())
        assertFalse(DrivingSignal.isDriving(context))
    }
    @Test fun explicitPauseStopsActiveManagerAndLateReceiverCannotRevive() {
        val owned = SensorManager(context).also { manager = it }
        owned.startMonitoring()
        val oldReceiver = owned.monitoringReceiver()
        assertTrue(owned.isMonitoringActive.value)
        DrivingSignal.publish(context, true)
        DrivingState.updateSpeed(60f, System.currentTimeMillis())
        assertTrue(MonitoringPreference.setByUser(context, false))
        owned.stopByUser()
        oldReceiver(com.example.data.model.RawSensorReading(
            "gnss_location", "GPS", com.example.data.model.SensorCategory.LOCATION,
            floatArrayOf(0f, 0f, 60f, 5f), "Lat/Lng", System.currentTimeMillis()))
        owned.startMonitoring()
        assertFalse(owned.isMonitoringActive.value)
        assertFalse(owned.drivingDiagnosticState().open)
        assertNull(DrivingState.speed.value.kmh)
        assertFalse(DrivingSignal.isDriving(context))
    }
    @Test fun bootCannotStartServiceAfterPause() {
        assertTrue(MonitoringPreference.setByUser(context, false))
        BootReceiver().onReceive(context, Intent(Intent.ACTION_BOOT_COMPLETED))
        assertNull(shadowOf(context as android.app.Application).nextStartedService)
        assertFalse(MonitoringPreference.isEnabled(context))
    }
    @Test fun queuedAndStickyServiceCommandsAreNotStickyWhilePaused() {
        assertTrue(MonitoringPreference.setByUser(context, false))
        val controller = Robolectric.buildService(NetraForegroundService::class.java).create()
        try {
            val service = controller.get()
            assertNull(service.monitoringOwnerForTests())
            assertEquals(android.app.Service.START_NOT_STICKY, service.onStartCommand(null, 0, 1))
            assertNull(service.monitoringOwnerForTests())
            assertEquals(android.app.Service.START_NOT_STICKY,
                service.onStartCommand(Intent(context, NetraForegroundService::class.java), 0, 2))
        } finally { controller.destroy() }
    }
    @Test fun batteryRecoveryCannotRegisterReceiverWhilePaused() {
        assertTrue(MonitoringPreference.setByUser(context, false))
        val battery = BatteryManager(context)
        val before = shadowOf(context as android.app.Application).registeredReceivers.size
        battery.startMonitoring()
        assertEquals(before, shadowOf(context as android.app.Application).registeredReceivers.size)
    }
    @Test fun queuedHeartbeatPublicationCannotOverridePausedSignal() {
        DrivingSignal.publish(context, true)
        assertTrue(MonitoringPreference.setByUser(context, false))
        DrivingSignal.publish(context, false)
        assertFalse(MonitoringPreference.publishIfEnabled(context) { DrivingSignal.publish(context, true) })
        assertFalse(DrivingSignal.isDriving(context))
    }
    @Test fun pauseTeardownFailureKeepsDurableFalseAndDoesNotThrow() {
        val rejecting = object : android.content.ContextWrapper(context) {
            override fun stopService(service: Intent): Boolean = throw SecurityException("test rejection")
        }
        assertFalse(MonitoringControl.pause(rejecting))
        assertFalse(MonitoringPreference.isEnabled(context))
    }
    @Test fun synchronousResumeStartFailureRollsBackPreference() {
        assertTrue(MonitoringPreference.setByUser(context, false))
        val rejecting = object : android.content.ContextWrapper(context) {
            override fun startForegroundService(service: Intent): android.content.ComponentName? =
                throw SecurityException("test rejection")
        }
        assertFalse(MonitoringControl.resume(rejecting))
        assertFalse(MonitoringPreference.isEnabled(context))
    }
    @Test fun preferenceFlowEmitsPauseAndResumeWithoutPolling() = kotlinx.coroutines.test.runTest {
        val observed = mutableListOf<Boolean>()
        val collector = backgroundScope.launch(kotlinx.coroutines.test.UnconfinedTestDispatcher(testScheduler)) {
            MonitoringPreference.enabledFlow(context).collect { observed.add(it) }
        }
        testScheduler.runCurrent()
        assertEquals(listOf(true), observed)
        assertTrue(MonitoringPreference.setByUser(context, false))
        shadowOf(android.os.Looper.getMainLooper()).idle()
        testScheduler.runCurrent()
        assertEquals(listOf(true, false), observed)
        assertTrue(MonitoringPreference.setByUser(context, true))
        shadowOf(android.os.Looper.getMainLooper()).idle()
        testScheduler.runCurrent()
        assertEquals(listOf(true, false, true), observed)
        collector.cancel()
    }
    @Test fun rejectedOldHeartbeatCannotTearDownResumedManagerOrService() = kotlinx.coroutines.runBlocking {
        val controller = Robolectric.buildService(NetraForegroundService::class.java).create()
        try {
            val service = controller.get()
            var resumedManager: SensorManager? = null
            // Drive the ACTUAL production loop, pause just before publication, then resume
            // after rejection but before that old loop's caller continuation completes.
            val oldLoop = service.startHeartbeatLoop(
                beforePublication = {
                    assertTrue(MonitoringPreference.setByUser(context, false))
                    service.monitoringOwnerForTests()?.stopByUser()
                },
                afterRejected = {
                    assertTrue(MonitoringPreference.setByUser(context, true))
                    val owned = service.monitoringOwnerForTests()!!
                    resumedManager = owned
                    owned.startMonitoring()
                    DrivingSignal.publish(context, true)
                }
            )
            kotlinx.coroutines.withTimeout(10_000L) { oldLoop.join() }
            assertTrue(oldLoop.isCompleted)
            assertFalse(oldLoop.isCancelled)
            assertNotNull(resumedManager)
            assertTrue(MonitoringPreference.isEnabled(context))
            assertTrue(resumedManager!!.isMonitoringActive.value)
            assertTrue(DrivingSignal.isDriving(context))
            assertFalse(shadowOf(service).isStoppedBySelf)
            resumedManager?.stopByUser()
        } finally { controller.destroy() }
    }
    @Test fun explicitReenableAllowsMonitoringAgain() {
        assertTrue(MonitoringPreference.setByUser(context, false))
        val owned = SensorManager(context).also { manager = it }
        owned.startMonitoring()
        assertFalse(owned.isMonitoringActive.value)
        assertTrue(MonitoringPreference.setByUser(context, true))
        owned.startMonitoring()
        assertTrue(owned.isMonitoringActive.value)
    }
}
