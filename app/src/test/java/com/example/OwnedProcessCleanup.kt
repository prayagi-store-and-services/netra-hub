package com.example

import android.os.Looper
import com.example.data.repository.NetraSafetyRepository
import com.example.nasre.NasreEngine
import kotlinx.coroutines.runBlocking
import org.robolectric.Shadows.shadowOf
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Loaded through the test sandbox. Keep the paused Android main looper available to real cleanup. */
object OwnedProcessCleanup {
    @JvmStatic fun dispose() {
        val worker = Executors.newSingleThreadExecutor()
        val future = worker.submit {
            runBlocking {
                NasreEngine.disposeIfCreated()
                NetraSafetyRepository.disposeIfCreated()
            }
        }
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
        try {
            while (!future.isDone && System.nanoTime() < deadline) {
                shadowOf(Looper.getMainLooper()).idle()
                Thread.yield()
            }
            check(future.isDone) { "Process cleanup did not join within 10 seconds" }
            shadowOf(Looper.getMainLooper()).idle() // Drain listener removal posted during cancellation.
            future.get() // Propagate the actual cleanup error, never swallow it.
        } finally {
            future.cancel(true)
            worker.shutdownNow()
        }
    }
}
