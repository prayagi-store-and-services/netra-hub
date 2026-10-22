package com.example.sos.alert

enum class AlertState { IDLE, DETECTED, COUNTDOWN, CONFIRMED, CANCELLED, DISPATCHED, CLOSED }

data class AuditEntry(val atMs: Long, val state: AlertState, val note: String)

/**
 * DETECTED -> COUNTDOWN -> CONFIRMED / CANCELLED -> DISPATCHED (per channel, in parallel) -> CLOSED.
 * Local audit log only. One combined cooldown across all channels.
 */
class AlertCoordinator(
    private val senders: List<ChannelSender>,
    private val gate: Emergency112Gate,
    private val cooldownMs: Long = 10 * 60_000L,
    private val clock: () -> Long = System::currentTimeMillis
) {
    var state = AlertState.IDLE; private set
    val audit = mutableListOf<AuditEntry>()
    val results = linkedMapOf<AlertChannel, SendResult>()
    private var lastDispatchMs = -1L

    private fun go(s: AlertState, note: String) { state = s; audit += AuditEntry(clock(), s, note) }
    private fun is112(c: AlertChannel) = c == AlertChannel.ONE_TWELVE_SMS || c == AlertChannel.ONE_TWELVE_EMAIL || c == AlertChannel.ONE_TWELVE_DIALER

    fun detect(trigger: String): Boolean {
        if (state != AlertState.IDLE && state != AlertState.CLOSED) return false
        if (lastDispatchMs >= 0 && clock() - lastDispatchMs < cooldownMs) { audit += AuditEntry(clock(), state, "ignored: cooldown"); return false }
        results.clear(); go(AlertState.DETECTED, trigger); go(AlertState.COUNTDOWN, "countdown started"); return true
    }

    fun cancel() { if (state == AlertState.COUNTDOWN) { go(AlertState.CANCELLED, "cancelled by user"); go(AlertState.CLOSED, "closed") } }

    /** Countdown ended without cancel. Sends to every ON channel; one channel failing never blocks another. */
    fun confirm(payload: AlertPayload): Map<AlertChannel, SendResult> {
        if (state != AlertState.COUNTDOWN) return results
        go(AlertState.CONFIRMED, "countdown ended")
        val on112 = gate.isOn()
        val sent112 = mutableListOf<AlertChannel>()
        // 112 first so the family text can say it was also alerted, but only if it was really sent.
        for (s in senders.filter { is112(it.channel) }) {
            val r = if (!on112) SendResult.Unavailable("112 is off") else runCatching { s.send(payload) }.getOrElse { SendResult.Failed(it.message ?: "error") }
            results[s.channel] = r; if (r is SendResult.Sent) sent112 += s.channel
            audit += AuditEntry(clock(), AlertState.DISPATCHED, "${s.channel}: $r")
        }
        for (s in senders.filter { !is112(it.channel) }) {
            val body = if (s.channel == AlertChannel.FAMILY_SMS && sent112.isNotEmpty()) payload.copy(fallbackNote = (payload.fallbackNote ?: "") + " 112 was also alerted.") else payload
            val r = runCatching { s.send(body) }.getOrElse { SendResult.Failed(it.message ?: "error") }
            results[s.channel] = r
            audit += AuditEntry(clock(), AlertState.DISPATCHED, "${s.channel}: $r")
        }
        lastDispatchMs = clock(); go(AlertState.CLOSED, "closed"); return results
    }
}
