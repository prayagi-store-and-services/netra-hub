package com.example.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.BentoBorder
import com.example.ui.theme.BentoCardBg
import com.example.ui.theme.BentoTextMuted
import com.example.ui.theme.BentoTextPrimary
import com.example.ui.theme.BentoTextSecondary

/**
 * Weather Intelligence: shows the official IMD/NDMA SACHET report exactly as checked.
 * When no current official report exists it says so - an unavailable report is never
 * rendered as an all-clear. Automatic checks run about every 30 minutes; Android may
 * delay them.
 */
@Composable
fun WeatherIntelligenceScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val report = remember { com.example.data.engine.OfficialWeatherReports.text(context, System.currentTimeMillis()) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .testTag("weather_intelligence_screen")
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = BentoCardBg),
            border = androidx.compose.foundation.BorderStroke(1.dp, BentoBorder),
            modifier = Modifier.fillMaxWidth().testTag("weather_report_card")
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Official Weather Report", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = BentoTextPrimary)
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = report, fontSize = 13.sp, color = BentoTextSecondary)
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Source: IMD & NDMA SACHET. Automatic checks about every 30 minutes; Android may delay them. An unavailable report is not an all-clear.",
            fontSize = 11.sp,
            color = BentoTextMuted
        )
    }
}
