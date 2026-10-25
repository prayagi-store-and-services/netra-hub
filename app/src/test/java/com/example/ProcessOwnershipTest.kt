package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.repository.NetraSafetyRepository
import com.example.data.engine.WatchdogEngine
import com.example.nasre.NasreEngine
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.Robolectric
import com.example.nasre.NasreInitializer

@RunWith(OwnedProcessTestRunner::class)
@Config(sdk = [34])
class ProcessOwnershipTest {
    @Test fun realManifestProviderStartsAndOwnedGraphCanBeDisposedAndRecreated() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        // Robolectric does not eagerly initialize this manifest provider. Resolve the real manifest
        // declaration and create the actual provider, without a mock or replacement manifest.
        val info = context.packageManager.resolveContentProvider(context.packageName + ".nasre", 0)
        assertNotNull("Real manifest must declare NASRE provider", info)
        assertEquals(NasreInitializer::class.java.name, info!!.name)
        Robolectric.buildContentProvider(NasreInitializer::class.java).create()
        val original = NetraSafetyRepository.getIfCreated()
        assertNotNull("Real NasreInitializer must create the repository", original)
        assertTrue("Real NasreInitializer must create NASRE", NasreEngine.isCreated())
        withTimeout(10_000) {
            NasreEngine.disposeIfCreated()
            NetraSafetyRepository.disposeIfCreated()
            // Disposal is idempotent and must return without a delayed audit touching a dead context.
            NasreEngine.disposeIfCreated()
            NetraSafetyRepository.disposeIfCreated()
        }
        assertNull(NetraSafetyRepository.getIfCreated())
        assertFalse(NasreEngine.isCreated())
        val recreated = NetraSafetyRepository.get(context)
        assertNotSame(original, recreated)
    }

    @Test fun watchdogBlockedQueueReadDoesNotPreventTerminalJoin() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val watchdog = WatchdogEngine(context, null, null, null)
        withTimeout(10_000) {
            watchdog.dispose()
            watchdog.dispose()
        }
    }
}
