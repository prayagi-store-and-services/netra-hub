package com.example.data.service

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat

/**
 * Background location state for the automatic checks (earthquake and NDMA SACHET, about every 30 minutes).
 * Android only gives location to a background job when the app has the "Allow all the time" choice.
 * On Android 11 and newer that choice is only on the app's own settings page, never in a pop-up.
 */
object LocationAccess {
    fun backgroundGranted(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            // Before Android 10 there is no separate background permission: foreground location also works in the background.
            return ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        }
        return ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    /** Opens this app's system settings page, where Permissions > Location > "Allow all the time" is chosen. */
    fun appSettingsIntent(context: Context): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    const val NEEDS_BACKGROUND_TEXT = "Automatic checks need background location. Open Hub app settings > Permissions > Location > Allow all the time."

    /** Plain-language reason a check could not run, instead of an internal status code. Pure function, unit tested. */
    fun failureText(status: String, backgroundGranted: Boolean): String = when {
        status == "PERMISSION_NOT_GRANTED" -> "Location permission is off for Hub. Turn it on in Hub app settings."
        status == "PROVIDER_DISABLED" -> "Phone location (GPS) is switched off."
        !backgroundGranted -> NEEDS_BACKGROUND_TEXT
        else -> "Android gave no recent location fix this time (a fix must be under 15 minutes old)."
    }

    /** Joins place names, skipping repeats and placeholders. Returns "Unavailable" when nothing real is left. */
    fun placeText(vararg parts: String): String {
        val seen = LinkedHashSet<String>()
        for (p in parts) {
            val t = p.trim()
            if (t.isNotEmpty() && !t.equals("Location Unavailable", true) && !t.equals("Unavailable", true)) seen.add(t)
        }
        return if (seen.isEmpty()) "Unavailable" else seen.joinToString(", ")
    }
}
