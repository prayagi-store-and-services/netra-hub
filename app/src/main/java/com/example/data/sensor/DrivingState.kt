package com.example.data.sensor

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Driving mode (H3). The phone counts as driving while the [SpeedGate] is open, which means GPS speed has been
 * 30 km/h or more (and a short stop does not end it). No new sensor, no network. Speed-limit warnings are not
 * available: there is no on-device source for road speed limits, so the UI says "Unavailable".
 */
object DrivingState {
    private val _driving = MutableStateFlow(false)
    val driving: StateFlow<Boolean> = _driving.asStateFlow()

    /** Called with the speed gate's state after each speed reading. */
    fun set(isDriving: Boolean) { _driving.value = isDriving }

    fun reset() { _driving.value = false }
}
