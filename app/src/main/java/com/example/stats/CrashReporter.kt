package com.example.stats

import android.content.Context
import android.os.Build
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Automatic crash reports. When the app crashes, a short report is saved on the device. The next time the app opens,
 * it is sent by itself (no button, no question) and then deleted; if the send fails it is kept and retried next start.
 * The report holds ONLY: app name, phone model, Android version, app version and the crash stack trace
 * (exception class names and code locations; exception messages are dropped). No name, email, location, files or device IDs.
 */
object CrashReporter {
    private const val APP_NAME = "netra-hub"
    private const val FILE = "pending_crash_report.txt"
    private const val ENDPOINT = "https://formsubmit.co/ajax/prayagideepak@gmail.com"
    private const val MAX_TRACE = 3000
    @Volatile private var installed = false

    /** Exception class names and code locations only. */
    fun sanitize(t: Throwable): String {
        val sb = StringBuilder()
        var cur: Throwable? = t
        var depth = 0
        while (cur != null && depth < 5) {
            sb.append(if (depth == 0) "" else "Caused by: ").append(cur.javaClass.name).append('\n')
            for (frame in cur.stackTrace.take(25)) sb.append("  at ").append(frame.toString()).append('\n')
            cur = cur.cause
            depth++
        }
        return sb.toString().take(MAX_TRACE)
    }

    fun install(context: Context) {
        if (installed) return
        installed = true
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching { File(app.filesDir, FILE).writeText(sanitize(throwable)) }
            previous?.uncaughtException(thread, throwable)
        }
        Thread { sendPending(app, appVersion(app)) }.start()
    }

    private fun appVersion(c: Context): String =
        runCatching { c.packageManager.getPackageInfo(c.packageName, 0).versionName ?: "unknown" }.getOrDefault("unknown")

    fun sendPending(context: Context, version: String): Boolean {
        val file = File(context.filesDir, FILE)
        if (!file.exists()) return false
        val trace = runCatching { file.readText() }.getOrDefault("")
        if (trace.isBlank()) { runCatching { file.delete() }; return false }
        return try {
            val json = org.json.JSONObject()
            json.put("_subject", "[Netra] Automatic crash report: $APP_NAME")
            json.put("_captcha", "false")
            json.put("_template", "table")
            json.put("app", APP_NAME)
            json.put("device", Build.MODEL ?: "Unknown")
            json.put("android_version", Build.VERSION.RELEASE ?: "Unknown")
            json.put("app_version", version)
            json.put("stack_trace", trace)
            val c = URL(ENDPOINT).openConnection() as HttpURLConnection
            c.requestMethod = "POST"; c.connectTimeout = 10000; c.readTimeout = 15000; c.doOutput = true
            c.setRequestProperty("Content-Type", "application/json"); c.setRequestProperty("Accept", "application/json")
            c.outputStream.use { it.write(json.toString().toByteArray(Charsets.UTF_8)) }
            val ok = c.responseCode in 200..299
            c.disconnect()
            if (ok) runCatching { file.delete() }
            ok
        } catch (_: Exception) { false }
    }
}
