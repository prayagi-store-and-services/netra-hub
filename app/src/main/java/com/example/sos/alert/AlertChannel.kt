package com.example.sos.alert

/** Where an alert can go. M1 skeleton: only fake channels exist; nothing here sends anything. */
enum class AlertChannel { FAMILY_SMS, ONE_TWELVE_SMS, ONE_TWELVE_EMAIL, ONE_TWELVE_DIALER, ERSS_API }

sealed class SendResult {
    object Sent : SendResult()
    data class Failed(val reason: String) : SendResult()
    /** Channel is switched off or not available (112 gated OFF, ERSS stub). Never counted as sent. */
    data class Unavailable(val reason: String) : SendResult()
}

interface ChannelSender {
    val channel: AlertChannel
    fun send(payload: AlertPayload): SendResult
}

/** Records what it was asked to send. For tests and the skeleton only. */
class FakeSender(override val channel: AlertChannel, private val result: SendResult = SendResult.Sent) : ChannelSender {
    val sent = mutableListOf<AlertPayload>()
    override fun send(payload: AlertPayload): SendResult { sent += payload; return result }
}

/** ERSS API is not available; always Unavailable. */
class ErssApiStub : ChannelSender {
    override val channel = AlertChannel.ERSS_API
    override fun send(payload: AlertPayload): SendResult = SendResult.Unavailable("ERSS API is not available")
}
