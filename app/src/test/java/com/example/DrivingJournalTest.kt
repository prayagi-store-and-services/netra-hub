package com.example

import com.example.driving.*
import org.junit.Assert.*
import org.junit.Test

class DrivingJournalTest {
    /** Models Android's important failure semantic: memory changes EVEN WHEN the disk write fails. */
    internal class MemoryVisibleFailureStore : DrivingJournalStore {
        var memory = emptyList<DrivingTransition>()
        var disk = emptyList<DrivingTransition>()
        var fail = false
        override fun readDurable() = DrivingJournalCodec.decode(DrivingJournalCodec.encode(disk))
        override fun writeDurable(snapshot: List<DrivingTransition>): Boolean {
            memory = snapshot.toList()
            if (fail) return false
            disk = DrivingJournalCodec.decode(DrivingJournalCodec.encode(snapshot))
            return true
        }
    }
    private fun journal(s: MemoryVisibleFailureStore): DrivingJournal {
        var n = 0
        return DrivingJournal(s) { "event_${++n}" }
    }
    @Test fun failedWriteChangesMemoryButCannotSubmitAudio() {
        val s = MemoryVisibleFailureStore(); val j = journal(s); s.fail = true
        j.accept(true, 1L, 20f, "on", "diagnostic")
        assertEquals(1, s.memory.size); assertTrue(s.disk.isEmpty())
        assertTrue(j.prepareAudio(true).isEmpty())
    }
    @Test fun recoverySameStateHasOneStableAcceptedRecord() {
        val s = MemoryVisibleFailureStore(); val j = journal(s); s.fail = true
        val id = j.accept(true, 1L, 20f, "on", "full diagnostic")
        assertNull(j.accept(true, 2L, 21f, "duplicate", ""))
        assertTrue(j.prepareAudio(true).isEmpty())
        s.fail = false
        assertEquals(listOf(id), j.prepareAudio(true).map { it.id })
        assertEquals(1, s.disk.size); assertEquals("full diagnostic", s.disk.single().diagnostics)
        assertTrue(j.prepareAudio(true).isEmpty())
    }
    @Test fun oppositeDuringFailureRetainsBothHistoriesWithoutStaleSpeech() {
        val s = MemoryVisibleFailureStore(); val j = journal(s); s.fail = true
        val on = j.accept(true, 1L, 20f, "on", "a")
        j.prepareAudio(true)
        j.accept(false, 2L, 0f, "off", "b")
        s.fail = false
        val work = j.prepareAudio(true)
        assertEquals(listOf(false), work.map { it.started })
        assertEquals(2, s.disk.size)
        assertEquals(DrivingTransition.SKIPPED, s.disk.first { it.id == on }.audio)
    }
    @Test fun disablingBeforeRecoveryRecordsSkippedNotRequested() {
        val s = MemoryVisibleFailureStore(); val j = journal(s); s.fail = true
        j.accept(true, 1L, 20f, "on", "")
        j.prepareAudio(true); s.fail = false
        assertTrue(j.prepareAudio(false).isEmpty())
        assertEquals(DrivingTransition.SKIPPED, s.disk.single().audio)
        assertEquals("disabled by user; skipped", s.disk.single().result)
    }
    @Test fun crashAfterDurableAcceptKeepsFullHistoryNoBlindReplay() {
        val s = MemoryVisibleFailureStore(); val j = journal(s)
        j.accept(true, 10L, 30f, "reason", "original diagnostics")
        val restarted = DrivingJournal(s)
        assertTrue(restarted.prepareAudio(true).isEmpty())
        assertEquals(DrivingTransition.UNKNOWN, s.disk.single().audio)
        assertEquals("original diagnostics", s.disk.single().diagnostics)
    }
    @Test fun crashAfterSubmissionIntentMarksUnknownAndNeverSubmitsAgain() {
        val s = MemoryVisibleFailureStore(); val j = journal(s)
        j.accept(true, 10L, 30f, "reason", "")
        assertEquals(1, j.prepareAudio(true).size)
        val restarted = DrivingJournal(s)
        assertTrue(restarted.prepareAudio(true).isEmpty())
        assertEquals(DrivingTransition.UNKNOWN, s.disk.single().audio)
    }
    @Test fun ordinaryCommittedOnOffOnEdgesAreAllPreserved() {
        val s = MemoryVisibleFailureStore(); val j = journal(s)
        val work = mutableListOf<DrivingTransition>()
        for ((n, state) in listOf(true, false, true).withIndex()) {
            j.accept(state, n.toLong(), 20f, "edge", "")
            work += j.prepareAudio(true)
        }
        assertEquals(listOf(true, false, true), work.map { it.started })
        assertEquals(3, work.map { it.id }.distinct().size)
        assertEquals(3, s.disk.size)
    }
    @Test fun callbackReplayCannotAppendOrOverwriteFinishedHistory() {
        val s = MemoryVisibleFailureStore(); val j = journal(s)
        val id = j.accept(true, 1L, 20f, "on", "")!!
        j.prepareAudio(true); j.complete(id, true, "done"); j.complete(id, false, "late error")
        assertEquals(1, s.disk.size); assertEquals(DrivingTransition.FINISHED, s.disk.single().audio)
        assertEquals("done", s.disk.single().result)
    }
    @Test fun clearHistoryDoesNotReplayOrResurrectSubmittedWork() {
        val s = MemoryVisibleFailureStore(); val j = journal(s)
        val id = j.accept(true, 1L, 20f, "on", "")!!
        j.prepareAudio(true)
        assertTrue(j.clearHistory())
        j.complete(id, true, "late done")
        assertTrue(s.disk.isEmpty())
        assertTrue(j.prepareAudio(true).isEmpty())
        assertNull(j.accept(true, 2L, 20f, "duplicate", ""))
    }
    @Test fun failedClearPreservesTheAcceptedHistory() {
        val s = MemoryVisibleFailureStore(); val j = journal(s)
        j.accept(true, 1L, 20f, "on", "")
        s.fail = true
        assertFalse(j.clearHistory()); assertEquals(1, j.snapshot().size)
        assertEquals(1, s.disk.size)
    }
    @Test fun ambiguousClearCommitReadsDiskAndNeverResurrects() {
        val backing = MemoryVisibleFailureStore()
        var ambiguous = false
        val store = object : DrivingJournalStore {
            override fun readDurable() = backing.readDurable()
            override fun writeDurable(snapshot: List<DrivingTransition>): Boolean {
                backing.writeDurable(snapshot)
                return !ambiguous
            }
        }
        val j = DrivingJournal(store) { "one" }
        j.accept(true, 1L, 20f, "on", "")
        j.prepareAudio(true)
        ambiguous = true
        assertTrue(j.clearHistory()) // acknowledgement lost, but disk proves the clear landed
        j.complete("one", true, "late done"); j.flush()
        assertTrue(backing.disk.isEmpty()); assertTrue(j.snapshot().isEmpty())
    }
    @Test fun codecRoundTripsNullSpeedAndMultilineDiagnostics() {
        val e = DrivingTransition("uuid", 123L, false, null, "a|b\nc", "age=5s\nowner=one")
        assertEquals(listOf(e), DrivingJournalCodec.decode(DrivingJournalCodec.encode(listOf(e))))
    }
}
