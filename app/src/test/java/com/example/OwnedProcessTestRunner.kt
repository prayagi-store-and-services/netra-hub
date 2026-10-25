package com.example

import org.junit.runners.model.FrameworkMethod
import org.robolectric.RobolectricTestRunner
import java.lang.reflect.Method

/** Keep real manifest/provider startup, then join owners in that test's sandbox before teardown. */
class OwnedProcessTestRunner(testClass: Class<*>) : RobolectricTestRunner(testClass) {
    override fun afterTest(method: FrameworkMethod, bootstrappedMethod: Method) {
        try {
            val cleanup = bootstrappedMethod.declaringClass.classLoader
                .loadClass("com.example.OwnedProcessCleanup")
            cleanup.getMethod("dispose").invoke(null)
        } finally {
            super.afterTest(method, bootstrappedMethod)
        }
    }
}
