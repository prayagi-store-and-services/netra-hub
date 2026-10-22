package com.example.sos.alert

/**
 * 112 is live only when ALL three hold: the build constant, a signed server flag, and the user's consent.
 * Every default is false. Nothing in M1 turns any of them on.
 */
class Emergency112Gate(
    private val buildLive: Boolean = EMERGENCY_112_LIVE,
    private val signedServerFlag: () -> Boolean = { false },
    private val userConsent: () -> Boolean = { false }
) {
    fun isOn(): Boolean = buildLive && signedServerFlag() && userConsent()

    companion object { const val EMERGENCY_112_LIVE = false }
}
