package com.example

import com.example.data.service.LocationAccess
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocationAccessTest {
    @Test
    fun timeoutWithoutBackgroundPermissionSaysWhatToDo() {
        val t = LocationAccess.failureText("LOCATION_TIMEOUT", false)
        assertEquals(LocationAccess.NEEDS_BACKGROUND_TEXT, t)
        assertTrue(t.contains("Allow all the time"))
        assertFalse(t.contains("LOCATION_TIMEOUT"))
    }

    @Test
    fun timeoutWithBackgroundPermissionIsNotBlamedOnPermission() {
        val t = LocationAccess.failureText("LOCATION_TIMEOUT", true)
        assertFalse(t.contains("Allow all the time"))
        assertTrue(t.contains("no recent location fix"))
    }

    @Test
    fun otherStatusesKeepTheirOwnReason() {
        assertTrue(LocationAccess.failureText("PERMISSION_NOT_GRANTED", true).contains("permission is off"))
        assertTrue(LocationAccess.failureText("PROVIDER_DISABLED", true).contains("switched off"))
    }
}
