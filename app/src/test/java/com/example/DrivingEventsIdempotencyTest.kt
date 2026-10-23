package com.example

import androidx.test.core.app.ApplicationProvider
import com.example.driving.DrivingEvents
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
