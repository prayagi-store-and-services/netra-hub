package com.example

import com.example.util.drainAll
import com.example.util.safeSnapshot
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConcurrentSnapshotTest {
    @Test fun snapshotCopiesInOrder() {
        val q = ConcurrentLinkedQueue(listOf(1, 2, 3))
        assertEquals(listOf(1, 2, 3), safeSnapshot(q))
        assertEquals(3, q.size)
    }

    @Test fun drainTakesEverythingOnce() {
        val q = ConcurrentLinkedQueue(listOf("a", "b"))
        assertEquals(listOf("a", "b"), drainAll(q))
        assertTrue(q.isEmpty())
        assertTrue(drainAll(q).isEmpty())
    }

    /** The crash: one thread keeps the queue at 0 or 1 items while another copies it. */
    @Test fun neverThrowsWhileAnotherThreadEmptiesTheQueue() {
        val q = ConcurrentLinkedQueue<Int>()
        val d = ConcurrentLinkedDeque<Int>()
        val stop = AtomicBoolean(false)
        val churn = Thread {
            var i = 0
            while (!stop.get()) { q.add(i); d.addLast(i); q.poll(); d.pollFirst(); i++ }
        }
        churn.start()
        var failure: Throwable? = null
        try {
            repeat(200_000) { safeSnapshot(q); safeSnapshot(d) }
        } catch (t: Throwable) { failure = t }
        stop.set(true); churn.join()
        assertNull("snapshot threw $failure", failure)
    }
}
