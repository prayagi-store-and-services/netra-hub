package com.example.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/** A newer release described by latest.json (published with every release by the release workflow). */
data class AppRelease(
    val tag: String,
    val versionName: String,
    val versionCode: Long,
    val notes: String,
    val apkUrl: String,
    val sha256: String,
    val size: Long
)

/**
 * In-app updater. Reads latest.json from the repo's latest GitHub release, offers the update at most once a day,
 * downloads app-release.apk, verifies its sha256 and size, then hands it to the Android package installer
 * (the user confirms with one system tap). Android only installs it if it is signed with the same key as the installed app.
 */
object AppUpdater {
    const val REPO = "prayagi-store-and-services/netra-hub"
    const val APP_LABEL = "netra-hub"
    private const val PREFS = "netra_app_update"
    private const val KEY_LAST_CHECK = "last_check_ms"
    private const val DAY_MS = 24L * 60L * 60L * 1000L
    private val TAG_RE = Regex("^v[0-9]+\\.[0-9]+\\.[0-9]+$")
    private val SHA_RE = Regex("^[0-9a-f]{64}$")

    fun latestJsonUrl() = "https://github.com/$REPO/releases/latest/download/latest.json"

    /** Returns the release only when it is newer than [installedCode] and every field passes validation. */
    fun parse(json: String, installedCode: Long): AppRelease? {
        return try {
            val o = JSONObject(json)
            val tag = o.getString("tag")
            val code = o.getLong("versionCode")
            val sha = o.getString("sha256").lowercase()
            val size = o.getLong("size")
            if (!TAG_RE.matches(tag) || !SHA_RE.matches(sha) || size <= 0L || code <= installedCode) return null
            AppRelease(
                tag = tag,
                versionName = o.optString("versionName", tag.removePrefix("v")),
                versionCode = code,
                notes = o.optString("notes", "").trim(),
                apkUrl = "https://github.com/$REPO/releases/download/$tag/app-release.apk",
                sha256 = sha,
                size = size
            )
        } catch (e: Exception) {
            null
        }
    }

    /** Result of a manual check: on the newest version, a newer release exists, or the check could not be done. */
    sealed class CheckResult {
        object Latest : CheckResult()
        data class Newer(val release: AppRelease) : CheckResult()
        object Failed : CheckResult()
    }

    /** Pure decision for a latest.json text: newer release, already latest, or unreadable (Failed). */
    fun classify(json: String, installedCode: Long): CheckResult {
        parse(json, installedCode)?.let { return CheckResult.Newer(it) }
        return try {
            val code = JSONObject(json).getLong("versionCode")
            if (code <= installedCode) CheckResult.Latest else CheckResult.Failed
        } catch (e: Exception) {
            CheckResult.Failed
        }
    }

    /** Manual "Check for update": always asks now (ignores the once-a-day limit). Call off the main thread. */
    fun checkNow(context: Context): CheckResult {
        return try {
            val c = open(latestJsonUrl())
            if (c.responseCode != 200) return CheckResult.Failed
            val text = c.inputStream.use { it.readBytes() }
            if (text.size > 1024 * 1024) return CheckResult.Failed
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putLong(KEY_LAST_CHECK, System.currentTimeMillis()).apply()
            classify(String(text, Charsets.UTF_8), installedCode(context))
        } catch (e: Exception) {
            CheckResult.Failed
        }
    }

    fun installedVersionName(context: Context): String =
        try { context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "Unavailable" } catch (e: Exception) { "Unavailable" }

    fun installedCode(context: Context): Long =
        PackageInfoCompat.getLongVersionCode(context.packageManager.getPackageInfo(context.packageName, 0))

    private fun open(url: String): HttpURLConnection {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 10000
        c.readTimeout = 20000
        c.instanceFollowRedirects = true
        c.setRequestProperty("User-Agent", "$APP_LABEL-updater")
        return c
    }

