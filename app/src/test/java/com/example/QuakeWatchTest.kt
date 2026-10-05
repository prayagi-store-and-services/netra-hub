package com.example

import com.example.data.engine.selectNewQuakes
import com.example.ui.screens.QuakeItem
import org.junit.Assert.assertEquals
import org.junit.Test

class QuakeWatchTest {
    private val now = 1_000_000_000L
    @Test
    fun keepsOnlyNewRecentQuakes() {
        val q = listOf(
            QuakeItem(3.0, "a", now - 1_000L, 10.0, "id1"),
            QuakeItem(4.0, "b", now - 2_000L, 20.0, "id2"),
            QuakeItem(5.0, "c", now - 90_000_000L, 30.0, "id3"),
            QuakeItem(3.5, "d", now - 3_000L, 40.0, "")
        )
        val r = selectNewQuakes(q, setOf("id1"), now, 86_400_000L)
        assertEquals(listOf("id2"), r.map { it.id })
    }
}
