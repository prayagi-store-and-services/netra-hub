package com.example

import com.example.sos.ImpactLogic
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImpactLogicTest {
    @Test fun hardHitWhileDrivingCounts() = assertTrue(ImpactLogic.isImpact(0f, 0f, 9.81f * 4.5f, true))
    @Test fun sameHitWhileNotDrivingIgnored() = assertFalse(ImpactLogic.isImpact(0f, 0f, 9.81f * 4.5f, false))
    @Test fun normalBumpWhileDrivingIgnored() = assertFalse(ImpactLogic.isImpact(0f, 0f, 9.81f * 2.0f, true))
    @Test fun exactlyAtThresholdCounts() = assertTrue(ImpactLogic.isImpact(9.81f * 4.0f, 0f, 0f, true))
}
