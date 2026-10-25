package com.example

import android.content.BroadcastReceiver
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.IntentFilter
import androidx.test.core.app.ApplicationProvider
import com.example.ui.withReceiverCleanup
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(com.example.OwnedProcessTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class ReceiverCleanupTest {
    private class Owner(base: Context) : ContextWrapper(base) {
        val active = mutableSetOf<BroadcastReceiver>()
        var releases = 0
        override fun registerReceiver(receiver: BroadcastReceiver?, filter: IntentFilter?): Intent? {
            if (receiver != null) active.add(receiver)
            return null
        }
        override fun unregisterReceiver(receiver: BroadcastReceiver) {
            if (!active.remove(receiver)) throw IllegalArgumentException("not registered")
            releases++
        }
    }
    private fun receiver() = object : BroadcastReceiver() { override fun onReceive(context: Context?, intent: Intent?) {} }
    @Test fun cancelledScansReleaseOnlyTheirOwnReceiverAndDoNotAccumulate() = runTest {
        val owner = Owner(ApplicationProvider.getApplicationContext())
        repeat(4) {
            val receiver = receiver()
            val job = launch {
                withReceiverCleanup(owner, receiver) {
                    owner.registerReceiver(receiver, IntentFilter("scan"))
                    awaitCancellation()
                }
            }
            runCurrent()
            assertEquals(1, owner.active.size)
            job.cancelAndJoin()
            assertTrue(owner.active.isEmpty())
        }
        assertEquals(4, owner.releases)
    }
    @Test fun completionAndFailureAlsoReleaseAndAbsentRegistrationIsSafe() = runTest {
        val owner = Owner(ApplicationProvider.getApplicationContext())
        val completed = receiver()
        assertEquals(42, withReceiverCleanup(owner, completed) {
            owner.registerReceiver(completed, IntentFilter("scan")); 42
        })
        val failed = receiver()
        try {
            withReceiverCleanup(owner, failed) {
                owner.registerReceiver(failed, IntentFilter("scan"))
                throw IllegalStateException("scan failed")
            }
            fail("Original failure must propagate")
        } catch (expected: IllegalStateException) { assertEquals("scan failed", expected.message) }
        withReceiverCleanup(owner, receiver()) { } // permission missing / registration failed
        assertTrue(owner.active.isEmpty())
        assertEquals(2, owner.releases)
    }
}
