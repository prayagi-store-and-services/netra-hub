package com.example

import com.example.update.AppUpdater
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class UpdateCheckClassifyTest {
    private val sha = "ab".repeat(32)
    private fun json(code: Long) = JSONObject().put("tag", "v1.2.0").put("versionName", "1.2.0").put("versionCode", code)
        .put("sha256", sha).put("size", 1000).put("notes", "x").toString()

    @Test fun newerIsReported() = assertTrue(AppUpdater.classify(json(6), 5) is AppUpdater.CheckResult.Newer)
    @Test fun sameOrOlderMeansLatest() {
        assertTrue(AppUpdater.classify(json(5), 5) is AppUpdater.CheckResult.Latest)
        assertTrue(AppUpdater.classify(json(4), 5) is AppUpdater.CheckResult.Latest)
    }
    @Test fun garbageIsFailedNotLatest() {
        assertTrue(AppUpdater.classify("not json", 5) is AppUpdater.CheckResult.Failed)
        assertTrue(AppUpdater.classify("{}", 5) is AppUpdater.CheckResult.Failed)
    }
}
