package com.example.camcheck

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CameraCheckLogicTest {
    @Test fun privateRanges() {
        assertTrue(CameraCheckLogic.isPrivateIpv4("192.168.1.20"))
        assertTrue(CameraCheckLogic.isPrivateIpv4("10.0.0.5"))
        assertTrue(CameraCheckLogic.isPrivateIpv4("172.16.4.4"))
        assertTrue(CameraCheckLogic.isPrivateIpv4("172.31.255.1"))
        assertFalse(CameraCheckLogic.isPrivateIpv4("172.32.0.1"))
        assertFalse(CameraCheckLogic.isPrivateIpv4("8.8.8.8"))
        assertFalse(CameraCheckLogic.isPrivateIpv4("192.168.1"))
        assertFalse(CameraCheckLogic.isPrivateIpv4("abc"))
        assertFalse(CameraCheckLogic.isPrivateIpv4("300.1.1.1"))
    }

    @Test fun slash24TargetsSkipOwnAddress() {
        val t = CameraCheckLogic.scanTargets("192.168.1.20", 24)
        assertEquals(253, t.size)
        assertEquals("192.168.1.1", t.first())
        assertEquals("192.168.1.254", t.last())
        assertFalse(t.contains("192.168.1.20"))
        assertFalse(t.contains("192.168.1.0"))
        assertFalse(t.contains("192.168.1.255"))
    }

    @Test fun largerNetworksAreLimitedToOneSlash24() {
        val t = CameraCheckLogic.scanTargets("10.5.7.9", 16)
        assertEquals(253, t.size)
        assertTrue(t.all { it.startsWith("10.5.7.") })
    }

    @Test fun smallNetworkUsesItsOwnSize() {
        val t = CameraCheckLogic.scanTargets("192.168.43.2", 28)
        assertEquals(13, t.size)
        assertTrue(t.all { it.startsWith("192.168.43.") })
    }

    @Test fun publicOrTinyNetworksAreNotScanned() {
        assertTrue(CameraCheckLogic.scanTargets("8.8.8.8", 24).isEmpty())
        assertTrue(CameraCheckLogic.scanTargets("192.168.1.20", 31).isEmpty())
        assertTrue(CameraCheckLogic.scanTargets("nonsense", 24).isEmpty())
    }

    @Test fun cameraHints() {
        assertNull(CameraCheckLogic.cameraHint(null, listOf(80, 443)))
        assertNull(CameraCheckLogic.cameraHint("galaxy-s23", emptyList()))
        assertTrue(CameraCheckLogic.cameraHint(null, listOf(80, 554))!!.contains("554"))
        assertTrue(CameraCheckLogic.cameraHint("Living-Room-CAM.lan", emptyList())!!.contains("cam"))
        assertTrue(CameraCheckLogic.cameraHint("ipc-8fa1", listOf(80))!!.contains("ipc"))
    }

    @Test fun magneticLevels() {
        assertEquals(MagLevel.NORMAL, CameraCheckLogic.magLevel(45f, 50f))
        assertEquals(MagLevel.NOTICEABLE, CameraCheckLogic.magLevel(45f, 60f))
        assertEquals(MagLevel.NOTICEABLE, CameraCheckLogic.magLevel(45f, 30f))
        assertEquals(MagLevel.STRONG, CameraCheckLogic.magLevel(45f, 85f))
        assertEquals(5f, CameraCheckLogic.magnitude(3f, 4f, 0f), 0.001f)
    }

    @Test fun checklistIsNotEmptyAndAdmitsLimits() {
        assertTrue(CameraCheckLogic.ROOM_CHECKLIST.size >= 5)
        assertTrue(CameraCheckLogic.ROOM_CHECKLIST.last().contains("does not prove"))
    }
}
