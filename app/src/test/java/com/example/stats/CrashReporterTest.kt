package com.example.stats

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CrashReporterTest {
    @Test fun traceKeepsClassNamesButDropsExceptionMessage() {
        val t = IllegalStateException("secret-user-text 555-1234")
        val s = CrashReporter.sanitize(t)
        assertTrue(s.startsWith("java.lang.IllegalStateException"))
        assertFalse(s.contains("secret-user-text"))
    }
    @Test fun causeMessageIsDroppedToo() {
        val s = CrashReporter.sanitize(RuntimeException("outer", IllegalArgumentException("inner-secret")))
        assertTrue(s.contains("Caused by: java.lang.IllegalArgumentException"))
        assertFalse(s.contains("inner-secret"))
    }
    @Test fun acceptedNeedsRealSuccessAnswer() {
        assertTrue(CrashReporter.accepted("{\"success\":\"true\",\"message\":\"ok\"}"))
        assertTrue(CrashReporter.accepted("{\"success\":true}"))
        assertFalse(CrashReporter.accepted("{\"success\":\"false\",\"message\":\"Needs Activation\"}"))
        assertFalse(CrashReporter.accepted(""))
    }
}
