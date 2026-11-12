package com.example.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.db.SafetyEventEntity
import com.example.ui.theme.BentoBorder
import com.example.ui.theme.BentoCardBg
import com.example.ui.theme.BentoTextMuted
import com.example.ui.theme.BentoTextPrimary
import com.example.ui.theme.BentoTextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Maps an event domain to the reference filter chips. Pure so it can be unit tested. */
fun alertFilterOf(domain: String, eventType: String): String = when {
    domain.equals("WEATHER", true) || eventType.contains("weather", true) || eventType.contains("sachet", true) -> "Weather"
    domain.equals("QUAKE", true) || eventType.contains("quake", true) || eventType.contains("earthquake", true) -> "Earthquake"
    domain.equals("SOS", true) || eventType.contains("sos", true) -> "SOS"
    else -> "All"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlertsScreen(eventLogs: List<SafetyEventEntity>, modifier: Modifier = Modifier) {
    var filter by remember { mutableStateOf("All") }
    val chips = listOf("All", "Weather", "Earthquake", "SOS")
    val shown = if (filter == "All") eventLogs else eventLogs.filter { alertFilterOf(it.domain, it.eventType) == filter }
    val fmt = remember { SimpleDateFormat("dd MMM, hh:mm a", Locale.US) }

    Column(modifier = modifier.fillMaxSize().padding(16.dp).testTag("alerts_screen")) {
        Row {
            chips.forEach { chip ->
                FilterChip(
                    selected = filter == chip,
                    onClick = { filter = chip },
                    label = { Text(chip, fontSize = 12.sp) },
                    modifier = Modifier.padding(end = 8.dp).testTag("alerts_chip_${chip.lowercase()}")
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        if (shown.isEmpty()) {
            Text(
                text = if (filter == "All") "No alerts recorded yet." else "No $filter alerts recorded.",
                fontSize = 13.sp,
                color = BentoTextMuted,
                modifier = Modifier.padding(top = 24.dp).testTag("alerts_empty")
            )
        } else {
            LazyColumn {
                items(shown, key = { it.id }) { event ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = BentoCardBg),
                        border = androidx.compose.foundation.BorderStroke(1.dp, BentoBorder),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(text = event.title.ifBlank { event.eventType }, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = BentoTextPrimary)
                            Text(
                                text = "${fmt.format(Date(event.timestamp))} - ${event.domain} - ${event.riskLevel}",
                                fontSize = 11.sp,
                                color = BentoTextSecondary
                            )
                        }
                    }
                }
            }
        }
    }
}
