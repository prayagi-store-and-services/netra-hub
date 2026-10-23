package com.example.driving

/** A successful write means the WHOLE snapshot is durable. readDurable() must read disk, never a
 * SharedPreferences memory cache. A failed write may have changed a cache; the journal never reads it.
 * The implementation must preserve either the old or new complete snapshot across interrupted writes. */
internal interface DrivingJournalStore {
    fun readDurable(): List<DrivingTransition>
    fun writeDurable(snapshot: List<DrivingTransition>): Boolean
}

internal data class DrivingTransition(
    val id: String,
    val at: Long,
    val started: Boolean,
    val speedKmH: Float?,
    val reason: String,
    val diagnostics: String,
    val audio: String = READY,
    val result: String = ""
) {
    companion object {
        const val READY = "ready"
        const val SUBMITTED = "submitted"
        const val FINISHED = "finished"
        const val FAILED = "failed"
        const val SKIPPED = "skipped"
        const val UNKNOWN = "unknown_after_restart"
    }
}

/** One snapshot contains the accepted history AND audio intent. Stable IDs make every reconciliation an
 * upsert, never an append of the same transition. No numeric ID counter lives in another preference file.
 * Pending writes stay in the desired snapshot until they land. A disk-failed event is not called durable.
 * No program can recover bytes that never reached any durable medium; such a failure holds all audio.
 * Restart keeps history but never blindly replays audio with an uncertain outcome. */
internal class DrivingJournal(
    private val store: DrivingJournalStore,
    private val newId: () -> String = { java.util.UUID.randomUUID().toString() }
) {
    private var desired = store.readDurable().distinctBy { it.id }.takeLast(MAX)
    private var lastProcessState: Boolean? = null

    init {
        // Both submitted and ready belong to a previous process. Preserve the accepted record and mark
        // audio uncertainty explicitly. Recovery never turns those old records back into speech work.
        desired = desired.map { event ->
            if (event.audio == DrivingTransition.READY || event.audio == DrivingTransition.SUBMITTED)
                event.copy(audio = DrivingTransition.UNKNOWN, result = "audio outcome unknown after process restart; not replayed")
            else event
        }
        store.writeDurable(desired)
    }

    @Synchronized fun snapshot(): List<DrivingTransition> = desired.toList()

    /** Duplicate suppression is process-local because the detector itself restarts CLOSED after death. */
    @Synchronized fun accept(started: Boolean, at: Long, speed: Float?, reason: String, diagnostics: String): String? {
        if (lastProcessState == started) return null
        lastProcessState = started
        val id = newId()
        require(desired.none { it.id == id }) { "transition id must be unique" }
        // A later authoritative state makes an unsubmitted prior audio request stale, but never erases
        // its accepted history. Normal committed submissions/queued TTS edges are never dropped here.
        desired = desired.map {
            if (it.audio == DrivingTransition.READY && it.started != started)
                it.copy(audio = DrivingTransition.SKIPPED, result = "unsubmitted audio superseded by current state")
            else it
        }
        desired = (desired + DrivingTransition(id, at, started, speed, reason, diagnostics)).takeLast(MAX)
        store.writeDurable(desired)
        return id
    }

    /** Snapshot+submission intent become durable BEFORE returning work for the engine. The caller never
     * submits returned work twice. Failed writes keep the same IDs/envelopes pending for reconciliation. */
    @Synchronized fun prepareAudio(enabled: Boolean): List<DrivingTransition> {
        val ready = desired.filter { it.audio == DrivingTransition.READY }
        val ids = ready.map { it.id }.toSet()
        val next = desired.map {
            if (it.id in ids) it.copy(
                audio = if (enabled) DrivingTransition.SUBMITTED else DrivingTransition.SKIPPED,
                result = if (enabled) "submission intent persisted before engine call" else "disabled by user; skipped")
            else it
        }
        if (!store.writeDurable(next)) return emptyList()
        desired = next
        return if (enabled) next.filter { it.id in ids } else emptyList()
    }

    @Synchronized fun complete(id: String, ok: Boolean, result: String) {
        desired = desired.map {
            if (it.id == id && it.audio == DrivingTransition.SUBMITTED)
                it.copy(audio = if (ok) DrivingTransition.FINISHED else DrivingTransition.FAILED, result = result)
            else it
        }
        store.writeDurable(desired)
    }

    @Synchronized fun flush(): Boolean = store.writeDurable(desired)

    /** Clear durable history without resetting detector dedup or creating new audio work. Already submitted
     * engine requests keep their normal queue lifetime; late callbacks cannot recreate cleared records. */
    @Synchronized fun clearHistory(): Boolean {
        if (!store.writeDurable(emptyList())) return false
        desired = emptyList()
        return true
    }

    companion object { const val MAX = 500 }
}

/** Versioned binary snapshot codec, shared by production storage and the crash-boundary tests. */
internal object DrivingJournalCodec {
    fun encode(events: List<DrivingTransition>): ByteArray {
        val bytes = java.io.ByteArrayOutputStream()
        java.io.DataOutputStream(bytes).use { out ->
            out.writeInt(1)
            out.writeInt(events.size)
            events.forEach {
                out.writeUTF(it.id); out.writeLong(it.at); out.writeBoolean(it.started)
                out.writeBoolean(it.speedKmH != null); it.speedKmH?.let { speed -> out.writeFloat(speed) }
                out.writeUTF(it.reason.take(4000)); out.writeUTF(it.diagnostics.take(8000))
                out.writeUTF(it.audio); out.writeUTF(it.result.take(4000))
            }
        }
        return bytes.toByteArray()
    }
    fun decode(bytes: ByteArray): List<DrivingTransition> = java.io.DataInputStream(java.io.ByteArrayInputStream(bytes)).use { input ->
        require(input.readInt() == 1) { "unsupported driving journal version" }
        val size = input.readInt()
        require(size in 0..DrivingJournal.MAX)
        List(size) {
            DrivingTransition(input.readUTF(), input.readLong(), input.readBoolean(),
                if (input.readBoolean()) input.readFloat() else null,
                input.readUTF(), input.readUTF(), input.readUTF(), input.readUTF())
        }
    }
}
