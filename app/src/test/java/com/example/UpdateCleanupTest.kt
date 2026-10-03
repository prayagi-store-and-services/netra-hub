package com.example

import com.example.update.AppUpdater
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCleanupTest {
    @Test
    fun removesEveryDownloadedInstaller() {
        val dir = java.nio.file.Files.createTempDirectory("updates").toFile()
        File(dir, "app-v1.apk").writeText("a")
        File(dir, "app-v2.apk").writeText("b")
        assertEquals(2, AppUpdater.cleanDir(dir))
        assertTrue(dir.listFiles()!!.isEmpty())
        dir.delete()
    }

    @Test
    fun missingFolderIsFine() {
        assertEquals(0, AppUpdater.cleanDir(File("/nonexistent-folder-for-test")))
        assertEquals(0, AppUpdater.cleanDir(null))
    }
}
