package com.example.data.engine

import android.content.Context
import com.example.data.db.NetraDatabase
import com.example.data.db.SafetyEventEntity
import com.example.data.service.OfficialLocationContextManager
import com.example.data.service.LocationContextInfo
import com.example.util.LoggingManager
import com.example.util.NetraNotificationManager
import com.example.util.NetraTtsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class OfficialWarningInfo(
    val alertId: String = "",
    val warningType: String = "No Active Warning",
    val severity: String = "UNKNOWN", // INFO, WATCH, WARNING, CRITICAL, UNKNOWN
    val headline: String = "",
    val description: String = "",
    val startTime: String = "",
    val endTime: String = "",
    val issuingAuthority: String = "Source unavailable",
    val sourceType: String = "THIRD_PARTY", // OFFICIAL_VERIFIED, THIRD_PARTY, UNVERIFIED
    val verificationStatus: String = "VERIFIED_THIRD_PARTY", // OFFICIAL_VERIFIED, VERIFIED_THIRD_PARTY, UNVERIFIED
    val locationRelevance: String = "UNKNOWN_RELEVANCE", // VERIFIED_MATCH, NO_MATCH, UNKNOWN_RELEVANCE
    val lifecycleState: String = "INFORMATIONAL", // ACTIVE, UPDATED, EXPIRED, CANCELLED, INFORMATIONAL
    val checkStatusState: String = "CHECK_SUCCESS_NO_ACTIVE_WARNING", // LOCATION_UNAVAILABLE, LOCATION_STALE, SOURCE_UNAVAILABLE, CHECK_FAILED, CHECK_SUCCESS_NO_ACTIVE_WARNING, VERIFIED_ACTIVE_WARNING, UNVERIFIED_INFORMATION
    val affectedArea: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val isAvailable: Boolean = false,
    val source: String = "Weather Provider (Third-Party)"
)

class OfficialEmergencyWarningManager(private val context: Context) {
    private val db = NetraDatabase.getInstance(context)
    private val locationManager = OfficialLocationContextManager(context)
    private val notificationManager = NetraNotificationManager(context)
    private val ttsManager = NetraTtsManager(context)

    private val _activeWarning = MutableStateFlow(OfficialWarningInfo())
    val activeWarning: StateFlow<OfficialWarningInfo> = _activeWarning.asStateFlow()

    private val _lastCheckTimestamp = MutableStateFlow(0L)
    val lastCheckTimestamp: StateFlow<Long> = _lastCheckTimestamp.asStateFlow()

    private val _checkStatus = MutableStateFlow("Idle")
    val checkStatus: StateFlow<String> = _checkStatus.asStateFlow()

    suspend fun checkOfficialWarnings(): OfficialWarningInfo = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        // 30-minute TTL cache check for ultra-low battery / network conservation
        if (now - _lastCheckTimestamp.value < 30 * 60 * 1000L && _activeWarning.value.checkStatusState.isNotEmpty() && _activeWarning.value.checkStatusState != "Idle") {
            return@withContext _activeWarning.value
        }

