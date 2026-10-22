package com.example.sos.alert

import org.junit.Assert.*
import org.junit.Test

class AlertCoordinatorTest {
    private fun payload() = AlertPayload("a1", 1L, "button", 1.0, 2.0, 10f, 1L, "https://maps/x", null, "Deepak", null, "O+", listOf("1", "2", "3"), 50, "1.4.28", "x")
    private val gateOff = Emergency112Gate()
    private val gateOn = Emergency112Gate(true, { true }, { true })

    @Test fun defaultsAreAllOff() { assertFalse(Emergency112Gate.EMERGENCY_112_LIVE); assertFalse(gateOff.isOn()) }
    @Test fun needsAllThreeGates() {
        assertFalse(Emergency112Gate(true, { true }, { false }).isOn())
        assertFalse(Emergency112Gate(true, { false }, { true }).isOn())
        assertFalse(Emergency112Gate(false, { true }, { true }).isOn())
        assertTrue(gateOn.isOn())
    }
    @Test fun offGateNeverSends112AndFamilyTextDoesNotClaimIt() {
        val fam = FakeSender(AlertChannel.FAMILY_SMS); val s112 = FakeSender(AlertChannel.ONE_TWELVE_SMS)
        val c = AlertCoordinator(listOf(fam, s112), gateOff); c.detect("t"); val r = c.confirm(payload())
        assertTrue(r[AlertChannel.ONE_TWELVE_SMS] is SendResult.Unavailable); assertTrue(s112.sent.isEmpty())
        assertEquals(1, fam.sent.size); assertFalse((fam.sent[0].fallbackNote ?: "").contains("112"))
    }
    @Test fun failing112DoesNotBlockFamily() {
        val fam = FakeSender(AlertChannel.FAMILY_SMS); val s112 = FakeSender(AlertChannel.ONE_TWELVE_SMS, SendResult.Failed("x"))
        val c = AlertCoordinator(listOf(fam, s112), gateOn); c.detect("t"); c.confirm(payload())
        assertEquals(1, fam.sent.size); assertFalse((fam.sent[0].fallbackNote ?: "").contains("112"))
    }
    @Test fun familyTextMentions112OnlyWhenReallySent() {
        val fam = FakeSender(AlertChannel.FAMILY_SMS); val s112 = FakeSender(AlertChannel.ONE_TWELVE_SMS)
        val c = AlertCoordinator(listOf(fam, s112), gateOn); c.detect("t"); c.confirm(payload())
        assertTrue(fam.sent[0].fallbackNote!!.contains("112 was also alerted"))
    }
    @Test fun erssStubIsUnavailable() {
        val c = AlertCoordinator(listOf(ErssApiStub(), FakeSender(AlertChannel.FAMILY_SMS)), gateOn); c.detect("t")
        assertTrue(c.confirm(payload())[AlertChannel.ERSS_API] is SendResult.Unavailable)
    }
    @Test fun cancelSendsNothingAndOneCombinedCooldown() {
        var now = 0L; val fam = FakeSender(AlertChannel.FAMILY_SMS)
        val c = AlertCoordinator(listOf(fam), gateOff, 600_000L) { now }
        assertTrue(c.detect("t")); c.cancel(); assertTrue(fam.sent.isEmpty())
        assertTrue(c.detect("t")); c.confirm(payload()); assertEquals(1, fam.sent.size)
        now = 1000; assertFalse(c.detect("t"))
        now = 700_000; assertTrue(c.detect("t"))
    }
    @Test fun bloodGroupOnlyWithConsent() {
        assertNull(AlertPayload.withConsent(payload(), false).bloodGroup); assertEquals("O+", AlertPayload.withConsent(payload(), true).bloodGroup)
    }
    @Test fun needsThreeToFiveContacts() { val ok = try { payload().copy(contacts = listOf("1")); false } catch (e: IllegalArgumentException) { true }; assertTrue(ok) }
}
