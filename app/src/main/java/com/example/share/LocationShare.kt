package com.example.share

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Builds the text for one-tap location sharing. It is a snapshot of one fix, not a live link. */
object LocationShare {
    /** A fix counts only when it has real coordinates. 0,0 is the "no fix" default and is refused. */
    fun hasFix(lat: Double, lon: Double, timestampMs: Long): Boolean =
        timestampMs > 0L && !(lat == 0.0 && lon == 0.0) && lat in -90.0..90.0 && lon in -180.0..180.0

    fun mapsLink(lat: Double, lon: Double): String =
        "https://maps.google.com/?q=" + "%.6f".format(Locale.US, lat) + "," + "%.6f".format(Locale.US, lon)

    /** Null when there is no real fix, so nothing made-up is ever shared. */
    fun message(lat: Double, lon: Double, accuracyM: Float, timestampMs: Long): String? {
        if (!hasFix(lat, lon, timestampMs)) return null
        val fmt = SimpleDateFormat("dd MM yyyy HH:mm:ss", Locale.US)
        val acc = if (accuracyM > 0f) "about " + accuracyM.toInt() + " m" else "Unavailable"
        return "My location from Netra Hub: " + mapsLink(lat, lon) +
            "\nAccuracy: " + acc + ". Fix taken at " + fmt.format(Date(timestampMs)) +
            ".\nThis is a one-time snapshot, not a live track."
    }
}
