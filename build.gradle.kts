buildscript {
  configurations.classpath {
    resolutionStrategy {
      // Dependabot 2026-10-16: the Gradle plugin classpath pulls bcprov-jdk18on 1.79
      // (via com.android.tools:sdk-common 32.1.1 from AGP) and httpclient 4.5.6 (via httpmime).
      // Force patched versions. Build-time only: never packaged into the app.
      force("org.bouncycastle:bcprov-jdk18on:1.85")
      force("org.apache.httpcomponents:httpclient:4.5.14")
    }
  }
}

// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
  alias(libs.plugins.android.application) apply false
  alias(libs.plugins.kotlin.compose) apply false
  alias(libs.plugins.google.devtools.ksp) apply false
  alias(libs.plugins.roborazzi) apply false
  alias(libs.plugins.secrets) apply false
  alias(libs.plugins.google.services) apply false
}
