package com.example.ui.theme

import androidx.compose.ui.graphics.Color

// Dark Navy Theme Palette (owner UI series 9 Nov 2026; reference image-fc4ed414.jpg)
// Deep navy background, navy cards, blue primary accent, green/amber/red status semantics.
val BentoBackground = Color(0xFF0A0E1A)
val BentoCardBg = Color(0xFF151B2E)
val BentoHeroCardBg = Color(0xFF1B2440)
val BentoDarkCardBg = Color(0xFF0D1322)
val BentoBorder = Color(0xFF263050)

// Primary brand accent is the reference blue (name kept for compatibility).
val BentoGreenPrimary = Color(0xFF4F8DFF)
// Status semantics: green = normal/active, amber = warning, red = critical/SOS.
val BentoGreenVibrant = Color(0xFF22C55E)
val BentoAmber = Color(0xFFF59E0B)
val BentoYellow = Color(0xFFFBBF24)
val BentoBlue = Color(0xFF60A5FA)
val BentoPurple = Color(0xFFA78BFA)
val BentoRed = Color(0xFFEF4444)

val BentoTextPrimary = Color(0xFFF1F5F9)
val BentoTextSecondary = Color(0xFF94A3B8)
val BentoTextMuted = Color(0xFF64748B)

// Backward Compatibility Aliases for components
val CyberObsidian = BentoBackground
val CyberSurface = BentoCardBg
val CyberSurfaceElevated = BentoHeroCardBg
val CyberBorder = BentoBorder

val ElectricCyan = BentoGreenPrimary
val EmeraldGreen = BentoGreenVibrant
val AmberYellow = BentoAmber
val HazardCrimson = BentoRed

val TextPrimary = BentoTextPrimary
val TextSecondary = BentoTextSecondary
val TextMuted = BentoTextMuted

// Dark Premium Theme Additions
// Names kept for compatibility; all map to the dark navy palette.
val NetraDarkBackground = BentoBackground
val NetraDarkSurface = BentoCardBg
val NetraDarkSurfaceElevated = BentoHeroCardBg
val NetraDarkBorder = BentoBorder
val NetraDarkTextPrimary = BentoTextPrimary
val NetraDarkTextSecondary = BentoTextSecondary
val NetraDarkTextMuted = BentoTextMuted
