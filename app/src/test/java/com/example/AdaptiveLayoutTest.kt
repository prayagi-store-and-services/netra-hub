package com.example

import androidx.compose.ui.unit.dp
import com.example.ui.utils.adaptiveContentMaxWidthFor
import com.example.ui.utils.adaptiveFontScaleFor
import com.example.ui.utils.AdaptiveWidthClass
import com.example.ui.utils.adaptiveWidthClassFor
import org.junit.Assert.assertEquals
import org.junit.Test

class AdaptiveLayoutTest {

    @Test
    fun `phone widths stay compact`() {
        assertEquals(AdaptiveWidthClass.COMPACT, adaptiveWidthClassFor(320.dp))
        assertEquals(AdaptiveWidthClass.COMPACT, adaptiveWidthClassFor(360.dp))
        assertEquals(AdaptiveWidthClass.COMPACT, adaptiveWidthClassFor(599.dp))
    }

    @Test
    fun `small tablets are medium`() {
        assertEquals(AdaptiveWidthClass.MEDIUM, adaptiveWidthClassFor(600.dp))
        assertEquals(AdaptiveWidthClass.MEDIUM, adaptiveWidthClassFor(700.dp))
        assertEquals(AdaptiveWidthClass.MEDIUM, adaptiveWidthClassFor(839.dp))
    }

    @Test
    fun `wide screens are expanded`() {
        assertEquals(AdaptiveWidthClass.EXPANDED, adaptiveWidthClassFor(840.dp))
        assertEquals(AdaptiveWidthClass.EXPANDED, adaptiveWidthClassFor(1280.dp))
        assertEquals(AdaptiveWidthClass.EXPANDED, adaptiveWidthClassFor(1920.dp))
    }

    @Test
    fun `content cap matches class`() {
        assertEquals(360.dp, adaptiveContentMaxWidthFor(360.dp))
        assertEquals(600.dp, adaptiveContentMaxWidthFor(700.dp))
        assertEquals(840.dp, adaptiveContentMaxWidthFor(1280.dp))
    }

    @Test
    fun `font scale is unchanged on phones and larger on wide screens`() {
        assertEquals(1.00f, adaptiveFontScaleFor(360), 0.001f)
        assertEquals(1.00f, adaptiveFontScaleFor(599), 0.001f)
        assertEquals(1.10f, adaptiveFontScaleFor(600), 0.001f)
        assertEquals(1.20f, adaptiveFontScaleFor(840), 0.001f)
        assertEquals(1.20f, adaptiveFontScaleFor(1920), 0.001f)
    }
}
