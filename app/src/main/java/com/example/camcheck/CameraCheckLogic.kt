package com.example.camcheck

/** One device found on the local Wi-Fi network. */
data class LanDevice(
    val ip: String,
    val hostName: String?,
    val openPorts: List<Int>,
    val cameraHint: String?
)

enum class MagLevel { NORMAL, NOTICEABLE, STRONG }

/** Pure logic for the hidden camera check tools. No Android classes, so it is unit-tested. */
object CameraCheckLogic {
    /** Ports we try on every address. Short list on purpose: quick, and not an attack tool. */
    val PROBE_PORTS = listOf(80, 443, 554, 8080, 8554, 8000, 37777, 34567, 22)

    private val CAMERA_PORTS = mapOf(
        554 to "port 554 (RTSP video stream) answered",
        8554 to "port 8554 (RTSP video stream) answered",
        37777 to "port 37777 (common camera/recorder port) answered",
        34567 to "port 34567 (common camera/recorder port) answered",
        8000 to "port 8000 (common camera port) answered"
    )

    private val NAME_WORDS = listOf("cam", "ipc", "dvr", "nvr", "hikvision", "dahua", "reolink", "ezviz", "imou", "wyze")

    const val NOTICEABLE_UT = 15f
    const val STRONG_UT = 40f

    fun isPrivateIpv4(ip: String): Boolean {
        val p = parse(ip) ?: return false
        return p[0] == 10 || (p[0] == 172 && p[1] in 16..31) || (p[0] == 192 && p[1] == 168)
    }

    private fun parse(ip: String): IntArray? {
        val parts = ip.trim().split(".")
        if (parts.size != 4) return null
        val out = IntArray(4)
        for (i in 0 until 4) {
            val v = parts[i].toIntOrNull() ?: return null
            if (v !in 0..255) return null
            out[i] = v
        }
        return out
    }

    /**
     * Addresses to probe: the phone's own network, never larger than one /24 (254 addresses).
     * Returns an empty list for a public or invalid address, so nothing outside a private network is ever scanned.
     */
    fun scanTargets(ownIp: String, prefixLength: Int): List<String> {
        if (!isPrivateIpv4(ownIp)) return emptyList()
        val p = parse(ownIp) ?: return emptyList()
        val prefix = if (prefixLength < 24) 24 else prefixLength
        if (prefix > 30) return emptyList()
        val own = (p[0] shl 24) or (p[1] shl 16) or (p[2] shl 8) or p[3]
        val mask = (-1 shl (32 - prefix))
        val network = own and mask
        val size = 1 shl (32 - prefix)
        val out = ArrayList<String>()
        for (i in 1 until size - 1) {
            val a = network + i
            if (a == own) continue
            out.add("${(a shr 24) and 255}.${(a shr 16) and 255}.${(a shr 8) and 255}.${a and 255}")
        }
        return out
    }

    /** A hint only, never proof. Returns null when nothing looks camera-like. */
    fun cameraHint(hostName: String?, openPorts: List<Int>): String? {
        for (port in openPorts) CAMERA_PORTS[port]?.let { return it }
        val name = hostName?.lowercase() ?: return null
        val word = NAME_WORDS.firstOrNull { name.contains(it) } ?: return null
        return "the device name contains \"$word\""
    }

    fun magnitude(x: Float, y: Float, z: Float): Float = kotlin.math.sqrt(x * x + y * y + z * z)

    fun magLevel(baselineUt: Float, nowUt: Float): MagLevel {
        val d = kotlin.math.abs(nowUt - baselineUt)
        return when {
            d >= STRONG_UT -> MagLevel.STRONG
            d >= NOTICEABLE_UT -> MagLevel.NOTICEABLE
            else -> MagLevel.NORMAL
        }
    }

    val ROOM_CHECKLIST = listOf(
        "Look at smoke detectors, clocks, USB chargers, plug sockets, air vents and plants that point at the bed or bathroom. Any tiny hole that looks like a lens is worth a close look.",
        "Switch the room lights off, then sweep the phone torch slowly over mirrors, TV, set-top box and shelves. A lens gives back a small bright glint.",
        "Mirror test: touch a fingertip to the mirror. A real mirror shows a gap between your finger and its reflection; a two-way mirror shows none.",
        "Check what is plugged in. Unplug items you do not need and look behind them.",
        "Look at the Wi-Fi device list below. Unknown devices with a camera hint deserve a question to the hotel or host.",
        "Nothing found does not prove the room is safe. If anything looks wrong, leave, take photos and tell the host, the hotel or the police."
    )
}
