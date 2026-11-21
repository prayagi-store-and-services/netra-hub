package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.BentoBackground
import com.example.ui.theme.BentoGreenPrimary
import com.example.ui.theme.BentoPurple
import com.example.ui.theme.BentoTextMuted
import com.example.ui.theme.BentoTextPrimary
import com.example.ui.theme.BentoTextSecondary

/**
 * Startup splash per the owner's reference (9 Nov 2026): dark full-bleed backdrop, shield
 * mark, wordmark, tagline, loading indicator. The reference uses a mountain photo; this
 * uses a navy vertical gradient instead because no photo asset was supplied. Purely
 * cosmetic: it hides no initialization failure and never blocks longer than the caller's
 * fixed timeout.
 */
@Composable
fun SplashScreen(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(BentoBackground, Color(0xFF101736), BentoBackground)))
            .testTag("splash_screen"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.Shield,
            contentDescription = null,
            tint = BentoPurple,
            modifier = Modifier.size(96.dp)
        )
        Spacer(modifier = Modifier.height(20.dp))
        Text("Netra Hub", fontSize = 34.sp, fontWeight = FontWeight.Bold, color = BentoTextPrimary)
        Text("Human Safety Platform", fontSize = 16.sp, color = BentoTextSecondary)
        Spacer(modifier = Modifier.height(4.dp))
        Text("Drive Safe · Stay Alert · Be Prepared", fontSize = 12.sp, color = BentoTextMuted)
        Spacer(modifier = Modifier.height(48.dp))
        CircularProgressIndicator(color = BentoGreenPrimary, modifier = Modifier.size(28.dp), strokeWidth = 2.dp)
        Spacer(modifier = Modifier.height(12.dp))
        Text("Loading...", fontSize = 13.sp, color = BentoTextMuted)
    }
}