        // No official alert feed is connected. We do not send the user's coordinates to any third party.
        val stateInfo = OfficialWarningInfo(
            warningType = "Warning Source Unavailable",
            headline = "No official alert feed is connected, so this app cannot tell you about warnings. Check official sources.",
            checkStatusState = "SOURCE_UNAVAILABLE",
            issuingAuthority = "Unavailable"
        )
        _activeWarning.value = stateInfo
        _checkStatus.value = "Unavailable: no official alert feed"
        _lastCheckTimestamp.value = now
        return@withContext stateInfo
    }

    private fun mapSeverity(severityStr: String, eventStr: String): String {
        val s = severityStr.uppercase()
        val e = eventStr.uppercase()
        return when {
            s.contains("CRITICAL") || s.contains("SEVERE") || e.contains("EXTREME") || e.contains("RED") -> "CRITICAL"
            s.contains("WARNING") || s.contains("ORANGE") || e.contains("WARNING") -> "WARNING"
            s.contains("WATCH") || s.contains("YELLOW") || e.contains("WATCH") -> "WATCH"
            s.contains("INFO") || s.contains("ADVISORY") || e.contains("ADVISORY") -> "INFO"
            else -> "UNKNOWN"
        }
    }

    private fun determineLocationRelevance(warning: OfficialWarningInfo, loc: LocationContextInfo): String {
        val affected = warning.affectedArea.lowercase().trim()
        val locality = loc.locality.lowercase().trim()
        val district = loc.district.lowercase().trim()
        val state = loc.state.lowercase().trim()

        if (affected.isEmpty()) {
            return "UNKNOWN_RELEVANCE"
        }

        val matchesCity = locality.isNotEmpty() && locality != "location unavailable" && affected.contains(locality)
        val matchesDistrict = district.isNotEmpty() && district != "location unavailable" && affected.contains(district)
        val matchesState = state.isNotEmpty() && state != "location unavailable" && affected.contains(state) &&
            (affected.contains("state") || affected.contains("all") || affected.length < 100)
        val matchesCoordsFallback = loc.locationStatus == "COORDINATES_AVAILABLE" && (affected.contains("country") || affected.contains("national") || affected.contains("region") || affected.isEmpty() || affected.contains("all"))

        return if (matchesCity || matchesDistrict || matchesState || matchesCoordsFallback) {
            "VERIFIED_MATCH"
        } else {
            "NO_MATCH"
        }
    }

    private suspend fun processMatchedWarning(warning: OfficialWarningInfo, loc: LocationContextInfo) {
        val dao = db.safetyEventDao()
        val existing = dao.getEventByEventId(warning.alertId)

        if (existing == null) {
            val entity = SafetyEventEntity(
                eventId = warning.alertId,
                domain = "OFFICIAL_WARNING",
                lifecycleState = "ACTIVE",
                timestamp = warning.timestamp,
                riskLevel = when (warning.severity) {
                    "CRITICAL" -> "EMERGENCY"
                    "WARNING" -> "WARNING"
                    else -> "ATTENTION"
                },
                riskScore = if (warning.severity == "CRITICAL") 90 else if (warning.severity == "WARNING") 75 else 50,
                eventType = warning.warningType,
                title = warning.warningType,
                description = warning.headline,
                aiRecommendation = "Source: ${warning.source} (${warning.issuingAuthority}). Valid: ${warning.startTime} to ${warning.endTime}.",
                isVerifiedHardwareEvent = false,
                moduleName = "OfficialEmergencyWarningManager",
                severity = warning.severity,
                gpsLocation = "${loc.locality}, ${loc.state} (${warning.source})"
            )
            dao.insertEvent(entity)

            notificationManager.sendEmergencyAlert(
                title = "${warning.warningType} (${warning.source})",
                message = "${warning.headline}\nValid: ${warning.startTime} – ${warning.endTime}\nSource: ${warning.issuingAuthority}",
                riskLevel = when (warning.severity) {
                    "CRITICAL" -> com.example.data.model.SafetyRiskLevel.EMERGENCY
                    "WARNING" -> com.example.data.model.SafetyRiskLevel.WARNING
                    else -> com.example.data.model.SafetyRiskLevel.ATTENTION
                }
            )

            val announcementText = "आपके क्षेत्र के लिए एक विश्वसनीय weather service (${warning.source}) ने ${warning.warningType} की चेतावनी रिपोर्ट की है। कृपया application में विवरण देखें।"
            ttsManager.speakAlert(announcementText, isCriticalSafety = warning.severity == "CRITICAL" || warning.severity == "WARNING")

            LoggingManager.critical("EmergencyWarning", "NEW_WARNING_DETECTED", "New warning detected: ${warning.warningType}", "Source: ${warning.source}, Severity: ${warning.severity}")
        }
    }
}
