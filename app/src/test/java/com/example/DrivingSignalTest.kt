package com.example

import com.example.driving.DrivingSignal
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DrivingSignalTest {
    @Test fun freshDrivingFlagReadsTrue() = assertTrue(DrivingSignal.effective(true, 1_000L, 1_000L + 60_000L))
    @Test fun staleFlagReadsFalse() = assertFalse(DrivingSignal.effective(true, 1_000L, 1_000L + DrivingSignal.FRESH_MS + 1))
    @Test fun notDrivingReadsFalse() = assertFalse(DrivingSignal.effective(false, 1_000L, 2_000L))
    @Test fun neverSetReadsFalse() = assertFalse(DrivingSignal.effective(true, 0L, 2_000L))
    @Test fun clockGoingBackwardsReadsFalse() = assertFalse(DrivingSignal.effective(true, 5_000L, 1_000L))
}
