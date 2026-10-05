package com.example

import com.example.push.PushAlerts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Test

class PushAlertsTest {
    @Test fun topicsUseStateAndDistrict() {
        assertEquals(listOf("in_uttar_pradesh", "in_uttar_pradesh__gautam_buddha_nagar"), PushAlerts.topicsFor("Uttar Pradesh", "Gautam Buddha Nagar"))
    }
    @Test fun unknownStateGivesNoTopics() {
        assertEquals(emptyList<String>(), PushAlerts.topicsFor("Unavailable", "Pune"))
    }
    @Test fun unknownDistrictKeepsStateTopic() {
        assertEquals(listOf("in_goa"), PushAlerts.topicsFor("Goa", "Unavailable"))
    }
    @Test fun parseRequiresFieldsAndRejectsExpired() {
        assertNull(PushAlerts.parse(mapOf("id" to "1", "title" to "T"), 1000L))
        assertNull(PushAlerts.parse(mapOf("id" to "1", "title" to "T", "body" to "B", "expires" to "500"), 1000L))
        assertNotNull(PushAlerts.parse(mapOf("id" to "1", "title" to "T", "body" to "B", "expires" to "5000"), 1000L))
        assertNotNull(PushAlerts.parse(mapOf("id" to "1", "title" to "T", "body" to "B"), 1000L))
    }
}
