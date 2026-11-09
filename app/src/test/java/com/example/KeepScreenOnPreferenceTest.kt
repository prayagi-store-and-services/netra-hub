package com.example

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.example.monitoring.KeepScreenOnPreference
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(org.robolectric.RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class KeepScreenOnPreferenceTest {
    private val ctx get() = ApplicationProvider.getApplicationContext<Application>()

    @Test fun defaultIsOff() {
        ctx.getSharedPreferences(KeepScreenOnPreference.PREFS, 0).edit().clear().commit()
        assertFalse(KeepScreenOnPreference.isEnabled(ctx))
    }
    @Test fun explicitChoicePersists() {
        ctx.getSharedPreferences(KeepScreenOnPreference.PREFS, 0).edit().clear().commit()
        assertTrue(KeepScreenOnPreference.setByUser(ctx, true))
        assertTrue(KeepScreenOnPreference.isEnabled(ctx))
        assertTrue(KeepScreenOnPreference.setByUser(ctx, false))
        assertFalse(KeepScreenOnPreference.isEnabled(ctx))
    }
}
