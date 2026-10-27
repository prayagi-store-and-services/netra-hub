package com.example.update

import android.content.Context
import java.io.File

/** No APK downloader or installer remains. Eco owns app downloads and updates. */
object AppUpdater {
    const val ECO_PACKAGE = "com.prayagi.netraeco"
    const val MIN_ECO_HANDOFF_VERSION = 59L
    const val ECO_PAGE = "https://prayagi-store-and-services.github.io/netra-eco/app/hub/"
    const val METADATA_URL = "https://github.com/prayagi-store-and-services/netra-hub/releases/latest/download/latest.json"
    data class Newer(val code:Long,val name:String)
    fun installedCode(context:Context):Long {
        val p=context.packageManager.getPackageInfo(context.packageName,0)
        return if(android.os.Build.VERSION.SDK_INT>=28)p.longVersionCode else p.versionCode.toLong()
    }
    fun parseNewer(json:String,installed:Long):Newer? {
        val o=org.json.JSONObject(json)
        val code=o.getLong("versionCode");require(code>0)
        if(code<=installed)return null
        val name=o.optString("versionName","").take(80).filter { !it.isISOControl() }
        require(name.isNotBlank())
        return Newer(code,name)
    }
    /** Metadata only. Never follows or reads artifact fields, never writes an installer file. */
    internal fun readMetadata(input:java.io.InputStream):ByteArray {
        val out=java.io.ByteArrayOutputStream();val b=ByteArray(4096)
        while(true){val n=input.read(b);if(n<0)break;require(out.size()+n<=131072);out.write(b,0,n)}
        return out.toByteArray()
    }
    // The fixed HTTPS start URL follows the hosting service's redirects. No redirect-host allowlist is claimed.
    fun newer(context:Context):Newer? {
        val connection=java.net.URL(METADATA_URL).openConnection() as javax.net.ssl.HttpsURLConnection
        connection.connectTimeout=10000;connection.readTimeout=10000
        return try {
            require(connection.responseCode==200)
            val bytes=connection.inputStream.use { readMetadata(it) }
            parseNewer(bytes.toString(Charsets.UTF_8),installedCode(context))
        } finally { connection.disconnect() }
    }
    fun openEco(context: Context): Boolean {
        val page=android.net.Uri.parse(ECO_PAGE)
        val compatible=try {
            val info=context.packageManager.getPackageInfo(ECO_PACKAGE,0)
            (if(android.os.Build.VERSION.SDK_INT>=28)info.longVersionCode else info.versionCode.toLong())>=MIN_ECO_HANDOFF_VERSION
        }catch(_:android.content.pm.PackageManager.NameNotFoundException){false}
        if(compatible)try {
            context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW,page).setPackage(ECO_PACKAGE).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK));return true
        } catch (_: android.content.ActivityNotFoundException) { } catch (_:SecurityException) { }
        return try {
            val browser=android.content.Intent(android.content.Intent.ACTION_VIEW,page).addCategory(android.content.Intent.CATEGORY_BROWSABLE).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            // Generic web capability, not the target app-link resolver list. Android marks
            // browsers that handle all web URIs; target-specific app handlers are not browsers.
            val generic=android.content.Intent(android.content.Intent.ACTION_VIEW,android.net.Uri.parse("https://"))
                .addCategory(android.content.Intent.CATEGORY_BROWSABLE)
            val candidates=context.packageManager.queryIntentActivities(generic,android.content.pm.PackageManager.MATCH_DEFAULT_ONLY or android.content.pm.PackageManager.GET_RESOLVED_FILTER)
            val handler=candidates.firstOrNull {
                it.filter?.let { filter -> filter.hasDataScheme("https") && filter.countDataAuthorities()==0 && filter.countDataPaths()==0 } == true && it.activityInfo.exported &&
                it.activityInfo.packageName!=ECO_PACKAGE && it.activityInfo.packageName!=context.packageName
            } ?: return false
            context.startActivity(browser.setPackage(handler.activityInfo.packageName));true
        } catch (_:android.content.ActivityNotFoundException){false} catch (_:SecurityException){false}
    }
}
