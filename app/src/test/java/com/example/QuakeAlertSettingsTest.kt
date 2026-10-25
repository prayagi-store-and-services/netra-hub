package com.example

import android.app.Application
import com.example.data.engine.QuakeAlertSettings
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(com.example.OwnedProcessTestRunner::class)
@Config(sdk = [34], application = Application::class)
class QuakeAlertSettingsTest {
 @Test fun defaultPreservesExistingRadiusAndMagnitude() {
  assertEquals(200, QuakeAlertSettings().distanceKm)
  assertEquals(2.5, QuakeAlertSettings().minimumMagnitude, 0.0)
 }
 @Test fun offRejectsAllEvents() { assertFalse(QuakeAlertSettings(enabled = false).accepts(1.0, 8.0)) }
 @Test fun boundariesAreInclusive() {
  val s = QuakeAlertSettings(distanceKm = 250, minimumMagnitude = 4.0)
  assertTrue(s.accepts(250.0, 4.0)); assertFalse(s.accepts(250.1, 4.0)); assertFalse(s.accepts(1.0, 3.99))
  assertFalse(s.accepts(Double.NaN, 5.0)); assertFalse(s.accepts(1.0, Double.NaN))
 }
 @Test fun invalidValuesFallBackWithoutExpandingScope() {
  assertEquals(QuakeAlertSettings(), QuakeAlertSettings.validated(true, Int.MAX_VALUE, Double.NaN))
  assertEquals(listOf(100,200,250,500,1000), QuakeAlertSettings.distances)
  assertEquals(12, QuakeAlertSettings.magnitudes.size)
 }
 @Test fun selectionsPersistOnlyInDevicePreferences() {
  val ctx = RuntimeEnvironment.getApplication()
  val selected = QuakeAlertSettings(false, 1000, 5.5)
  QuakeAlertSettings.write(ctx, selected)
  assertEquals(selected, QuakeAlertSettings.read(ctx))
  QuakeAlertSettings.write(ctx, QuakeAlertSettings())
 }
}
