package com.example

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(com.example.OwnedProcessTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers="w360dp-h800dp-xxhdpi", sdk=[34], application=Application::class)
class OfficialWeatherReportUiTest {
    @get:Rule val rule=createAndroidComposeRule<ComponentActivity>()
    @Test fun scopedStoredReportVisibleThenAreaClearInvalidates() {
        val ctx=rule.activity
        val now=System.currentTimeMillis()
        com.example.data.engine.OfficialWeatherReports.save(ctx, com.example.data.engine.OfficialReportSnapshot(com.example.data.engine.AlertArea.version(ctx), now, now+60_000,"Test area fixture","IMD Yellow warning fixture. Heavy rain. Not live phone weather."))
        rule.setContent { MaterialTheme { androidx.compose.material3.Surface { Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
            androidx.compose.material3.Text("Official weather reports - automated fixture")
            com.example.ui.screens.OfficialWeatherReportCard()
        } } } }
        rule.onNodeWithText("Official weather reports - automated fixture").assertIsDisplayed()
        rule.onNodeWithText("Not live phone weather.",substring=true).assertIsDisplayed()
        rule.runOnIdle {
            val v=rule.activity.window.decorView
            val b=android.graphics.Bitmap.createBitmap(v.width,v.height,android.graphics.Bitmap.Config.ARGB_8888)
            v.draw(android.graphics.Canvas(b))
            java.io.File("build/outputs/official-weather-fixture.png").apply{parentFile?.mkdirs()}.outputStream().use{b.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}
            b.recycle()
            com.example.data.engine.AlertArea.clear(ctx)
            assertTrue(com.example.data.engine.OfficialWeatherReports.text(ctx,now).contains("unavailable or expired"))
        }
        rule.onNodeWithText("unavailable or expired",substring=true).assertIsDisplayed()
    }
    @Test fun storedReportsIndependentOfNotificationOrSpeechPreferences() {
        val ctx=rule.activity;val now=System.currentTimeMillis()
        com.example.data.engine.OfficialAlertAnnouncer.setEnabled(ctx,false)
        com.example.data.engine.OfficialWeatherReports.save(ctx, com.example.data.engine.OfficialReportSnapshot(com.example.data.engine.AlertArea.version(ctx),now,now+60_000,"Area","CAP Minor report fixture"))
        assertTrue(com.example.data.engine.OfficialWeatherReports.text(ctx,now).contains("CAP Minor"))
        com.example.data.engine.OfficialWeatherReports.clear(ctx)
        assertTrue(com.example.data.engine.OfficialWeatherReports.text(ctx,now).contains("unavailable"))
    }
}
