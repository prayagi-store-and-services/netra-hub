package com.example.data.engine

import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

internal const val IMD_WARNING_URL = "https://reactjs.imd.gov.in/geoserver/ows?service=WFS&version=1.1.0&request=GetFeature&typename=imd:subdiv_warnings_now&srsname=EPSG:4326&outputFormat=application/json"
internal data class ImdWarningReport(val area: String, val date: String, val colour: String, val hazards: String) {
    val audible: Boolean get() = colour in setOf("Yellow", "Orange", "Red")
    val identity: String get() = area
    fun text() = "IMD subdivision warning: " + area + ". Date: " + date + ". Level: " + colour + ". " + hazards + ". Source: IMD public subdivision warnings. Not a district nowcast or hourly forecast."
}
internal fun imdDate(now: Long): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = TimeZone.getTimeZone("Asia/Kolkata") }.format(Date(now))
internal fun imdColour(code: Int): String? = when (code) { 1 -> "Red"; 2 -> "Orange"; 3 -> "Yellow"; 4 -> "Green"; else -> null }
internal fun imdShouldSpeak(previousColour: String?, report: ImdWarningReport): Boolean = report.audible && previousColour != report.colour
private fun imdRingContains(ring: org.json.JSONArray, lat: Double, lon: Double): Boolean {
    if (ring.length() < 4) return false
    val points = (0 until ring.length()).map { i -> ring.getJSONArray(i).let { it.getDouble(1) to it.getDouble(0) } }
    if (points.any { !it.first.isFinite() || !it.second.isFinite() }) return false
    return pointInPolygon(lat, lon, points)
}
private fun imdPolygonContains(poly: org.json.JSONArray, lat: Double, lon: Double): Boolean {
    if (poly.length() == 0 || !imdRingContains(poly.getJSONArray(0), lat, lon)) return false
    return (1 until poly.length()).none { imdRingContains(poly.getJSONArray(it), lat, lon) }
}
/** Geometry and current Indian date required. Never guess a subdivision from a state name. */
internal fun parseImdWarning(json: String, latitude: Double, longitude: Double, now: Long): ImdWarningReport? {
    if (!plausibleCoords(latitude, longitude)) return null
    val root = JSONObject(json)
    require(root.getString("type") == "FeatureCollection")
    val features = root.getJSONArray("features")
    val names = mapOf("1" to "No warning", "2" to "Heavy rain", "3" to "Heavy snow", "4" to "Thunderstorm and lightning", "5" to "Hailstorm", "6" to "Dust storm", "7" to "Dust raising winds", "8" to "Strong surface winds", "9" to "Heat wave", "91" to "Severe heat wave", "10" to "Hot day", "11" to "Warm night", "12" to "Cold wave", "121" to "Severe cold wave", "13" to "Cold day", "131" to "Severe cold day", "14" to "Ground frost", "15" to "Fog", "151" to "Dense fog", "152" to "Very dense fog", "16" to "Very heavy rain", "17" to "Extremely heavy rain", "18" to "Hot and humid")
    for (i in 0 until features.length()) {
        val f = features.getJSONObject(i); val g = f.getJSONObject("geometry"); val coords = g.getJSONArray("coordinates")
        val inside = when (g.getString("type")) {
            "MultiPolygon" -> (0 until coords.length()).any { imdPolygonContains(coords.getJSONArray(it), latitude, longitude) }
            "Polygon" -> imdPolygonContains(coords, latitude, longitude)
            else -> false
        }
        if (!inside) continue
        val props = f.getJSONObject("properties")
        val date = props.getString("Date")
        if (date != imdDate(now)) return null
        val colour = imdColour(props.getInt("Day1_Color")) ?: return null
        val area = props.getString("SUBDIV").trim().take(100)
        if (area.isBlank()) return null
        val hazards = props.getString("Day_1").split(",").map { it.trim() }.filter { it.isNotEmpty() }.map { code ->
            if (code in setOf("41", "42", "43", "44", "45", "46")) "Thunderstorm and lightning" else names[code] ?: "Source hazard code " + code.take(12)
        }.distinct().joinToString(", ").take(400)
        return ImdWarningReport(area, date, colour, hazards)
    }
    return null
}
