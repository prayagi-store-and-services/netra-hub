package com.example

import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import com.example.data.engine.queryBiometricStatus
import com.example.data.model.FeatureStatus
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(OwnedProcessTestRunner::class)
@Config(sdk = [24, 29, 34], application = Application::class)
class BiometricPermissionTest {
    @Test fun deniedPermissionReturnsUnknownWithoutQuery() {
        val base = ApplicationProvider.getApplicationContext<Application>()
        var queries = 0
        val context = object : ContextWrapper(base) {
            override fun checkSelfPermission(permission: String): Int = PackageManager.PERMISSION_DENIED
            override fun getSystemServiceName(serviceClass: Class<*>): String? {
                queries++
                throw AssertionError("Permission denied: typed service lookup must not be queried")
            }
            override fun getSystemService(name: String): Any? {
                queries++
                throw AssertionError("Permission denied: biometric service must not be queried")
            }
        }
        assertEquals(FeatureStatus.UNKNOWN, queryBiometricStatus(context))
        assertEquals(0, queries)
    }
}
