package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Single dark navy scheme (owner UI series 9 Nov 2026). Both historical scheme names
// resolve to it so the RedesignGate branch keeps compiling; the gate no longer changes colors.
private val HubDarkNavyScheme = darkColorScheme(
    primary = BentoGreenPrimary,
    onPrimary = Color(0xFF0A0E1A),
    secondary = BentoGreenVibrant,
    onSecondary = Color(0xFF0A0E1A),
    tertiary = BentoAmber,
    onTertiary = Color(0xFF0A0E1A),
    error = BentoRed,
    onError = Color(0xFF0A0E1A),
    background = BentoBackground,
    onBackground = BentoTextPrimary,
    surface = BentoCardBg,
    onSurface = BentoTextPrimary,
    surfaceVariant = BentoHeroCardBg,
    onSurfaceVariant = BentoTextSecondary,
    outline = BentoBorder
)

private val NetraBentoColorScheme = HubDarkNavyScheme
private val NewHubColorScheme = HubDarkNavyScheme

@Composable
fun NetraTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = com.example.festival.festiveScheme(if (RedesignGate.isOn()) NewHubColorScheme else NetraBentoColorScheme),
        typography = Typography,
        content = content
    )
}
