package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val NetraBentoColorScheme = lightColorScheme(
    primary = BentoGreenPrimary,
    onPrimary = Color.White,
    secondary = BentoGreenVibrant,
    onSecondary = BentoTextPrimary,
    tertiary = BentoAmber,
    error = BentoRed,
    background = BentoBackground,
    onBackground = BentoTextPrimary,
    surface = BentoCardBg,
    onSurface = BentoTextPrimary,
    surfaceVariant = BentoHeroCardBg,
    onSurfaceVariant = BentoTextSecondary,
    outline = BentoBorder
)

private val NewHubColorScheme = lightColorScheme(
    primary = Color(0xFF1A237E),
    onPrimary = Color.White,
    secondary = Color(0xFF00897B),
    onSecondary = Color.White,
    tertiary = BentoAmber,
    error = BentoRed,
    background = Color(0xFFF3F4FB),
    onBackground = Color(0xFF191B2E),
    surface = Color.White,
    onSurface = Color(0xFF191B2E),
    surfaceVariant = Color(0xFFE6E8F7),
    onSurfaceVariant = Color(0xFF4A4E6B),
    outline = Color(0xFFC5C9E3)
)

@Composable
fun NetraTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (RedesignGate.isOn()) NewHubColorScheme else NetraBentoColorScheme,
        typography = Typography,
        content = content
    )
}
