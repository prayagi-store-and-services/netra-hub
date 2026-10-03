package com.example

import com.example.update.AppUpdater
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class AppUpdaterTest {
    private val sha = "ab".repeat(32)
    private fun json(tag: String = "v1.2.0", code: Long = 5, hash: String = sha, size: Long = 1000) =
        JSONObject().put("tag", tag).put("versionName", tag.removePrefix("v")).put("versionCode", code)
            .put("sha256", hash).put("size", size).put("notes", "Fixes").toString()

    @Test fun newerReleaseIsOfferedWithTheAppReleaseApkUrl() {
        val r = AppUpdater.parse(json(), 4)
        assertNotNull(r)
        assertEquals("https://github.com/" + AppUpdater.REPO + "/releases/download/v1.2.0/app-release.apk", r!!.apkUrl)
        assertEquals(5L, r.versionCode)
    }
    @Test fun sameOrOlderVersionIsNotOffered() {
        assertNull(AppUpdater.parse(json(), 5))
        assertNull(AppUpdater.parse(json(), 9))
    }
    @Test fun malformedMetadataIsRejected() {
        assertNull(AppUpdater.parse(json(tag = "latest"), 1))
        assertNull(AppUpdater.parse(json(hash = "xyz"), 1))
        assertNull(AppUpdater.parse(json(size = 0), 1))
        assertNull(AppUpdater.parse("not json", 1))
    }
}
