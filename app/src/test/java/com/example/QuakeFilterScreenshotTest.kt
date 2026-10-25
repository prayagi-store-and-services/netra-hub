package com.example
import android.app.Application
import androidx.compose.foundation.layout.*
import androidx.compose.ui.unit.dp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.data.engine.QuakeAlertSettings
import com.example.ui.screens.QuakeFilterControls
import com.example.ui.screens.AlertAreaControls
import com.example.data.engine.AlertArea
import com.example.data.engine.SavedAlertArea
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
@RunWith(com.example.OwnedProcessTestRunner::class)
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

 private fun showArea() { compose.setContent { MaterialTheme { Surface(Modifier.fillMaxSize()) { Column(Modifier.padding(16.dp)) { AlertAreaControls() } } } } }
 private val naini = SavedAlertArea(25.43, 81.84, "Naini", "Prayagraj", "Uttar Pradesh", 0L)
 @Test fun alertAreaNoneSaved() { showArea()
  compose.onNodeWithText("Alert area").assertIsDisplayed()
  compose.onNodeWithText("Save my area").assertIsDisplayed()
  compose.onRoot().captureRoboImage(filePath = "build/outputs/quake-area-none.png") }
 @Test fun alertAreaSavedFresh() {
  AlertArea.save(org.robolectric.RuntimeEnvironment.getApplication(), naini.copy(savedAtMillis = System.currentTimeMillis()))
  showArea()
  compose.onNodeWithText("Update area").assertIsDisplayed(); compose.onNodeWithText("Clear area").assertIsDisplayed()
  compose.onRoot().captureRoboImage(filePath = "build/outputs/quake-area-saved.png") }
 @Test fun alertAreaSavedStale() {
  AlertArea.save(org.robolectric.RuntimeEnvironment.getApplication(), naini.copy(savedAtMillis = System.currentTimeMillis() - 20L * 86_400_000L))
  showArea()
  compose.onNodeWithText("If you have moved", substring = true).assertIsDisplayed()
  compose.onRoot().captureRoboImage(filePath = "build/outputs/quake-area-stale.png") }
 @Test fun alertAreaSaveWithoutLocationShowsUnavailableAndClearWorks() {
  showArea()
  compose.onNodeWithText("Save my area").performClick()
  compose.waitUntil(5000) { compose.onAllNodesWithText("Unavailable: area not saved", substring = true).fetchSemanticsNodes().isNotEmpty() }
  compose.onNodeWithText("Save my area").assertIsDisplayed()
  compose.onRoot().captureRoboImage(filePath = "build/outputs/quake-area-unavailable.png") }
 @Test fun alertAreaClearReturnsToNone() {
  AlertArea.save(org.robolectric.RuntimeEnvironment.getApplication(), naini.copy(savedAtMillis = System.currentTimeMillis()))
  showArea()
  compose.onNodeWithText("Clear area").performClick()
  compose.onNodeWithText("Save my area").assertIsDisplayed()
  compose.onNodeWithText("Saved area cleared", substring = true).assertIsDisplayed()
  compose.onRoot().captureRoboImage(filePath = "build/outputs/quake-area-cleared.png") }

 @Test fun alertAreaChoiceSwitchesBetweenSavedAndCurrent() {
  val app = org.robolectric.RuntimeEnvironment.getApplication()
  AlertArea.save(app, naini.copy(savedAtMillis = System.currentTimeMillis()))
  showArea()
  compose.onNodeWithText("Automatic official alert checks use: your saved area", substring = true).assertIsDisplayed()
  org.junit.Assert.assertNotNull(AlertArea.asLocation(app))
  compose.onNodeWithText("Use current location each check").performClick()
  compose.onNodeWithText("Automatic official alert checks use: your current location", substring = true).assertIsDisplayed()
  compose.onNodeWithText("Automatic checks now read your current location", substring = true).assertIsDisplayed()
  org.junit.Assert.assertFalse(AlertArea.useSaved(app)); org.junit.Assert.assertNull(AlertArea.asLocation(app))
  org.junit.Assert.assertNotNull(AlertArea.read(app)) // saved area is kept, just not used
  compose.onRoot().captureRoboImage(filePath = "build/outputs/quake-area-choice-current.png")
  compose.onNodeWithText("Use saved area").performClick()
  compose.onNodeWithText("Automatic checks now use your saved area.").assertIsDisplayed()
  org.junit.Assert.assertTrue(AlertArea.useSaved(app)); org.junit.Assert.assertNotNull(AlertArea.asLocation(app))
  compose.onRoot().captureRoboImage(filePath = "build/outputs/quake-area-choice-saved.png") }
 @Test fun alertAreaDefaultIsCurrentLocationAndFailedFixChangesNothing() {
  val app = org.robolectric.RuntimeEnvironment.getApplication()
  org.junit.Assert.assertFalse(AlertArea.useSaved(app)); org.junit.Assert.assertNull(AlertArea.asLocation(app))
  showArea()
  compose.onNodeWithText("Automatic official alert checks use: your current location", substring = true).assertIsDisplayed()
  compose.onNodeWithText("Save my area").performClick()
  compose.waitUntil(5000) { compose.onAllNodesWithText("nothing changed", substring = true).fetchSemanticsNodes().isNotEmpty() }
  org.junit.Assert.assertFalse(AlertArea.useSaved(app)); org.junit.Assert.assertNull(AlertArea.read(app))
  compose.onNodeWithText("Automatic official alert checks use: your current location", substring = true).assertIsDisplayed()
  compose.onRoot().captureRoboImage(filePath = "build/outputs/quake-area-choice-failed-fix.png") }
 @Test fun alertAreaStaleButCurrentModeDoesNotClaimMatching() {
  val app = org.robolectric.RuntimeEnvironment.getApplication()
  AlertArea.save(app, naini.copy(savedAtMillis = System.currentTimeMillis() - 20L * 86_400_000L))
  AlertArea.setUseSaved(app, false)
  showArea()
  compose.onNodeWithText("not in use right now", substring = true).assertIsDisplayed()
  compose.onAllNodesWithText("matched to this saved area", substring = true).assertCountEquals(0)
  compose.onNodeWithText("Automatic official alert checks use: your current location", substring = true).assertIsDisplayed()
  compose.onRoot().captureRoboImage(filePath = "build/outputs/quake-area-stale-current.png") }
}
