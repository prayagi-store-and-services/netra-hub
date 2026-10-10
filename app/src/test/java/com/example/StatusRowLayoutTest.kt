package com.example

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.example.data.service.LocationAccess
import com.example.ui.screens.StatusRow
import com.example.ui.theme.NetraTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
class StatusRowLayoutTest {

    @get:Rule val rule = createComposeRule()

    private fun card(widthDp: Int) {
        rule.setContent {
            NetraTheme {
                Column(Modifier.width(widthDp.dp).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatusRow("Current Location", LocationAccess.placeText("Location Unavailable", "Location Unavailable", "Location Unavailable"))
                    StatusRow("Location Source", "Unavailable")
                    StatusRow("Last Successful Location Update", "Unavailable")
                    StatusRow("Last Official Alert Check", "10/10/2026 10:01:57 (Unavailable: no official alert feed)")
                    StatusRow("Active Warning", "None / Unavailable")
                    StatusRow("Source", "Unavailable")
                }
            }
        }
    }

    @Test
    fun labelsAndValuesNeverOverlapAt320dp() {
        card(320)
        rule.onNodeWithText("Last Successful Location Update").assertIsDisplayed()
        val label = rule.onNodeWithText("Last Official Alert Check").getUnclippedBoundsInRoot()
        val value = rule.onNodeWithText("10/10/2026 10:01:57 (Unavailable: no official alert feed)").getUnclippedBoundsInRoot()
        assertTrue("value must start below its label", value.top >= label.bottom)
        rule.onRoot().captureRoboImage(filePath = "build/ui-shots/status_rows_320dp.png")
    }

    @Test
    fun narrowAndWideBothRender() {
        card(411)
        rule.onRoot().captureRoboImage(filePath = "build/ui-shots/status_rows_411dp.png")
    }

    @Test
    fun placeTextHasNoDoubledUnavailable() {
        assertEquals("Unavailable", LocationAccess.placeText("Location Unavailable", "Location Unavailable", "Location Unavailable"))
        assertEquals("Naini, Uttar Pradesh", LocationAccess.placeText("Naini", "Naini", "Uttar Pradesh"))
        assertEquals("Prayagraj, Uttar Pradesh", LocationAccess.placeText("Prayagraj", "Prayagraj", "Uttar Pradesh"))
        assertEquals("Uttar Pradesh", LocationAccess.placeText("Location Unavailable", "Location Unavailable", "Uttar Pradesh"))
    }
}
