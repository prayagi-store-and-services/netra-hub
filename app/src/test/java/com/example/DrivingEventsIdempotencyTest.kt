package com.example

import androidx.test.core.app.ApplicationProvider
import com.example.driving.DrivingEvents
import com.example.driving.DrivingLog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class DrivingEventsIdempotencyTest {
    private val spoken = mutableListOf<Pair<String, String>>()

    @Before fun setUp() {
        spoken.clear()
        DrivingEvents.resetTransitionMemoryForTests()
        DrivingEvents.speaker = { _, text, id, cb ->
            spoken.add(text to id)
            cb?.invoke(true, null)
            null
        }
    }

    @Test fun duplicateOnEdgeSpeaksOnce() {
        val c = ApplicationProvider.getApplicationContext<android.content.Context>()
        DrivingEvents.onGateChange(c, true, 20f, 1_000L)
        DrivingEvents.onGateChange(c, true, 21f, 2_000L) // replay/double-owner edge
        assertEquals(1, spoken.size)
        assertTrue(spoken[0].second.startsWith("driving_T"))
    }

    @Test fun genuineToggleSequenceEachSpeaks() {
        val c = ApplicationProvider.getApplicationContext<android.content.Context>()
        DrivingEvents.onGateChange(c, true, 20f, 1_000L)
        DrivingEvents.onGateChange(c, false, 3f, 2_000L)
        DrivingEvents.onGateChange(c, true, 25f, 3_000L)
        assertEquals(3, spoken.size)
    }

    @Test fun transitionIdsAreUniqueAndIncreasing() {
        val c = ApplicationProvider.getApplicationContext<android.content.Context>()
        DrivingEvents.onGateChange(c, true, 20f, 1_000L)
        DrivingEvents.onGateChange(c, false, 3f, 2_000L)
        val ids = spoken.map { it.second.removePrefix("driving_T").toLong() }
        assertEquals(ids.sorted(), ids)
        assertEquals(ids.toSet().size, ids.size)
    }
}

/** Map-backed SharedPreferences whose commit() can be made to fail, to prove fail-closed behavior. */
private class FakePrefs(@Volatile var commitOk: Boolean) : android.content.SharedPreferences {
    private val map = HashMap<String, Any?>()
    override fun getAll(): MutableMap<String, *> = HashMap(map)
    override fun getString(k: String, d: String?): String? = map[k] as? String ?: d
    override fun getStringSet(k: String, d: MutableSet<String>?): MutableSet<String>? = d
    override fun getInt(k: String, d: Int): Int = map[k] as? Int ?: d
    override fun getLong(k: String, d: Long): Long = map[k] as? Long ?: d
    override fun getFloat(k: String, d: Float): Float = map[k] as? Float ?: d
    override fun getBoolean(k: String, d: Boolean): Boolean = map[k] as? Boolean ?: d
    override fun contains(k: String) = map.containsKey(k)
    override fun edit() = Ed()
    override fun registerOnSharedPreferenceChangeListener(l: OnSharedPreferenceChangeListener?) {}
    override fun unregisterOnSharedPreferenceChangeListener(l: OnSharedPreferenceChangeListener?) {}
    inner class Ed : android.content.SharedPreferences.Editor {
        private val writes = HashMap<String, Any?>()
        override fun putString(k: String, v: String?) = apply { writes[k] = v }
        override fun putStringSet(k: String, v: MutableSet<String>?) = apply { writes[k] = v }
        override fun putInt(k: String, v: Int) = apply { writes[k] = v }
        override fun putLong(k: String, v: Long) = apply { writes[k] = v }
        override fun putFloat(k: String, v: Float) = apply { writes[k] = v }
        override fun putBoolean(k: String, v: Boolean) = apply { writes[k] = v }
        override fun remove(k: String) = apply { writes.remove(k) }
        override fun clear() = apply { map.clear() }
        override fun commit(): Boolean { if (commitOk) map.putAll(writes); return commitOk }
        override fun apply() { commit() }
    }
}

private class PrefsRouter(base: android.content.Context, private val routes: Map<String, FakePrefs>) :
    android.content.ContextWrapper(base) {
    override fun getSharedPreferences(name: String, mode: Int): android.content.SharedPreferences =
        routes[name] ?: super.getSharedPreferences(name, mode)
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class DrivingEventsPersistenceTest {
    private val spoken = mutableListOf<String>()
    private lateinit var base: android.content.Context

    @Before fun setUp() {
        spoken.clear()
        DrivingEvents.resetTransitionMemoryForTests()
        DrivingEvents.speaker = { _, text, _, cb -> spoken.add(text); cb?.invoke(true, null); null }
        base = ApplicationProvider.getApplicationContext()
        DrivingLog.clear(base)
    }

    @Test fun idCommitFailureHoldsSpeechUntilRecovery() {
        val announce = FakePrefs(commitOk = false)
        val c = PrefsRouter(base, mapOf(DrivingEvents.PREFS to announce))
        DrivingEvents.onGateChange(c, true, 20f, 1_000L)
        org.junit.Assert.assertTrue("speech must be held when the id cannot persist", spoken.isEmpty())
        announce.commitOk = true // storage recovers; next same-state edge retries
        DrivingEvents.onGateChange(c, true, 21f, 2_000L)
        org.junit.Assert.assertEquals(1, spoken.size)
        // The accepted transition is recorded exactly ONCE with a unique id; the held attempt was a NOTE without one.
        val entries = DrivingLog.list(base)
        org.junit.Assert.assertEquals(1, entries.count { it.kind == DrivingLog.STARTED })
        org.junit.Assert.assertTrue(entries.any { it.kind == DrivingLog.NOTE && it.reason.contains("no id issued") })
        org.junit.Assert.assertFalse(entries.any { it.kind == DrivingLog.NOTE && it.reason.contains("T#") })
    }

    @Test fun oppositeEdgeSupersedesHeldAnnouncement() {
        val announce = FakePrefs(commitOk = false)
        val c = PrefsRouter(base, mapOf(DrivingEvents.PREFS to announce))
        DrivingEvents.onGateChange(c, true, 20f, 1_000L)  // ON held
        announce.commitOk = true
        DrivingEvents.onGateChange(c, false, 3f, 2_000L)  // world moved to OFF: the held ON is stale
        org.junit.Assert.assertEquals(listOf(DrivingEvents.endedText()), spoken) // old ON never replays
        val log = DrivingLog.list(base).joinToString("\n") { it.reason }
        org.junit.Assert.assertTrue(log.contains("superseded"))
    }

    @Test fun logCommitFailureHoldsSpeech() {
        val logPrefs = FakePrefs(commitOk = false)
        val c = PrefsRouter(base, mapOf(DrivingLog.PREFS to logPrefs))
        DrivingEvents.onGateChange(c, true, 20f, 1_000L)
        org.junit.Assert.assertTrue("a committed id must not prove the request durable", spoken.isEmpty())
    }
}
