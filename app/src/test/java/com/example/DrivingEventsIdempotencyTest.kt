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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class DrivingEventsIdempotencyTest {
    private val spoken = mutableListOf<Pair<String, String>>()
    private val store = DrivingJournalTest.MemoryVisibleFailureStore()
    private lateinit var context: Context
    private val factory = DrivingEvents.journalStoreFactory
    private val speaker = DrivingEvents.speaker

    @Before fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        DrivingEvents.resetTransitionMemoryForTests()
        DrivingEvents.journalStoreFactory = { store }
        DrivingEvents.setAnnounceEnabled(context, true)
        DrivingEvents.speaker = { _, text, id, cb ->
            spoken.add(text to id); cb?.invoke(true, null); null
        }
    }
    @After fun tearDown() {
        DrivingEvents.resetTransitionMemoryForTests()
        DrivingEvents.journalStoreFactory = factory
        DrivingEvents.speaker = speaker
        DrivingTrip.stop()
    }
    @Test fun duplicateOnEdgeSpeaksOnceAndPersistsOneAcceptedRecord() {
        DrivingEvents.onGateChange(context, true, 20f, 1000L)
        DrivingEvents.onGateChange(context, true, 21f, 2000L)
        assertEquals(1, spoken.size); assertEquals(1, store.disk.size)
        assertEquals(DrivingTransition.FINISHED, store.disk.single().audio)
    }
    @Test fun genuineToggleSequenceEachSpeaksWithUniqueIds() {
        for ((i, state) in listOf(true, false, true).withIndex())
            DrivingEvents.onGateChange(context, state, 20f, i.toLong())
        assertEquals(listOf(DrivingEvents.startedText(), DrivingEvents.endedText(), DrivingEvents.startedText()), spoken.map { it.first })
        assertEquals(3, spoken.map { it.second }.distinct().size)
        assertEquals(3, store.disk.size)
    }
    @Test fun memoryVisibleCommitFailureHoldsAudioAndRecoversOnce() {
        store.fail = true
        DrivingEvents.onGateChange(context, true, 20f, 1000L)
        assertTrue(spoken.isEmpty()); assertEquals(1, store.memory.size); assertTrue(store.disk.isEmpty())
        store.fail = false
        DrivingEvents.onGateChange(context, true, 21f, 2000L)
        assertEquals(1, spoken.size); assertEquals(1, store.disk.size)
    }
    @Test fun oppositeAfterFailureRecoversOnHistoryButNeverStaleOnAudio() {
        store.fail = true
        DrivingEvents.onGateChange(context, true, 20f, 1000L)
        store.fail = false
        DrivingEvents.onGateChange(context, false, 0f, 2000L)
        assertEquals(listOf(DrivingEvents.endedText()), spoken.map { it.first })
        assertEquals(listOf(true, false), store.disk.map { it.started })
        assertEquals(DrivingTransition.SKIPPED, store.disk.first().audio)
    }
    @Test fun disablingBeforeRecoveryPersistsSkippedNotRequested() {
        store.fail = true
        DrivingEvents.onGateChange(context, true, 20f, 1000L)
        store.fail = false
        DrivingEvents.setAnnounceEnabled(context, false)
        DrivingEvents.onGateChange(context, true, 20f, 2000L)
        assertTrue(spoken.isEmpty())
        assertEquals(DrivingTransition.SKIPPED, store.disk.single().audio)
        assertEquals("disabled by user; skipped", store.disk.single().result)
    }
    @Test fun processRestartDoesNotReplayAnUncertainPreviousSubmission() {
        DrivingEvents.speaker = { _, text, id, _ -> spoken.add(text to id); null }
        DrivingEvents.onGateChange(context, true, 20f, 1000L)
        DrivingEvents.resetTransitionMemoryForTests()
        val accepted = DrivingEvents.acceptedHistory(context)
        assertEquals(1, spoken.size); assertEquals(1, accepted.size)
        assertEquals(DrivingTransition.UNKNOWN, store.disk.single().audio)
    }
}

/** Actual Android SQLite persistence adapter tests, separate from the injectable failure model above. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class AndroidDrivingJournalStoreTest {
    @Test fun persistedSnapshotSurvivesStoreReopenAndUpsertDoesNotDuplicate() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.deleteDatabase("driving_journal.db")
        val first = AndroidDrivingJournalStore(context)
        val journal = DrivingJournal(first) { "stable" }
        journal.accept(true, 1L, 20f, "reason", "diagnostic")
        journal.prepareAudio(true)
        assertTrue(journal.flush()); first.close()
        val reopened = AndroidDrivingJournalStore(context)
        try {
            val recovered = DrivingJournal(reopened)
            assertTrue(recovered.prepareAudio(true).isEmpty())
            assertEquals(1, reopened.readDurable().size)
            assertEquals("stable", reopened.readDurable().single().id)
            assertEquals(DrivingTransition.UNKNOWN, reopened.readDurable().single().audio)
        } finally { reopened.close(); context.deleteDatabase("driving_journal.db") }
    }
}
