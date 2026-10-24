package com.example.monitoring

import android.content.Context
import android.content.Intent
import android.os.Build
import com.example.NetraForegroundService
import com.example.data.repository.NetraSafetyRepository

/** UI-only orchestration. Never called by boot, charger, permission or recovery paths. */
object MonitoringControl {
    fun pause(context: Context): Boolean {
        if (!MonitoringPreference.setByUser(context, false)) return false
        // Do not construct/start a repository just to pause it. Persisted false remains authoritative
        // even if immediate teardown fails; report failure rather than claiming all work stopped.
        var stopped = true
        try { NetraSafetyRepository.getIfCreated()?.sensorManager?.stopByUser() }
        catch (_: Exception) { stopped = false }
        try { context.stopService(Intent(context, NetraForegroundService::class.java)) }
        catch (_: Exception) { stopped = false }
        return stopped
    }

    fun resume(context: Context): Boolean {
        if (!MonitoringPreference.setByUser(context, true)) return false
        try {
            val intent = Intent(context, NetraForegroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent)
            else context.startService(intent)
            return true
        } catch (_: Exception) {
            // A rejected start must not leave background recovery free to enable monitoring later.
            MonitoringPreference.setByUser(context, false)
            try { NetraSafetyRepository.getIfCreated()?.sensorManager?.stopByUser() }
            catch (_: Exception) { }
            return false
        }
    }
}
