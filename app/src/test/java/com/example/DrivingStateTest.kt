package com.example

import com.example.data.sensor.DrivingState
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DrivingStateTest {
    @After fun tearDown() = DrivingState.reset()
    @Test fun startsNotDriving() = assertFalse(DrivingState.driving.value)
    @Test fun followsTheGate() {
        DrivingState.set(true); assertTrue(DrivingState.driving.value)
        DrivingState.set(false); assertFalse(DrivingState.driving.value)
    }
    @Test fun resetClears() { DrivingState.set(true); DrivingState.reset(); assertFalse(DrivingState.driving.value) }
}
