package com.example.widget

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.Row
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.example.MainActivity
import com.example.stats.StepMath
import com.example.stats.StepState
import com.example.stats.StepTarget
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * The single Netra Hub widget. Real values only; anything not known shows "Unavailable".
 * - Battery temperature and charging state: the phone's battery broadcast, read when the widget is drawn.
 * - Steps today and the age based target: the values the Hub app itself saved on this phone the last time it read the
 *   step counter (the widget never reads the sensor, so no background work).
 */
class NetraBatteryWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val tempInt = intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE) ?: Int.MIN_VALUE
        val batteryTempC = if (tempInt == Int.MIN_VALUE) null else tempInt / 10f
        val statusInt = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val statusText = when (statusInt) {
            BatteryManager.BATTERY_STATUS_CHARGING -> "Charging"
            BatteryManager.BATTERY_STATUS_DISCHARGING, BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "Not charging"
            BatteryManager.BATTERY_STATUS_FULL -> "Full"
            else -> "Unavailable"
        }
        val isWarning = batteryTempC != null && batteryTempC >= 40.0f

        val prefs = context.getSharedPreferences("netra_steps", Context.MODE_PRIVATE)
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val day = prefs.getString("day", null)
        val steps: Long? = if (day == today) {
            StepMath.today(StepState(day, prefs.getFloat("base", 0f), prefs.getFloat("carried", 0f), prefs.getFloat("last", 0f), prefs.getLong("since", 0L)))
        } else null
        val dob = prefs.getString("dob", null)
        val cal = Calendar.getInstance()
        val parsed = dob?.let { StepTarget.parseDob(it, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH)) }
        val target = parsed?.let { StepTarget.forAge(StepTarget.ageYears(it, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH))) }
        val stepsText = steps?.let { "$it steps" } ?: "Unavailable"
        val targetText = target?.let { "Target $it" } ?: "Target Unavailable"
        val read = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())

        provideContent {
            GlanceTheme {
                Column(
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .background(ColorProvider(Color(0xFF1F1F1F)))
                        .padding(12.dp)
                        .clickable(actionStartActivity<MainActivity>()),
                    horizontalAlignment = Alignment.Horizontal.CenterHorizontally,
                    verticalAlignment = Alignment.Vertical.CenterVertically
                ) {
                    Text("Netra Hub", style = TextStyle(color = ColorProvider(Color(0xFF00E5FF)), fontSize = 12.sp, fontWeight = FontWeight.Bold))
                    Spacer(modifier = GlanceModifier.height(4.dp))
                    Text(
                        text = batteryTempC?.let { String.format(Locale.US, "%.1f\u00B0C", it) } ?: "Unavailable",
                        style = TextStyle(color = ColorProvider(Color.White), fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = "Battery temperature, $statusText",
                        style = TextStyle(color = ColorProvider(Color.LightGray), fontSize = 10.sp)
                    )
                    if (batteryTempC != null) {
                        Spacer(modifier = GlanceModifier.height(4.dp))
                        Row(
                            modifier = GlanceModifier
                                .background(ColorProvider(if (isWarning) Color(0xFFD32F2F) else Color(0xFF388E3C)))
                                .padding(horizontal = 10.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = if (isWarning) "HIGH TEMP (40C or more)" else "Below 40C",
                                style = TextStyle(color = ColorProvider(Color.White), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            )
                        }
                    }
                    Spacer(modifier = GlanceModifier.height(6.dp))
                    Text(
                        text = "Steps today: $stepsText",
                        style = TextStyle(color = ColorProvider(Color.White), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = "$targetText  |  drawn $read",
                        style = TextStyle(color = ColorProvider(Color.LightGray), fontSize = 10.sp)
                    )
                    Text(
                        text = "Steps update when Hub is open",
                        style = TextStyle(color = ColorProvider(Color.Gray), fontSize = 9.sp)
                    )
                }
            }
        }
    }
}
