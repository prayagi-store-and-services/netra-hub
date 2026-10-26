package com.example.monitoring

import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
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

@RunWith(com.example.OwnedProcessTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w320dp-h800dp-xxhdpi", sdk = [34], application = android.app.Application::class)
class MonitoringControlScreenshotTest {
    @get:Rule val compose = createComposeRule()
    @Test fun unavailableBiometric320() {
        compose.setContent { NetraTheme {
            androidx.compose.material3.Surface(color = com.example.ui.theme.BentoCardBg) {
            androidx.compose.foundation.layout.Column(modifier = androidx.compose.ui.Modifier.padding(16.dp)) {
            com.example.ui.screens.SecurityFeatureItemRow(
                feature = com.example.data.model.SecurityFeature(
                    id = "biometric", name = "Biometric Authentication",
                    category = com.example.data.model.FeatureCategory.B,
                    isMandatory = false, scoreWeight = 1,
                    status = com.example.data.model.FeatureStatus.UNKNOWN
                ), onItemClick = {}, onToggle = {}
            )
            }
            }
        } }
        compose.onNodeWithText("Unavailable").assertExists()
        compose.onRoot().captureRoboImage(filePath = "src/test/screenshots/monitoring_biometric_unavailable_320.png")
    }
    @Test fun centralUpdates320() {
        compose.setContent { NetraTheme { com.example.update.UpdateCheckCard() } }
        compose.onNodeWithText("Updates are available only through Netra Ecosystem. This app does not download or install updates.").assertExists()
        compose.onRoot().captureRoboImage(filePath = "src/test/screenshots/monitoring_central_updates_320.png")
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