    /** Checks at most once a day. Returns a newer release, or null (up to date, already checked today, or offline). */
    fun checkIfDue(context: Context): AppRelease? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        if (now - prefs.getLong(KEY_LAST_CHECK, 0L) < DAY_MS) return null
        return try {
            val c = open(latestJsonUrl())
            if (c.responseCode != 200) return null
            val text = c.inputStream.use { it.readBytes() }
            if (text.size > 1024 * 1024) return null
            prefs.edit().putLong(KEY_LAST_CHECK, now).apply()
            parse(String(text, Charsets.UTF_8), installedCode(context))
        } catch (e: Exception) {
            null
        }
    }

    /** Deletes every file in the installer download folder and returns how many were removed. */
    fun cleanDir(dir: File?): Int {
        var n = 0
        dir?.listFiles()?.forEach { if (it.delete()) n++ }
        return n
    }

    /**
     * Called when the app starts. After an in-app update installs, Android restarts the app, so this
     * removes the downloaded installer file and nothing is left in storage.
     */
    fun cleanLeftovers(context: Context) {
        try { cleanDir(File(context.cacheDir, "updates")) } catch (_: Exception) {}
    }

    /** Downloads the APK and verifies size and sha256. Deletes the file and throws if anything is off. */
    fun download(context: Context, release: AppRelease, onProgress: ((Long, Long) -> Unit)? = null): File {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val file = File(dir, "$APP_LABEL-${release.tag}.apk")
        val c = open(release.apkUrl)
        if (c.responseCode != 200) throw IllegalStateException("Download failed (server answered ${c.responseCode}).")
        val md = MessageDigest.getInstance("SHA-256")
        var total = 0L
        var lastReport = 0L
        c.inputStream.use { input ->
            file.outputStream().use { out ->
                val buf = ByteArray(16384)
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    total += n
                    if (total > release.size) { file.delete(); throw IllegalStateException("Downloaded file is larger than expected.") }
                    md.update(buf, 0, n)
                    out.write(buf, 0, n)
                    if (onProgress != null) { val now = System.currentTimeMillis(); if (now - lastReport >= 250L) { lastReport = now; onProgress(total, release.size) } }
                }
            }
        }
        val actual = md.digest().joinToString("") { "%02x".format(it) }
        if (total != release.size || actual != release.sha256) {
            file.delete()
            throw IllegalStateException("The downloaded file did not match its checksum, so it was not installed.")
        }
        return file
    }

    /** Opens the system installer. Throws a readable message when the user must first allow installs from this app. */
    fun install(context: Context, file: File) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + context.packageName))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            throw IllegalStateException("Allow installs from this app, then tap Update again.")
        }
        val uri = FileProvider.getUriForFile(context, context.packageName + ".updates", file)
        context.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}

/** Shows the update prompt (version, what changed, Update / Later) when a newer release exists. Checks at most once a day. */
@Composable
fun AppUpdatePrompt() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var release by remember { mutableStateOf<AppRelease?>(null) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var line by remember { mutableStateOf<String?>(null) }
    var frac by remember { mutableStateOf(0f) }
    LaunchedEffect(Unit) {
        release = withContext(Dispatchers.IO) { AppUpdater.checkIfDue(context) }
    }
    val r = release ?: return
    AlertDialog(
        onDismissRequest = { if (!busy) release = null },
        title = { Text("Update available: " + r.versionName) },
        text = {
            Column {
                Text(if (r.notes.isNotEmpty()) r.notes.take(800) else "A new version is ready.")
                if (busy) androidx.compose.material3.LinearProgressIndicator(progress = frac)
                line?.let { Text(it) }
                message?.let { Text(it) }
            }
        },
        confirmButton = {
            TextButton(enabled = !busy, onClick = {
                scope.launch {
                    busy = true
                    message = null
                    try {
                        val meter = DownloadMeter()
                        val file = withContext(Dispatchers.IO) { AppUpdater.download(context, r) { d, t -> line = meter.line(d, t, System.currentTimeMillis()); frac = DownloadMeter.fraction(d, t) } }
                        line = null
                        AppUpdater.install(context, file)
                        release = null
                    } catch (e: Exception) {
                        message = plainFailure(e, "Update failed.")
                    }
                    busy = false
                }
            }) { Text(if (busy) "Downloading..." else "Update") }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = { release = null }) { Text("Later") } }
    )
}
