package com.example.sos

import kotlin.math.sqrt

/** Pure logic for the optional crash alert while driving. Phone sensors cannot tell a real crash from a hard drop, so this is best effort and the user always gets a countdown with a Cancel button. */
object ImpactLogic {
    const val IMPACT_G = 4.0f      // strength of one hard impact, in g
    const val COUNTDOWN_S = 15     // seconds of siren before any SMS goes out

    /** True only while driving and for one sample at or above [IMPACT_G]. */
    fun isImpact(x: Float, y: Float, z: Float, driving: Boolean): Boolean {
        if (!driving) return false
        return sqrt(x * x + y * y + z * z) / 9.81f >= IMPACT_G
    }
}
