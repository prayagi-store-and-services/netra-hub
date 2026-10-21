package com.example.driving

/** Speed from two GPS fixes, for phones whose location provider sends positions without a speed value. */
object DerivedSpeed {
    const val MAX_PLAUSIBLE_KMH = 200f

    fun kmh(lat1: Double, lon1: Double, t1: Long, lat2: Double, lon2: Double, t2: Long, accuracyM: Float): Float? {
        val dt = (t2 - t1) / 1000.0
        if (dt < 2.0 || dt > 60.0) return null          // too close together or too old to say anything
        if (accuracyM <= 0f || accuracyM > 50f) return null // a coarse fix makes a made-up speed
        val r = 6371000.0
        val p1 = Math.toRadians(lat1); val p2 = Math.toRadians(lat2)
        val dp = p2 - p1; val dl = Math.toRadians(lon2 - lon1)
        val a = Math.sin(dp / 2) * Math.sin(dp / 2) + Math.cos(p1) * Math.cos(p2) * Math.sin(dl / 2) * Math.sin(dl / 2)
        val dist = 2 * r * Math.asin(Math.min(1.0, Math.sqrt(a)))
        if (dist < accuracyM) return 0f                   // inside the GPS noise: treat as standing
        val kmh = (dist / dt * 3.6).toFloat()
        return if (kmh > MAX_PLAUSIBLE_KMH) null else kmh // a GPS jump, not a real speed
    }
}
