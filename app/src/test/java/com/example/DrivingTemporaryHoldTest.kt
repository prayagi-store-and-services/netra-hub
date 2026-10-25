package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.driving.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Temporary update hold: driving on/off speech is muted; history, trip and the stored user switch are untouched. */
@RunWith(com.example.OwnedProcessTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class DrivingTemporaryHoldTest {
    private val spoken = mutableListOf<String>()
    private val store = DrivingJournalTest.MemoryVisibleFailureStore()
    private lateinit var context: Context
    private val factory = DrivingEvents.journalStoreFactory
    private val speaker = DrivingEvents.speaker

    @Before fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        DrivingEvents.resetTransitionMemoryForTests()
        DrivingEvents.journalStoreFactory = { store }
        DrivingEvents.setAnnounceEnabled(context, true)
        DrivingEvents.temporaryAnnouncementHold = true
        DrivingEvents.speaker = { _, text, _, cb -> spoken.add(text); cb?.invoke(true, null); null }
    }
    @After fun tearDown() {
        DrivingEvents.temporaryAnnouncementHold = true
        DrivingEvents.resetTransitionMemoryForTests()
        DrivingEvents.journalStoreFactory = factory
        DrivingEvents.speaker = speaker
        DrivingTrip.stop()
    }

    @Test fun holdSuppressesSpeechButKeepsHistoryTripAndReason() {
        DrivingEvents.onGateChange(context, true, 20f, 1000L)
        DrivingEvents.onGateChange(context, false, 0f, 2000L)
        assertTrue(spoken.isEmpty())
        assertEquals(listOf(true, false), store.disk.map { it.started })
        assertTrue(store.disk.all { it.audio == DrivingTransition.SKIPPED })
        assertTrue(store.disk.all { it.result == DrivingEvents.TEMPORARY_HOLD_RESULT })
    }
    @Test fun holdDoesNotOverwriteStoredUserSwitch() {
        DrivingEvents.onGateChange(context, true, 20f, 1000L)
        assertTrue(DrivingEvents.announceEnabled(context))
        DrivingEvents.setAnnounceEnabled(context, false)
        DrivingEvents.temporaryAnnouncementHold = true
        DrivingEvents.onGateChange(context, false, 0f, 2000L)
        assertFalse(DrivingEvents.announceEnabled(context))
        assertEquals("disabled by user; skipped", store.disk.last().result)
    }
    @Test fun releasingHoldDoesNotReplaySuppressedEventsAndSpeaksNewOnes() {
        DrivingEvents.onGateChange(context, true, 20f, 1000L)
        DrivingEvents.temporaryAnnouncementHold = false
        DrivingEvents.onGateChange(context, true, 21f, 1500L) // duplicate edge, nothing new
        assertTrue(spoken.isEmpty())
        DrivingEvents.resetTransitionMemoryForTests()
        DrivingEvents.acceptedHistory(context) // restart must not replay the skipped event
        assertTrue(spoken.isEmpty())
        DrivingEvents.onGateChange(context, false, 0f, 2000L)
        assertEquals(listOf(DrivingEvents.endedText()), spoken)
    }
}
