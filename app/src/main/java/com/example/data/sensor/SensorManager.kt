package com.example.data.sensor

import android.content.Context
import android.hardware.Sensor
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.example.data.model.RawSensorReading
import com.example.data.model.SensorCapabilityInfo
import com.example.data.model.SensorCategory
import com.example.data.model.SensorFusionState
import com.example.data.engine.PowerMode
import com.example.data.engine.HealthCenterEngine
import com.example.data.event.SensorEvent
import com.example.data.event.SensorEventBus
import com.example.data.event.EventPriority
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import com.example.data.service.BatteryManager
import com.example.data.repository.SettingsRepository
import com.example.util.LoggingManager

/**
 * SensorManager handles lifecycle-aware initialization, active monitoring, and pause/resume management
 * for Android hardware sensors while establishing the capability discovery process via [CapabilityManager].
 */
class SensorManager(
    private val context: Context,
    private val capabilityManager: CapabilityManager = CapabilityManager(context),
    private val sensorObservers: SensorObservers = SensorObservers(context),
    val fusionEngine: SensorFusionEngine = SensorFusionEngine(context),
    val healthCenterEngine: HealthCenterEngine = HealthCenterEngine(context),
    val batteryManager: BatteryManager = BatteryManager(context),
    private val powerManagerEngine: com.example.data.engine.PowerManagerEngine = com.example.data.engine.PowerManagerEngine(),
    private val settingsRepository: SettingsRepository = SettingsRepository(context),
    val stateManager: SensorStateManager = SensorStateManager()
) : DefaultLifecycleObserver {

    private var thermalState: ThermalState = ThermalState.NORMAL

    enum class ThermalState { NORMAL, WARM, HIGH, THERMAL_PROTECTION }

    private fun updateThermalState(tempC: Float) {
        val newState = when {
            tempC >= 45f -> ThermalState.THERMAL_PROTECTION
            tempC >= 42f -> ThermalState.HIGH
            tempC >= 38f -> ThermalState.WARM
            else -> ThermalState.NORMAL
        }
        
        if (newState != thermalState) {
            thermalState = newState
            if (_isMonitoringActive.value) {
                // Throttle adjustments on thermal state change
                stopMonitoring()
                startMonitoring()
            }
        }
    }

    private val _liveReadings = MutableStateFlow<Map<String, RawSensorReading>>(emptyMap())
    val liveReadings: StateFlow<Map<String, RawSensorReading>> = _liveReadings.asStateFlow()

    private var job = Job()
    private var scope = CoroutineScope(Dispatchers.Default + job)

    private val _capabilities = MutableStateFlow<List<SensorCapabilityInfo>>(emptyList())
    val capabilities: StateFlow<List<SensorCapabilityInfo>> = _capabilities.asStateFlow()

    private val _fusionState = MutableStateFlow(SensorFusionState())
    val fusionState: StateFlow<SensorFusionState> = _fusionState.asStateFlow()

    private val _isMonitoringActive = MutableStateFlow(false)
    val isMonitoringActive: StateFlow<Boolean> = _isMonitoringActive.asStateFlow()

    private val _sensorThrottles = MutableStateFlow<Map<Int, Long>>(emptyMap())
    val sensorThrottles: StateFlow<Map<Int, Long>> = _sensorThrottles.asStateFlow()

    private val _monitoringEnabled = MutableStateFlow<Map<Int, Boolean>>(
        mapOf(
            Sensor.TYPE_ACCELEROMETER to true,
            Sensor.TYPE_GYROSCOPE to true,
            Sensor.TYPE_LIGHT to true,
            Sensor.TYPE_MAGNETIC_FIELD to true,
            Sensor.TYPE_PROXIMITY to true
        )
    )

    private val subscribers = MutableStateFlow<Map<Int, Set<String>>>(emptyMap())
    private val sensorJobs = java.util.concurrent.ConcurrentHashMap<Int, Job>()
    
    fun addSubscriber(sensorType: Int, subscriberId: String) {
        val current = subscribers.value.toMutableMap()
        val set = current.getOrDefault(sensorType, emptySet()).toMutableSet()
        set.add(subscriberId)
        current[sensorType] = set
        subscribers.value = current
        evaluateSensorStreams()
    }
    
    fun removeSubscriber(sensorType: Int, subscriberId: String) {
        val current = subscribers.value.toMutableMap()
        val set = current.getOrDefault(sensorType, emptySet()).toMutableSet()
        set.remove(subscriberId)
        if (set.isEmpty()) current.remove(sensorType) else current[sensorType] = set
        subscribers.value = current
        evaluateSensorStreams()
    }

    private val sequenceNumbers = java.util.concurrent.ConcurrentHashMap<String, Int>()

    init {
        com.example.util.LoggingManager.init(context)
        // Establish capability discovery process upon manager instantiation
        runCapabilityDiscovery()
        
        // Observe power mode changes
        scope.launch {
            powerManagerEngine.powerMode.collect { mode ->
                updateThrottlingBasedOnPowerMode(mode)
            }
        }


        scope.launch { settingsRepository.monitorMagnetic.collect { updateMonitoringState(Sensor.TYPE_MAGNETIC_FIELD, it) } }
        scope.launch { settingsRepository.monitorLight.collect { updateMonitoringState(Sensor.TYPE_LIGHT, it) } }
        scope.launch { settingsRepository.monitorProximity.collect { updateMonitoringState(Sensor.TYPE_PROXIMITY, it) } }
    }

    private fun updateMonitoringState(type: Int, enabled: Boolean) {
        val current = _monitoringEnabled.value.toMutableMap()
        current[type] = enabled
        _monitoringEnabled.value = current
        
        // Restart monitoring to apply changes if active
        if (_isMonitoringActive.value) {
            stopMonitoring()
            startMonitoring()
        }
    }

    private fun updateThrottlingBasedOnPowerMode(mode: PowerMode) {
        val multiplier = when (mode) {
            PowerMode.ACTIVE -> 1L
            PowerMode.PASSIVE -> 2L
            PowerMode.IDLE -> 5L
            PowerMode.ADAPTIVE_QUIET -> 10L
        }
        
        // Define base intervals
        val baseIntervals = mapOf(
            Sensor.TYPE_ACCELEROMETER to 150L,
            Sensor.TYPE_GYROSCOPE to 200L,
            Sensor.TYPE_LIGHT to 1000L,
            Sensor.TYPE_MAGNETIC_FIELD to 500L
        )
        
        baseIntervals.forEach { (type, baseInterval) ->
            setSensorThrottle(type, baseInterval * multiplier)
        }
    }

    fun setSensorThrottle(sensorType: Int, intervalMs: Long) {
        val current = _sensorThrottles.value.toMutableMap()
        current[sensorType] = intervalMs
        _sensorThrottles.value = current
        if (_isMonitoringActive.value) {
            stopMonitoring()
            startMonitoring()
        }
    }

    fun setThermalThreshold(threshold: Int) {
        fusionEngine.thermalThresholdC = threshold
    }

    /**
     * Triggers capability discovery process via CapabilityManager.
     */
    fun runCapabilityDiscovery(): List<SensorCapabilityInfo> {
        val discoveredList = capabilityManager.discoverCapabilities()
        _capabilities.value = discoveredList
        com.example.data.engine.NetraWatchdogEngine.notifyUpdate("Sensor Status")
        return discoveredList
    }

    /**
     * Starts active lifecycle-aware sensor monitoring streams for all supported hardware capabilities.
     */
    @Synchronized
    fun startMonitoring() {
        if (_isMonitoringActive.value) return
        if (job.isCancelled || !scope.coroutineContext[Job]!!.isActive) {
            job = Job()
            scope = CoroutineScope(Dispatchers.Default + job)
        }
        _isMonitoringActive.value = true

        // Reconcile after a transient restart: the gate never closed, so driving continued, but the trip may
        // have been stopped by the old teardown path. Resume it with an honest log note - no fake new edge.
        if (speedGate.isOpen && !com.example.driving.DrivingTrip.isRecording()) {
            com.example.driving.DrivingTrip.start(context, null, System.currentTimeMillis())
            com.example.driving.DrivingEvents.noteTripResumed(context)
        }

        evaluateSensorStreams()

        // 2. Battery & Power Stream
        batteryManager.startMonitoring()
        scope.launch {
            batteryManager.batteryState.collect { _ ->
                val reading = batteryManager.getAsRawReading()
                handleIncomingReading(reading)
            }
        }
        
        // 3. Thermal Subsystem Stream
        scope.launch {
            sensorObservers.observeThermalState().collect { reading ->
                handleIncomingReading(reading)
            }
        }
        
        // 4. GNSS / Location Stream
        scope.launch {
            sensorObservers.observeLocation().collect { reading ->
                handleIncomingReading(reading)
            }
        }
    }

    fun stopMonitoring() {
        _isMonitoringActive.value = false
        // A monitoring restart (thermal, settings) is transient: if the gate is open the trip keeps recording
        // and startMonitoring reconciles. Only a closed gate means "not driving", so only then stop the trip.
        // The service's onDestroy owns the final teardown when the whole service goes away.
        if (!speedGate.isOpen) com.example.driving.DrivingTrip.stop()
        batteryManager.stopMonitoring()
        sensorJobs.values.forEach { it.cancel() }
        sensorJobs.clear()
        job.cancel()
    }

    private fun evaluateSensorStreams() {
        if (!_isMonitoringActive.value) {
            sensorJobs.values.forEach { it.cancel() }
            sensorJobs.clear()
            return
        }

        val supportedCapabilities = capabilityManager.getSupportedCapabilities()
        supportedCapabilities.forEach { cap ->
            val isEnabledBySettings = _monitoringEnabled.value[cap.type] == true
            val hasSubscribers = subscribers.value[cap.type]?.isNotEmpty() == true

            val isCoreSafety = cap.type == Sensor.TYPE_ACCELEROMETER || cap.type == Sensor.TYPE_STEP_DETECTOR || cap.type == Sensor.TYPE_STEP_COUNTER
            // Background listening only while moving at 30 km/h or more (user setting, on by default).
            // A sensor screen the user has open (a subscriber) still gets live data.
            val gateAllows = !SpeedGate.isEnabled(context) || speedGate.isOpen
            val shouldRun = hasSubscribers || (isEnabledBySettings && gateAllows)
            
            if (shouldRun && !sensorJobs.containsKey(cap.type)) {
                val baseThrottle = _sensorThrottles.value[cap.type] ?: 150L
                val thermalMultiplier = when (thermalState) {
                    ThermalState.NORMAL -> 1L
                    ThermalState.WARM -> 2L
                    ThermalState.HIGH -> 5L
                    ThermalState.THERMAL_PROTECTION -> 20L
                }
                val throttle = baseThrottle * thermalMultiplier
                
                val unit = when (cap.type) {
                    Sensor.TYPE_ACCELEROMETER -> "m/s²"
                    Sensor.TYPE_GYROSCOPE -> "rad/s"
                    Sensor.TYPE_STEP_COUNTER -> "steps"
                    Sensor.TYPE_STEP_DETECTOR -> "pulse"
                    Sensor.TYPE_LIGHT -> "Lux"
                    Sensor.TYPE_MAGNETIC_FIELD -> "µT"
                    Sensor.TYPE_PROXIMITY -> "cm"
                    Sensor.TYPE_PRESSURE -> "hPa"
                    Sensor.TYPE_AMBIENT_TEMPERATURE -> "°C"
                    Sensor.TYPE_RELATIVE_HUMIDITY -> "%"
                    else -> "unknown"
                }
                
                val j = scope.launch {
                    sensorObservers.observeHardwareSensor(cap.type, cap.name, cap.category, unit, throttle).collect { reading ->
                        handleIncomingReading(reading)
                    }
                }
                sensorJobs[cap.type] = j
            } else if (!shouldRun && sensorJobs.containsKey(cap.type)) {
                sensorJobs[cap.type]?.cancel()
                sensorJobs.remove(cap.type)
            }
        }
    }

    private val readingBuffer = java.util.concurrent.ConcurrentLinkedQueue<RawSensorReading>()
    private var lastAggregationTime = 0L
    private val AGGREGATION_INTERVAL = 5000L // 5 seconds

    private val speedGate = SpeedGate(openDelayMs = SpeedGate.openDelayMs(context))
    private val stopTracker = com.example.sos.StopTracker()
    @Volatile private var lastFedFixMonoMs: Long = -1L
    /** Bounded diagnostics: last few speeds fed to the gate ("?" = unknown). No coordinates, capped at 10. */
    private val recentGateSamples = java.util.ArrayDeque<String>(10)
    /** Per-reason reject counters, so a rejected fix leaves a durable fact, not only a transient string. */
    private val rejectCounts = java.util.concurrent.ConcurrentHashMap<String, Int>()

    /** Updates the speed gate from a GNSS reading and re-evaluates the sensor streams when it opens or closes. */
    private fun updateSpeedGate(reading: RawSensorReading) {
        if (reading.sensorId != "gnss_location") return
        val monoNow = android.os.SystemClock.elapsedRealtime()
        val fixMono = reading.extraDetails["fixElapsedMs"]?.toLongOrNull()
        val accuracyM = reading.values.getOrElse(3) { 0f }
        // Validation order matters: reject first, and only a fully validated fix may advance the high-water mark.
        // Rejected readings still reach the gate as UNKNOWN, so the blind timer advances and cached/duplicate
        // fixes can never hold the gate open.
        val readingSource = reading.extraDetails["source"] ?: ""
        // The 10 s poller exists for the UI; a speed-less poll is not evidence and must not open, confirm or
        // restart anything. But the blind clock is clock-driven, not callback-driven: even when live updates
        // stop completely, these ticks keep advancing it so a stuck-ON gate still closes after the blind limit.
        if (readingSource == "poller" && reading.extraDetails["speedKmH"]?.endsWith("km/h") != true) {
            val closedByBlind = speedGate.onBlindTick(monoNow)
            if (closedByBlind) {
                val nowTick = System.currentTimeMillis()
                val lastKmh = DrivingState.speed.value.kmh
                DrivingState.set(false)
                DrivingState.updateSpeed(null, nowTick)
                synchronized(recentGateSamples) {
                    if (recentGateSamples.size >= 10) recentGateSamples.removeFirst()
                    recentGateSamples.addLast("[${monoNow / 1000}s OPEN->closed] poll:blind-timeout")
                }
                com.example.driving.DrivingEvents.onGateChange(context, false, lastKmh, nowTick, blindClose = true,
                    recentSamples = synchronized(recentGateSamples) { recentGateSamples.toList() })
                com.example.sos.ParkingSpot.onDrivingEnded(context, stopTracker, monoNow)
                evaluateSensorStreams()
            }
            return
        }
        var rejectReason: String? = null
        if (fixMono == null) rejectReason = "nofixtime"
        // Same monotonic clock on both sides; >1 s in the future means a broken/relabeled fix (batching skew is sub-second).
        else if (fixMono > monoNow + 1_000L) rejectReason = "futurefix"
        else if (fixMono <= lastFedFixMonoMs) rejectReason = if (fixMono == lastFedFixMonoMs) "dupfix" else "outoforder"
        else if (monoNow - fixMono > 30_000L) rejectReason = "stalefix"
        val rawSpeed: Float? = if (reading.extraDetails["speedKmH"]?.endsWith("km/h") == true && reading.values.size > 2) reading.values[2] else null
        val cleaned = SpeedTrust.cleanSpeed(rawSpeed)
        if (rejectReason == null && rawSpeed != null && cleaned == null) rejectReason = "badspeed"
        val speedKmH: Float? = if (rejectReason != null) null else cleaned
        if (rejectReason == null && fixMono != null) lastFedFixMonoMs = fixMono
        val stateTag = "[" + (monoNow / 1000) + "s " + (if (speedGate.isOpen) "OPEN" else "closed") + "] "
        if (rejectReason != null) rejectCounts.merge(rejectReason, 1, Int::plus)
        synchronized(recentGateSamples) {
            if (recentGateSamples.size >= 10) recentGateSamples.removeFirst()
            recentGateSamples.addLast(
                stateTag + when {
                    rejectReason != null -> "rej:" + rejectReason + "#" + (rejectCounts[rejectReason] ?: 0)
                    speedKmH != null -> "%.0f".format(speedKmH) + "@" + (if (accuracyM > 0f) "%.0fm".format(accuracyM) else "?m") + "/" + readingSource
                    else -> "?blind/" + readingSource
                }
            )
        }
        // The gate runs on the monotonic clock, so wall-clock jumps cannot open or close it early.
        val changed = speedGate.update(speedKmH, monoNow)
        val now = System.currentTimeMillis()
        val fixTime = reading.extraDetails["fixTimeMs"]?.toLongOrNull()
        if (changed && speedGate.isOpen) stopTracker.reset()
        stopTracker.onFix(reading.extraDetails["latitude"]?.toDoubleOrNull() ?: 0.0, reading.extraDetails["longitude"]?.toDoubleOrNull() ?: 0.0,
            reading.values.getOrElse(3) { 0f }, fixMono ?: monoNow, now - (monoNow - (fixMono ?: monoNow)), speedKmH)
        // Log first, then publish the state, so a crash between the two cannot lose the entry.
        if (changed) com.example.driving.DrivingEvents.onGateChange(context, speedGate.isOpen, speedKmH ?: DrivingState.speed.value.kmh, now, blindClose = !speedGate.isOpen && speedGate.closedByBlindSpell,
            recentSamples = synchronized(recentGateSamples) { recentGateSamples.toList() })
        DrivingState.set(speedGate.isOpen)
        DrivingState.updateSpeed(speedKmH, now)
        if (speedGate.isOpen) com.example.driving.DrivingTrip.onSpeed(speedKmH, reading.values.getOrElse(0) { 0f }, reading.values.getOrElse(1) { 0f }, fixTime ?: now)
        if (speedKmH == null && now - DrivingState.speed.value.speedAt > 60_000L) com.example.driving.DrivingEvents.noteBlind(context, "GPS gave no usable speed (no fix, old fix, or the phone sent a position without speed)", now)
        if (changed && !speedGate.isOpen) com.example.sos.ParkingSpot.onDrivingEnded(context, stopTracker, monoNow)
        if (changed) evaluateSensorStreams()
    }

    private fun handleIncomingReading(reading: RawSensorReading) {
        updateSpeedGate(reading)
        val seq = (sequenceNumbers[reading.sensorId] ?: 0) + 1
        sequenceNumbers[reading.sensorId] = seq

        val enrichedReading = reading.copy(
            lastUpdateTimestamp = System.currentTimeMillis(),
            sequenceNumber = seq
        )

        val currentReadings = _liveReadings.value.toMutableMap()
        currentReadings[reading.sensorId] = enrichedReading
        _liveReadings.value = currentReadings

        val updatedFusion = fusionEngine.updateReading(enrichedReading)
        val watchdogStates = com.example.data.engine.NetraWatchdogEngine.moduleStates.value
        val refreshing = watchdogStates.filterValues { it.status == "Refreshing" || it.isRefreshing }.keys.toList()

        val finalFusion = updatedFusion.copy(
            isAnyModuleRefreshing = refreshing.isNotEmpty(),
            refreshingModules = refreshing,
            lastUpdateTimestamp = System.currentTimeMillis()
        )
        _fusionState.value = finalFusion
        
        // Update Single Source of Truth
        stateManager.updateSnapshot { current ->
            current.copy(
                batteryPercent = finalFusion.batteryLevelPercent,
                batteryTempC = finalFusion.batteryTempC,
                ambientTempC = finalFusion.ambientTemperatureC,
                magneticFieldUT = finalFusion.magneticMagnitudeuT,
                ambientLightLux = finalFusion.ambientLightLux,
                isCharging = finalFusion.isCharging,
                timestamp = System.currentTimeMillis()
            )
        }
        updateThermalState(finalFusion.batteryTempC)

        // Buffering for health center update
        readingBuffer.add(enrichedReading)
        val now = System.currentTimeMillis()
        if (now - lastAggregationTime > AGGREGATION_INTERVAL) {
            val batch = com.example.util.drainAll(readingBuffer)
            lastAggregationTime = now
            healthCenterEngine.update(finalFusion, batch.associateBy { it.sensorId })
        }

        // Watchdog notification mapping
        when (reading.sensorId) {
            "battery_telemetry" -> {
                SensorEventBus.post(SensorEvent(EventPriority.INFO, "Battery"))
                SensorEventBus.post(SensorEvent(EventPriority.INFO, "Charging"))
                SensorEventBus.post(SensorEvent(EventPriority.INFO, "Temperature"))
            }
            "sensor_2" -> { // TYPE_MAGNETIC_FIELD
                SensorEventBus.post(SensorEvent(EventPriority.INFO, "Magnetic Field"))
            }
            "sensor_13", "sensor_7" -> { // TYPE_AMBIENT_TEMPERATURE / thermal
                SensorEventBus.post(SensorEvent(EventPriority.INFO, "Temperature"))
            }
            "location_telemetry" -> {
                SensorEventBus.post(SensorEvent(EventPriority.CRITICAL, "Driving"))
            }
            "thermal_telemetry" -> {
                SensorEventBus.post(SensorEvent(EventPriority.CRITICAL, "Temperature"))
            }
        }
    }

    // --- LifecycleAware Observer Implementation ---

    override fun onStart(owner: LifecycleOwner) {
        super.onStart(owner)
        startMonitoring()
    }

    override fun onResume(owner: LifecycleOwner) {
        super.onResume(owner)
        if (!_isMonitoringActive.value) {
            startMonitoring()
        }
    }

    override fun onPause(owner: LifecycleOwner) {
        super.onPause(owner)
        // Optionally pause non-essential sensor monitoring if required
    }

    override fun onStop(owner: LifecycleOwner) {
        super.onStop(owner)
        stopMonitoring()
    }

    override fun onDestroy(owner: LifecycleOwner) {
        super.onDestroy(owner)
        stopMonitoring()
    }
}
