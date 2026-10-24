package com.example.data.sensor

import kotlinx.coroutines.flow.first
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.NetraForegroundService
import com.example.DrivingJournalTest
import com.example.data.model.RawSensorReading
import com.example.data.model.SensorCategory
import com.example.driving.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Real SensorManager GPS entrypoint, actual gate/events/trip wiring and actual service teardown/heartbeat.
 * GPS and clocks are deterministic inputs; no GPS permission, real network or sleeps are needed. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class DrivingPipelineIntegrationTest {
    private lateinit var context: Context
    private lateinit var manager: SensorManager
    private var mono = 100_000L
    private var wall = 1_000_000L
    private val spoken = mutableListOf<String>()
    private val store = DrivingJournalTest.MemoryVisibleFailureStore()
    private val factory = DrivingEvents.journalStoreFactory
    private val speaker = DrivingEvents.speaker

    @Before fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        DrivingEvents.resetTransitionMemoryForTests()
        DrivingEvents.journalStoreFactory = { store }
        DrivingEvents.setAnnounceEnabled(context, true)
        DrivingEvents.temporaryAnnouncementHold = false // these tests cover the normal speech path
        DrivingEvents.speaker = { _, _, id, cb -> spoken.add(id); cb?.invoke(true, null); null }
        DrivingTrip.stop()
        manager = SensorManager(context, monotonicNow = { mono }, wallNow = { wall })
        context.getSharedPreferences("netra_sensor_diagnostics", Context.MODE_PRIVATE).edit().clear().commit()
    }
    @After fun tearDown() {
        manager.stopMonitoring(); DrivingTrip.stop()
        DrivingEvents.temporaryAnnouncementHold = true
        DrivingEvents.resetTransitionMemoryForTests()
        DrivingEvents.journalStoreFactory = factory; DrivingEvents.speaker = speaker
    }
    private fun reading(speed: Float?, fix: Long = mono, source: String = "live", accuracy: Float = 5f): RawSensorReading =
        RawSensorReading("gnss_location", "GPS", SensorCategory.LOCATION, floatArrayOf(0f, 0f, speed ?: 0f, accuracy),
            "Lat/Lng", wall, extraDetails = mapOf("speedKmH" to if (speed == null) "Unavailable" else "$speed km/h",
                "fixElapsedMs" to fix.toString(), "fixTimeMs" to (wall - (mono - fix)).toString(), "source" to source))
    private fun feed(speed: Float?, advance: Long = 5_000L, fix: Long? = null, source: String = "live") {
        mono += advance; wall += advance
        manager.acceptLocationReading(reading(speed, fix ?: mono, source))
    }
    private fun open() { feed(20f, 0); feed(20f); feed(20f) }

    @Test fun injectedPowerEngineDrivesOneRestartAndRepeatedProfileDoesNotRestart() = kotlinx.coroutines.runBlocking {
        val power = com.example.data.engine.PowerManagerEngine()
        val owned = SensorManager(context, powerManagerEngine = power)
        try {
            // Wait for the real initial StateFlow collection before measuring transitions.
            kotlinx.coroutines.withTimeout(5_000L) {
                owned.sensorThrottles.first { it[android.hardware.Sensor.TYPE_ACCELEROMETER] == 150L }
            }
            owned.startMonitoring()
            val before = owned.monitoringStartCountForTests()
            power.updateMode(com.example.data.engine.PowerMode.IDLE)
            kotlinx.coroutines.withTimeout(5_000L) {
                owned.sensorThrottles.first { it[android.hardware.Sensor.TYPE_ACCELEROMETER] == 750L }
            }
            // Taking the owner monitor waits for the collector's whole restart, not just its map assignment.
            assertEquals(before + 1, owned.monitoringStartCountForTests())
            power.updateMode(com.example.data.engine.PowerMode.IDLE)
            owned.updateThrottlingBasedOnPowerMode(com.example.data.engine.PowerMode.IDLE)
            assertEquals(before + 1, owned.monitoringStartCountForTests())
            assertEquals(5_000L, owned.sensorThrottles.value[android.hardware.Sensor.TYPE_LIGHT])
        } finally { owned.stopMonitoring() }
    }

    @Test fun cachedAndOutOfOrderInterleavingsDoNotCancelFreshConfirmation() {
        feed(20f, 0); val first = mono
        feed(20f)
        feed(20f, 1_000L, first, "poller") // cached duplicate/out-of-order, not new speed evidence
        feed(null, 1_000L, source = "poller")
        feed(20f, 3_000L)
        assertTrue(manager.drivingDiagnosticState().open)
        assertEquals(1, spoken.size); assertEquals(1, store.disk.size)
    }
    @Test fun stationaryNoiseAndInvalidAccuracyCannotOpen() {
        repeat(30) { feed(if (it % 3 == 0) 40f else 0f) }
        assertFalse(manager.drivingDiagnosticState().open); assertTrue(spoken.isEmpty())
        repeat(5) { mono += 5000; manager.acceptLocationReading(reading(40f, accuracy = 500f)) }
        assertFalse(manager.drivingDiagnosticState().open)
    }
    @Test fun serviceDestroyAndRecreatedHeartbeatResumeTripWithoutNewEdge() {
        open(); assertTrue(DrivingTrip.isRecording())
        val first = Robolectric.buildService(NetraForegroundService::class.java).get()
        first.reconcileDrivingMonitoring(manager)
        first.onDestroy()
        assertTrue(DrivingTrip.isRecording()) // service is not the repository-owned trip lifetime
        val recreated = Robolectric.buildService(NetraForegroundService::class.java).get()
        recreated.reconcileDrivingMonitoring(manager)
        assertTrue(DrivingTrip.isRecording())
        manager.stopMonitoring(); manager.startMonitoring()
        assertTrue(DrivingTrip.isRecording()); assertEquals(1, spoken.size)
        assertEquals(1, store.disk.count { it.started })
        recreated.onDestroy()
    }
    @Test fun realServiceCreateStartDestroyReusesRepositoryMonitoringOwner() {
        val first = Robolectric.buildService(NetraForegroundService::class.java).create().startCommand(0, 1)
        val owner = first.get().monitoringOwnerForTests()
        assertNotNull(owner)
        // Drive the actual repository manager with Robolectric's monotonic clock, not the helper manager.
        synchronized(owner!!) {
            repeat(3) {
                if (it > 0) org.robolectric.shadows.ShadowSystemClock.advanceBy(java.time.Duration.ofSeconds(5))
                val fix = android.os.SystemClock.elapsedRealtime()
                owner.acceptLocationReading(reading(20f, fix).copy(extraDetails = reading(20f, fix).extraDetails +
                    ("fixTimeMs" to System.currentTimeMillis().toString())))
            }
            assertTrue(owner.drivingDiagnosticState().open)
            assertTrue(DrivingTrip.isRecording())
            assertEquals(1, spoken.size)
        }
        first.destroy()
        assertTrue(owner.drivingDiagnosticState().open)
        assertTrue(DrivingTrip.isRecording())
        val second = Robolectric.buildService(NetraForegroundService::class.java).create().startCommand(0, 2)
        try {
            assertSame(owner, second.get().monitoringOwnerForTests())
            assertTrue(owner!!.isMonitoringActive.value)
            assertTrue(owner.drivingDiagnosticState().open)
            assertTrue(DrivingTrip.isRecording())
            assertEquals(1, spoken.size)
            assertEquals(1, store.disk.count { it.started })
        } finally { second.destroy(); owner?.stopMonitoring() }
    }
    @Test fun staleObserverGenerationCannotApplyAfterRestart() = synchronized(manager) {
        manager.startMonitoring()
        val oldReceiver = manager.monitoringReceiver()
        manager.stopMonitoring()
        manager.startMonitoring()
        try {
            val currentReceiver = manager.monitoringReceiver()
            // Explicitly deliver an old registration after restart. No scheduling race or sleep needed.
            repeat(3) {
                mono += 5000; wall += 5000
                oldReceiver(reading(20f))
            }
            assertFalse(manager.drivingDiagnosticState().open)
            assertFalse(DrivingTrip.isRecording())
            assertTrue(spoken.isEmpty())
            assertTrue(store.disk.isEmpty())
            repeat(3) {
                mono += 5000; wall += 5000
                currentReceiver(reading(20f))
            }
            assertTrue(manager.drivingDiagnosticState().open)
            assertTrue(DrivingTrip.isRecording())
            assertEquals(1, spoken.size)
            manager.stopMonitoring()
            repeat(3) { mono += 5000; wall += 5000; currentReceiver(reading(0f)) }
            assertEquals(1, store.disk.size)
        } finally { manager.stopMonitoring() }
    }
    @Test fun sampleAndAcceptedEventShareExplicitCorrelationId() {
        open()
        val fact = context.getSharedPreferences("netra_sensor_diagnostics", Context.MODE_PRIVATE)
            .getString("recent_samples", "")!!.lineSequence().first()
        val event = store.disk.single()
        assertTrue(fact.contains("eventId=" + event.id))
        assertTrue(fact.contains("requestId=driving_" + event.id))
        val sampleId = fact.substringAfter("sampleId=").substringBefore(" ")
        assertTrue(event.diagnostics.contains("triggerSampleId=" + sampleId))
    }
    @Test fun concurrentStopAndGpsProcessingDoNotDuplicateTransitions() {
        val start = java.util.concurrent.CountDownLatch(1)
        val errors = java.util.concurrent.ConcurrentLinkedQueue<Throwable>()
        val callbacks = Thread {
            try { start.await(); repeat(30) { feed(20f, 1_000L) } }
            catch (t: Throwable) { errors.add(t) }
        }
        val stop = Thread {
            try { start.await(); repeat(30) { manager.stopMonitoring() } }
            catch (t: Throwable) { errors.add(t) }
        }
        callbacks.start(); stop.start(); start.countDown()
        callbacks.join(); stop.join()
        assertTrue(errors.toString(), errors.isEmpty())
        assertEquals(1, spoken.size); assertEquals(1, store.disk.size)
    }
    @Test fun upstreamRejectedSpeedMetadataSurvivesBoundedLocalFact() {
        for (source in listOf("live", "poller")) {
            val rejected = reading(null, source = source).copy(extraDetails =
                reading(null, source = source).extraDetails + SpeedTrust.diagnosticFacts(20f, 5f, true, 9f))
            manager.acceptLocationReading(rejected)
            val fact = context.getSharedPreferences("netra_sensor_diagnostics", Context.MODE_PRIVATE)
                .getString("recent_samples", "")!!.lineSequence().first()
            assertTrue(fact.contains("originalSpeed=20.0"))
            assertTrue(fact.contains("speedAccuracy=9.0"))
            assertTrue(fact.contains("upstreamRejection=invalid_speed_accuracy"))
            assertFalse(fact.contains("latitude")); assertFalse(fact.contains("longitude"))
        }
        assertFalse(manager.drivingDiagnosticState().open)
        assertTrue(store.disk.isEmpty()); assertTrue(spoken.isEmpty())
    }
    @Test fun perSampleFactsAreBoundedAndContainDecisionOwnershipAndResult() {
        open()
        repeat(55) { feed(20f, 1000L, mono - 20_000L, "poller") }
        val rows = context.getSharedPreferences("netra_sensor_diagnostics", Context.MODE_PRIVATE)
            .getString("recent_samples", "")!!.lines()
        assertEquals(50, rows.size)
        val fact = rows.first()
        for (field in listOf("fixAt=", "fixMono=", "processedAt=", "processedMono=", "speed=", "accuracy=", "ageMs=",
            "decision=", "previous=", "result=", "confirmation=", "monitor=", "monitorStarts=", "service=", "serviceStarts="))
            assertTrue("missing $field", fact.contains(field))
        assertFalse(fact.contains("latitude")); assertFalse(fact.contains("longitude"))
    }
}
