package com.example.monitoring

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.assertTextEquals
import com.example.ui.components.MonitoringControlContent
import com.example.ui.theme.NetraTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w320dp-h800dp-xxhdpi", sdk = [34], application = android.app.Application::class)
class MonitoringControlScreenshotTest {
    @get:Rule val compose = createComposeRule()
    @Test fun humanSafetyHold320() {
        compose.setContent { NetraTheme { com.example.update.HumanSafetyHoldCard() } }
        compose.onNodeWithText(com.example.update.HumanSafetyHold.MESSAGE).assertExists()
        compose.onNodeWithText("Download and update paused").assertIsNotEnabled()
        compose.onRoot().captureRoboImage(filePath = "src/test/screenshots/monitoring_human_safety_hold_320.png")
    }
    @Test fun pausedControl320() {
        compose.setContent { NetraTheme { MonitoringControlContent(enabled = false, onAction = {}) } }
        compose.onNodeWithTag("monitoring_state").assertTextEquals("Sensor monitoring: paused")
        compose.onRoot().captureRoboImage(filePath = "src/test/screenshots/monitoring_paused_320.png")
    }
    @Test fun enabledControl320() {
        compose.setContent { NetraTheme { MonitoringControlContent(enabled = true, onAction = {}) } }
        compose.onNodeWithTag("monitoring_state").assertTextEquals("Sensor monitoring: enabled by you")
        compose.onRoot().captureRoboImage(filePath = "src/test/screenshots/monitoring_enabled_320.png")
    }
}
