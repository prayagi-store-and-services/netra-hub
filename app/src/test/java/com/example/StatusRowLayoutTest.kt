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
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.unit.dp
import com.example.data.service.LocationAccess
import com.example.ui.screens.StatusRow
import com.example.ui.theme.NetraTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
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

    /** Saves the rendered card as a PNG and also prints it as base64 between markers so the image can be read from the CI log. */
    private fun shot(name: String) {
        val bmp = rule.onRoot().captureToImage().asAndroidBitmap()
        val out = java.io.ByteArrayOutputStream()
        bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
        val bytes = out.toByteArray()
        val dir = java.io.File("build/ui-shots"); dir.mkdirs()
        java.io.File(dir, name).writeBytes(bytes)
        println("UISHOT_BEGIN " + name + " " + bmp.width + "x" + bmp.height)
        android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP).chunked(180).forEach { println("UISHOT " + it) }
        println("UISHOT_END " + name)
    }

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
        shot("status_rows_320dp.png")
    }

    @Test
    fun narrowAndWideBothRender() {
        card(411)
        shot("status_rows_411dp.png")
    }

    @Test
    fun placeTextHasNoDoubledUnavailable() {
        assertEquals("Unavailable", LocationAccess.placeText("Location Unavailable", "Location Unavailable", "Location Unavailable"))
        assertEquals("Naini, Uttar Pradesh", LocationAccess.placeText("Naini", "Naini", "Uttar Pradesh"))
        assertEquals("Prayagraj, Uttar Pradesh", LocationAccess.placeText("Prayagraj", "Prayagraj", "Uttar Pradesh"))
        assertEquals("Uttar Pradesh", LocationAccess.placeText("Location Unavailable", "Location Unavailable", "Uttar Pradesh"))
    }
}
