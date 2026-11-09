package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Timelapse
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.db.SafetyEventEntity
import com.example.driving.DrivingLog
import com.example.ui.theme.BentoBorder
import com.example.ui.theme.BentoCardBg
import com.example.ui.theme.BentoGreenPrimary
import com.example.ui.theme.BentoRed
import com.example.ui.theme.BentoTextMuted
import com.example.ui.theme.BentoTextPrimary
import com.example.ui.theme.BentoTextSecondary
import java.util.Calendar
import java.util.GregorianCalendar

/**
 * Reports tab per the owner's reference (9 Nov 2026): period chips plus stat rows from
 * real stored data only. Rows whose data the app does not record (distance) are absent,
 * not faked. "View Detailed Report" opens the existing logs screen.
 */
@Composable
fun ReportsSummaryScreen(
    eventLogs: List<SafetyEventEntity>,
    drivingEntries: List<DrivingLog.Entry>,
    onOpenLogs: () -> Unit,
    nowMs: Long = System.currentTimeMillis(),
    modifier: Modifier = Modifier
) {
    val periods = listOf("Today", "7 Days", "30 Days")
    var period by remember { mutableStateOf("Today") }
    val startMs = remember(period, nowMs) {
        when (period) {
            "Today" -> GregorianCalendar().apply {
                timeInMillis = nowMs
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            "7 Days" -> nowMs - 7L * 24 * 60 * 60 * 1000
            else -> nowMs - 30L * 24 * 60 * 60 * 1000
        }
    }
    val summary = remember(eventLogs, drivingEntries, startMs, nowMs) {
        ReportsSummaryLogic.summary(eventLogs, drivingEntries, startMs, nowMs)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .testTag("reports_summary_screen")
    ) {
        Row {
            periods.forEach { p ->
                FilterChip(
                    selected = period == p,
                    onClick = { period = p },
                    label = { Text(p, fontSize = 12.sp) },
                    modifier = Modifier.padding(end = 8.dp).testTag("reports_chip_${p.lowercase().replace(' ', '_')}")
                )
            }
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = BentoCardBg),
            border = BorderStroke(1.dp, BentoBorder),
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp).testTag("reports_stats_card")
        ) {
            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                StatRow(Icons.Default.Timelapse, "Driving time", ReportsSummaryLogic.formatDuration(summary.driving.drivingMs) + if (summary.driving.ongoing) " (driving now)" else "", "reports_row_driving_time")
                StatRow(Icons.Default.DirectionsCar, "Trips started", "${summary.driving.trips}", "reports_row_trips")
                StatRow(Icons.Default.Stop, "Stops", "${summary.driving.stops}", "reports_row_stops")
                StatRow(Icons.Default.Notifications, "Safety events", "${summary.safetyEvents}", "reports_row_events")
                StatRow(Icons.Default.Warning, "SOS triggered", "${summary.sosEvents}", "reports_row_sos", valueColor = if (summary.sosEvents > 0) BentoRed else BentoTextPrimary)
            }
        }

        Text(
            "Counts come from this phone's own safety event log and driving journal. Driving time pairs each start with the matching stop; a stop with no known start adds no time. The app does not record distance, so no distance is shown.",
            fontSize = 11.sp,
            color = BentoTextMuted,
            modifier = Modifier.padding(top = 10.dp)
        )

        Button(
            onClick = onOpenLogs,
            colors = ButtonDefaults.buttonColors(containerColor = BentoGreenPrimary),
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp).testTag("reports_open_logs")
        ) {
            Text("View Detailed Report")
        }
    }
}

@Composable
private fun StatRow(icon: ImageVector, label: String, value: String, tag: String, valueColor: androidx.compose.ui.graphics.Color = BentoTextPrimary) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp).testTag(tag)
    ) {
        Icon(icon, contentDescription = null, tint = BentoGreenPrimary)
        Text(label, fontSize = 14.sp, color = BentoTextSecondary, modifier = Modifier.weight(1f))
        Text(value, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = valueColor)
    }
}
