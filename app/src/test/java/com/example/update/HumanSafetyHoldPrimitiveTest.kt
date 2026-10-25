package com.example.update

import android.app.Application
import java.io.File
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(com.example.OwnedProcessTestRunner::class)
@Config(sdk = [34], application = Application::class)
class HumanSafetyHoldPrimitiveTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private fun release() = AppRelease("v999.0.0", "999.0.0", 999999L, "", "https://invalid.example/never.apk", "ab".repeat(32), 3L)
    @Test fun manualCheckReturnsPausedRatherThanLatestOrNetworkFailure() {
        assertSame(AppUpdater.CheckResult.Paused, AppUpdater.checkNow(context))
    }
    @Test fun dailyCheckDoesNotOfferAnyRelease() {
        assertNull(AppUpdater.checkIfDue(context))
    }
    @Test fun cachedMatchingSizeApkIsNotOfferedAndNotDeleted() {
        val file = File(File(context.cacheDir, "updates").apply { mkdirs() }, "netra-hub-v999.0.0.apk")
        file.writeText("apk")
        try {
            assertNull(AppUpdater.savedFile(context, release()))
            assertEquals("apk", file.readText())
        } finally { file.delete() }
    }
    @Test fun directDownloadFailsWithAdvisoryBeforeProgressOrNetwork() {
        var progressCalled = false
        try {
            AppUpdater.download(context, release()) { _, _ -> progressCalled = true }
            fail("quarantined download must fail")
        } catch (e: IllegalStateException) { assertEquals(HumanSafetyHold.MESSAGE, e.message) }
        assertFalse(progressCalled)
    }
    @Test fun directInstallerRejectsEvenRenamedFileBeforeAnySystemHandoff() {
        try {
            AppUpdater.install(context, File(context.cacheDir, "renamed.apk"))
            fail("quarantined install must fail")
        } catch (e: IllegalStateException) { assertEquals(HumanSafetyHold.MESSAGE, e.message) }
        assertNull(org.robolectric.Shadows.shadowOf(context).nextStartedActivity)
    }
}
