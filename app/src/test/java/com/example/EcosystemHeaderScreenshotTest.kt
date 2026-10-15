package com.example
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import com.example.ui.NetraVersionHeader
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h800dp-xhdpi", sdk = [34], application = android.app.Application::class)
class EcosystemHeaderScreenshotTest {
 @get:Rule val compose = createComposeRule()
 @Test fun versionNamesFamily() {
  compose.setContent { MaterialTheme { Surface { NetraVersionHeader("1.4.15", java.util.Date(1792067400000L)) } } }
  compose.onNodeWithText("v1.4.15 | Netra Ecosystem").assertIsDisplayed()
  compose.onRoot().captureRoboImage(filePath = "build/outputs/ecosystem-version-header.png")
 }
 @Test fun liteVersionNamesFamily() {
  compose.setContent { MaterialTheme { Surface { NetraVersionHeader("1.4.15", java.util.Date(1792067400000L), true) } } }
  compose.onNodeWithText("v1.4.15 | Netra Ecosystem").assertIsDisplayed()
  compose.onNodeWithText("Netra Hub (lite)").assertIsDisplayed()
  compose.onRoot().captureRoboImage(filePath = "build/outputs/ecosystem-version-header-lite.png")
 }
}
