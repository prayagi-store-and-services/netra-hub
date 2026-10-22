package com.example.sos.alert

data class AlertPayload(
    val alertId: String,
    val timestampMs: Long,
    val trigger: String,
    val lat: Double?, val lon: Double?, val accuracyM: Float?, val fixTimeMs: Long?,
    val mapLink: String?,
    val fallbackNote: String?,
    val name: String,
    val ageOrDob: String?,
    /** Included only when the user consented. */
    val bloodGroup: String?,
    val contacts: List<String>,
    val batteryPct: Int?,
    val appVersion: String,
    val deviceModel: String,
    val isCancel: Boolean = false,
    val isDuplicate: Boolean = false
) {
    init { require(contacts.size in 3..5) { "3 to 5 contacts are required" } }

    companion object {
        fun withConsent(p: AlertPayload, bloodGroupConsent: Boolean) = if (bloodGroupConsent) p else p.copy(bloodGroup = null)
    }
}
