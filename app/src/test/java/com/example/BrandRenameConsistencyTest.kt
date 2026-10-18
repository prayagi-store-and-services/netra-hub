package com.example

import org.junit.Assert.assertEquals
import org.junit.Test

/** The app is Netra Human Safety again everywhere the brand is read (17 Oct 2026 decision). */
class BrandRenameConsistencyTest {
    @Test fun currentNameIsHumanSafety() {
        assertEquals("Netra Human Safety", Brand.name)
        assertEquals("Netra Human Safety", Brand.nameAt(System.currentTimeMillis()))
        assertEquals(Brand.OLD_NAME, Brand.NEW_NAME)
    }
}
