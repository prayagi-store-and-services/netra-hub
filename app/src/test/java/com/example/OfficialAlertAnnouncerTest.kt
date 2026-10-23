package com.example

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.os.Bundle
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.test.core.app.ApplicationProvider
import com.example.data.engine.OfficialAlertAnnouncer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale
import java.util.concurrent.TimeUnit

/** Deterministic fake-TTS tests: callback timing, late init, queue order, failures. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class OfficialAlertAnnouncerTest {

    class FakeTts : OfficialAlertAnnouncer.TtsEngine {
        var listener: UtteranceProgressListener? = null
        val spoken = mutableListOf<Pair<String, String>>()
        var speakResult = TextToSpeech.SUCCESS
        var shutdowns = 0
        override fun setLanguage(locale: Locale): Int = TextToSpeech.LANG_AVAILABLE
        override fun setAudioAttributes(attrs: AudioAttributes): Int = 0
        override fun speak(text: String, queueMode: Int, params: Bundle, utteranceId: String): Int {
            spoken.add(text to utteranceId); return speakResult
        }
        override fun setOnUtteranceProgressListener(l: UtteranceProgressListener) { listener = l }
        override fun shutdown() { shutdowns++ }
    }

    private lateinit var fake: FakeTts
    private var initCb: ((Int) -> Unit)? = null
    private var deliverInit = true
    private lateinit var ctx: Context

    @Before fun setUp() {
        fake = FakeTts()
        initCb = null
        deliverInit = true
        OfficialAlertAnnouncer.resetAnnouncerForTests()
        OfficialAlertAnnouncer.ttsInit = OfficialAlertAnnouncer.TtsInit { _, cb ->
            initCb = cb
            if (deliverInit) cb(TextToSpeech.SUCCESS)
            fake
        }
        ctx = ApplicationProvider.getApplicationContext()
        (ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager)
            .setStreamVolume(AudioManager.STREAM_ALARM, 5, 0)
    }

    private fun results(): MutableList<Pair<Boolean, String?>> = mutableListOf()

    @Test fun successfulUtteranceReportsDone() {
        val res = results()
        OfficialAlertAnnouncer.speakNotice(ctx, "hello", "t1") { ok, err -> res.add(ok to err) }
        org.robolectric.Shadows.shadowOf(Looper.getMainLooper()).idle()
        assertEquals(1, fake.spoken.size)
        fake.listener?.onDone("t1")
        org.robolectric.Shadows.shadowOf(Looper.getMainLooper()).idle()
        assertEquals(listOf(true to null), res)
        assertEquals(1, fake.shutdowns)
    }

    @Test fun hungInitTimesOutAndShutsDown() {
        deliverInit = false
        val res = results()
        OfficialAlertAnnouncer.speakNotice(ctx, "hello", "t2") { ok, err -> res.add(ok to err) }
        org.robolectric.Shadows.shadowOf(Looper.getMainLooper()).idle()
        assertTrue(res.isEmpty())
        org.robolectric.Shadows.shadowOf(Looper.getMainLooper()).idleFor(61, TimeUnit.SECONDS)
        assertEquals(1, res.size)
        assertEquals(false, res[0].first)
        assertTrue(res[0].second!!.contains("60 s"))
        assertEquals(1, fake.shutdowns) // the hung engine was killed
    }

    @Test fun lateInitAfterTimeoutNeverSpeaks() {
        deliverInit = false
        val res = results()
        OfficialAlertAnnouncer.speakNotice(ctx, "hello", "t3") { ok, err -> res.add(ok to err) }
        org.robolectric.Shadows.shadowOf(Looper.getMainLooper()).idle()
        org.robolectric.Shadows.shadowOf(Looper.getMainLooper()).idleFor(61, TimeUnit.SECONDS)
        assertEquals(1, res.size)
        initCb?.invoke(TextToSpeech.SUCCESS) // engine wakes up far too late
        org.robolectric.Shadows.shadowOf(Looper.getMainLooper()).idle()
        assertTrue(fake.spoken.isEmpty()) // a stale ON can never speak after the timeout
        assertEquals(2, fake.shutdowns)
    }

    @Test fun engineRejectingSpeakReportsFailure() {
        fake.speakResult = TextToSpeech.ERROR
        val res = results()
        OfficialAlertAnnouncer.speakNotice(ctx, "hello", "t4") { ok, err -> res.add(ok to err) }
        org.robolectric.Shadows.shadowOf(Looper.getMainLooper()).idle()
        assertEquals(1, res.size)
        assertEquals(false, res[0].first)
        assertTrue(res[0].second!!.contains("rejected"))
    }

    @Test fun queuedAnnouncementPlaysAfterCurrentInOrder() {
        val res1 = results(); val res2 = results()
        OfficialAlertAnnouncer.speakNotice(ctx, "mode on", "on1") { ok, err -> res1.add(ok to err) }
        org.robolectric.Shadows.shadowOf(Looper.getMainLooper()).idle()
        OfficialAlertAnnouncer.speakNotice(ctx, "mode off", "off1") { ok, err -> res2.add(ok to err) }
        org.robolectric.Shadows.shadowOf(Looper.getMainLooper()).idle()
        assertEquals(listOf("mode on"), fake.spoken.map { it.first }) // still only the first
        fake.listener?.onDone("on1")
        org.robolectric.Shadows.shadowOf(Looper.getMainLooper()).idle()
        assertEquals(listOf("mode on", "mode off"), fake.spoken.map { it.first }) // ON before OFF, always
        assertEquals(listOf(true to null), res1)
        fake.listener?.onDone("off1")
        org.robolectric.Shadows.shadowOf(Looper.getMainLooper()).idle()
        assertEquals(listOf(true to null), res2)
    }

    @Test fun everyGenuineEdgePlaysInArrivalOrder() {
        // ON active + OFF queued + ON2 arriving later: OFF is a genuine committed edge and must NOT be
        // dropped or superseded - all three play, strictly in arrival order.
        val res = results()
        OfficialAlertAnnouncer.speakNotice(ctx, "mode on", "on1") { ok, err -> res.add(ok to err) }
        org.robolectric.Shadows.shadowOf(Looper.getMainLooper()).idle()
        OfficialAlertAnnouncer.speakNotice(ctx, "mode off", "off1") { ok, err -> res.add(ok to err) }
        OfficialAlertAnnouncer.speakNotice(ctx, "mode on again", "on2") { ok, err -> res.add(ok to err) }
        org.robolectric.Shadows.shadowOf(Looper.getMainLooper()).idle()
        assertEquals(listOf("mode on"), fake.spoken.map { it.first })
        fake.listener?.onDone("on1"); org.robolectric.Shadows.shadowOf(Looper.getMainLooper()).idle()
        fake.listener?.onDone("off1"); org.robolectric.Shadows.shadowOf(Looper.getMainLooper()).idle()
        assertEquals(listOf("mode on", "mode off", "mode on again"), fake.spoken.map { it.first })
        fake.listener?.onDone("on2"); org.robolectric.Shadows.shadowOf(Looper.getMainLooper()).idle()
        assertEquals(3, res.size)
        assertTrue(res.all { it.first })
    }

    @Test fun arrivalDuringCompletionKeepsOrder() {
        // A new request landing while the previous completion drains must queue behind, never overtake.
        OfficialAlertAnnouncer.speakNotice(ctx, "first", "f1") { _, _ -> }
        org.robolectric.Shadows.shadowOf(Looper.getMainLooper()).idle()
        OfficialAlertAnnouncer.speakNotice(ctx, "second", "f2") { _, _ -> }
        OfficialAlertAnnouncer.speakNotice(ctx, "third", "f3") { _, _ -> }
        fake.listener?.onDone("f1")
        org.robolectric.Shadows.shadowOf(Looper.getMainLooper()).idle()
        assertEquals(listOf("first", "second"), fake.spoken.map { it.first })
        fake.listener?.onDone("f2")
        org.robolectric.Shadows.shadowOf(Looper.getMainLooper()).idle()
        assertEquals(listOf("first", "second", "third"), fake.spoken.map { it.first })
    }

    @Test fun staleWatchdogAfterSuccessDoesNotTouchTheEngine() {
        // After a successful utterance the old 60 s timers must be inert: a reused engine instance
        // (next utterance) must never be killed by the previous attempt's watchdog.
        OfficialAlertAnnouncer.speakNotice(ctx, "one", "w1") { _, _ -> }
        org.robolectric.Shadows.shadowOf(Looper.getMainLooper()).idle()
        fake.listener?.onDone("w1")
        org.robolectric.Shadows.shadowOf(Looper.getMainLooper()).idle()
        assertEquals(1, fake.shutdowns)
        org.robolectric.Shadows.shadowOf(Looper.getMainLooper()).idleFor(61, TimeUnit.SECONDS)
        assertEquals(1, fake.shutdowns) // no stale shutdown
    }

    @Test fun reentrantCallbackCannotOvertakeTheQueue() {
        // onComplete immediately requests a new announcement while a second one is already queued:
        // the reentrant request must land BEHIND the queued one.
        val order = mutableListOf<String>()
        OfficialAlertAnnouncer.speakNotice(ctx, "first", "r1") { _, _ ->
            OfficialAlertAnnouncer.speakNotice(ctx, "reentrant", "r3") { _, _ -> }
        }
        org.robolectric.Shadows.shadowOf(Looper.getMainLooper()).idle()
        OfficialAlertAnnouncer.speakNotice(ctx, "second", "r2") { _, _ -> }
        org.robolectric.Shadows.shadowOf(Looper.getMainLooper()).idle()
        fake.listener?.onDone("r1")
        org.robolectric.Shadows.shadowOf(Looper.getMainLooper()).idle()
        fake.listener?.onDone("r2")
        org.robolectric.Shadows.shadowOf(Looper.getMainLooper()).idle()
        fake.listener?.onDone("r3")
        org.robolectric.Shadows.shadowOf(Looper.getMainLooper()).idle()
        assertEquals(listOf("first", "second", "reentrant"), fake.spoken.map { it.first })
    }

    @Test fun officialAlertRegressionPathSpeaks() {
        // Shared-alert regression: the sachet/quake announce() path must still reach the engine.
        OfficialAlertAnnouncer.setEnabled(ctx, true)
        OfficialAlertAnnouncer.announce(ctx, "quake", "ev1", "M5.1 near Test City", "Test Area")
        org.robolectric.Shadows.shadowOf(Looper.getMainLooper()).idle()
        assertEquals(1, fake.spoken.size)
    }
}
