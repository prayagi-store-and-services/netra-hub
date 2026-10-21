package com.example.data.sensor

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Driving mode (H3). The phone counts as driving while the [SpeedGate] is open, which means GPS speed has been
 * 15 km/h or more (and a stop shorter than 90 seconds does not end it). No new sensor, no network. Speed-limit warnings are not
 * available: there is no on-device source for road speed limits, so the UI says "Unavailable".
 */
object DrivingState {
    private val _driving = MutableStateFlow(false)
    val driving: StateFlow<Boolean> = _driving.asStateFlow()

    /** Called with the speed gate's state after each speed reading. */
    fun set(isDriving: Boolean) { _driving.value = isDriving }

    fun reset() { _driving.value = false; _speed.value = SpeedInfo() }

    /** Latest GPS speed seen (null speed = no speed from GPS) and when it was seen. Shown on the driving status card. */
    data class SpeedInfo(val kmh: Float? = null, val speedAt: Long = 0L, val lastReadingAt: Long = 0L)
    private val _speed = MutableStateFlow(SpeedInfo())
    val speed: StateFlow<SpeedInfo> = _speed.asStateFlow()

    fun updateSpeed(kmh: Float?, now: Long) {
        val cur = _speed.value
        _speed.value = if (kmh != null) SpeedInfo(kmh, now, now) else cur.copy(lastReadingAt = now)
    }
}
