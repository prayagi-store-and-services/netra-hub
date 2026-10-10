package com.example

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.example.ui.MainScreen
import com.example.ui.MainViewModel
import com.example.ui.theme.NetraTheme

open class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        com.example.update.UpdateAlert.handle(this, intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        try { Brand.applyLauncherName(this) } catch (_: Throwable) { }
        super.onCreate(savedInstanceState)
        if (com.example.geo.GeoGuard.isBlocked(this)) {
            val pad = (24 * resources.displayMetrics.density).toInt()
            val box = android.widget.LinearLayout(this).apply {
                orientation = android.widget.LinearLayout.VERTICAL
                gravity = android.view.Gravity.CENTER
                setPadding(pad, pad, pad, pad)
            }
            box.addView(android.widget.TextView(this).apply {
                text = "This app is not available in your region."
                textSize = 20f
                gravity = android.view.Gravity.CENTER
            })
            box.addView(android.widget.Button(this).apply {
                text = "Close"
                setOnClickListener { finishAffinity() }
            })
            setContentView(box)
            return
        }
        // Remove any installer file left from an in-app update (runs in the background).
        Thread { com.example.update.AppUpdater.cleanLeftovers(applicationContext) }.start()
        com.example.update.UpdateAlert.start(this)
        // Anonymous daily usage count (+1 on a public counter, nothing else). The user can turn it off in Settings.
        // Automatic crash reports: a crash saved last time is sent now, in the background. No personal data.
        com.example.stats.CrashReporter.install(this)
        val usageCtx = applicationContext
        Thread { com.example.stats.UsagePing.pingIfDue(usageCtx) }.start()
        com.example.data.engine.QuakeWatchWorker.schedule(applicationContext)
        com.example.data.engine.SachetWatchWorker.schedule(applicationContext)
        enableEdgeToEdge()
        com.example.util.LoggingManager.init(applicationContext)

        // Keep the screen on 24/7 while the application is active
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Start Foreground Service to keep app running in the background if permission is already granted
        val hasLocationPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED ||
            checkSelfPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

        if (hasLocationPermission) {
            val serviceIntent = Intent(this, NetraForegroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
        }

        setContent {
            NetraTheme {
                com.example.update.AppUpdatePrompt()
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen(viewModel = viewModel)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        Thread { com.example.update.AppUpdater.cleanStale(applicationContext) }.start()
        viewModel.securityEngine.scanDevice()
    }
}
