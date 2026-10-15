package com.example
import android.app.Application
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.data.engine.QuakeAlertSettings
import com.example.ui.screens.QuakeFilterControls
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h800dp-xhdpi", sdk = [34], application = Application::class)
class QuakeFilterScreenshotTest {
 @get:Rule val compose = createComposeRule()
 private fun show() { compose.setContent { var state by remember { mutableStateOf(QuakeAlertSettings()) }
  MaterialTheme { Surface(Modifier.fillMaxSize()) { QuakeFilterControls(state, { state = it }) } }
 } }
 @Test fun controlsAndStoredSelection() {
  show()
  compose.onNodeWithText("Maximum distance: 200 km").performClick()
  compose.onNodeWithText("250 km").performClick()
  compose.onNodeWithText("Maximum distance: 250 km").assertIsDisplayed()
  compose.onRoot().captureRoboImage(filePath = "build/outputs/quake-filters.png")
 }
 @Test fun distanceDialog() { show(); compose.onNodeWithText("Maximum distance: 200 km").performClick()
  compose.onNode(isDialog()).captureRoboImage(filePath = "build/outputs/quake-distance-dialog.png") }
 @Test fun magnitudeDialog() { show(); compose.onNodeWithText("Minimum magnitude: 2.5").performClick()
  compose.onNode(isDialog()).captureRoboImage(filePath = "build/outputs/quake-magnitude-dialog.png") }
}
