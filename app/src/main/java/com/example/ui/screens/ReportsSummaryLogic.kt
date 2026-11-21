package com.example.ui.screens

import com.example.data.db.SafetyEventEntity
import com.example.driving.DrivingLog

/**
 * Pure aggregation behind the Reports summary screen (owner UI reference 9 Nov 2026).
 * All inputs are real stored data: safety events from Room, driving transitions from the
 * driving journal. No distance figure exists anywhere in the app, so no distance row is
 * offered here - inventing one would be simulated data.
 */
object ReportsSummaryLogic {

    data class DrivingStats(
        val trips: Int,
        val stops: Int,
        val drivingMs: Long,
        /** True when the newest transition is STARTED: the phone believes driving is still on. */
        val ongoing: Boolean
    )

    data class Summary(
        val driving: DrivingStats,
        val safetyEvents: Int,
        val sosEvents: Int,
        val weatherEvents: Int,
        val quakeEvents: Int
    )

    /**
     * Driving time and counts inside [startMs, nowMs].
     * A trip that started before the window and is still open counts from the window start
     * (clipped, never extrapolated before it). ENDED without a known open start is counted
     * as a stop but adds no driving time, because the start moment is unknown.
     */
    fun drivingStats(entries: List<DrivingLog.Entry>, startMs: Long, nowMs: Long): DrivingStats {
        var trips = 0
        var stops = 0
        var drivingMs = 0L
        var openStart: Long? = null

        val before = entries.filter { it.at < startMs }.maxByOrNull { it.at }
        if (before?.kind == DrivingLog.STARTED) openStart = startMs

        entries.filter { it.at in startMs..nowMs }.sortedBy { it.at }.forEach { e ->
            when (e.kind) {
                DrivingLog.STARTED -> if (openStart == null) { openStart = e.at; trips++ }
                DrivingLog.ENDED -> {
                    val s = openStart
                    if (s != null) drivingMs += (e.at - s).coerceAtLeast(0)
                    openStart = null
                    stops++
                }
            }
        }
        val ongoing = openStart != null
        if (ongoing) drivingMs += (nowMs - (openStart ?: nowMs)).coerceAtLeast(0)
        return DrivingStats(trips, stops, drivingMs, ongoing)
    }

    /** Event counts reuse the exact classifier the Alerts screen filters with. */
    fun summary(events: List<SafetyEventEntity>, driving: List<DrivingLog.Entry>, startMs: Long, nowMs: Long): Summary {
        val inWindow = events.filter { it.timestamp in startMs..nowMs }
        val counts = inWindow.groupingBy { alertFilterOf(it.domain, it.eventType) }.eachCount()
        return Summary(
            driving = drivingStats(driving, startMs, nowMs),
            safetyEvents = inWindow.size,
            sosEvents = counts["SOS"] ?: 0,
            weatherEvents = counts["Weather"] ?: 0,
            quakeEvents = counts["Earthquake"] ?: 0
        )
    }

    /** "1 h 24 min" / "42 min". Sub-minute shows "<1 min"; zero shows "0 min". */
    fun formatDuration(ms: Long): String {
        if (ms <= 0L) return "0 min"
        val totalMin = ms / 60_000L
        if (totalMin == 0L) return "<1 min"
        val h = totalMin / 60
        val m = totalMin % 60
        return if (h > 0) "$h h $m min" else "$m min"
    }
}
