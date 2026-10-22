package com.example.util

import java.util.Queue

/**
 * Safe copies of lock-free collections that other threads may change at the same moment.
 *
 * Kotlin's Collection.toList() reads size first and, when the size is 1, calls iterator().next().
 * On a ConcurrentLinkedQueue or ConcurrentLinkedDeque another thread can empty it between those two
 * steps, which throws NoSuchElementException (seen in a real crash report). ArrayList(collection)
 * copies through toArray(), which never throws for a concurrent change.
 */
fun <T> safeSnapshot(source: Collection<T>): List<T> = ArrayList(source)

/** Removes and returns everything queued right now. Items added meanwhile stay for the next call. */
fun <T : Any> drainAll(queue: Queue<T>): List<T> {
    val out = ArrayList<T>()
    while (true) out.add(queue.poll() ?: break)
    return out
}
