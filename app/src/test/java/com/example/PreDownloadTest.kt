package com.example.update

import com.example.update.PreDownload
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PreDownloadTest {
    private val mb = 1024L * 1024
    private fun d(online: Boolean = true, unmetered: Boolean = false, roaming: Boolean = false, saver: Boolean = false, low: Boolean = false, saved: Boolean = false, size: Long = 8 * mb) =
        PreDownload.shouldPreDownload(online, unmetered, roaming, saver, low, saved, size)

    @Test fun wifiDownloadsAnySize() = assertTrue(d(unmetered = true, size = 300 * mb))
    @Test fun mobileDataSmallFileDownloads() = assertTrue(d())
    @Test fun mobileDataOverTheLimitWaits() = assertFalse(d(size = 100 * mb + 1))
    @Test fun roamingMobileDataNeverDownloads() = assertFalse(d(roaming = true))
    @Test fun offlineNever() = assertFalse(d(online = false, unmetered = true))
    @Test fun dataSaverStops() = assertFalse(d(unmetered = true, saver = true))
    @Test fun lowBatteryStops() = assertFalse(d(unmetered = true, low = true))
    @Test fun alreadySavedNotAgain() = assertFalse(d(unmetered = true, saved = true))
    @Test fun newerSavedFileIsKept() = assertTrue(PreDownload.keepFile("netra-hub-v1.4.10.apk", "1.4.9", 99_000_000L))
    @Test fun installedOrOlderFileIsDropped() {
        assertFalse(PreDownload.keepFile("netra-hub-v1.4.9.apk", "1.4.9", 1L))
        assertFalse(PreDownload.keepFile("netra-hub-v1.4.8.apk", "1.4.9", 1L))
    }
}
