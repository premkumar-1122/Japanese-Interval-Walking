package com.example.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color

val DarkSportsVolt = Color(0xFFB7F28E)       // Keep existing neon green #B7F28E
val DarkSportsVoltDim = Color(0xFF9ECE7E)
val DarkRecoveryTeal = Color(0xFF00D9FF)     // Cyan scientific accent
val DarkLaserCrimson = Color(0xFFFF4D6D)     // High-intensity phase / error

val DarkColorScheme = darkColorScheme(
    primary = DarkSportsVolt,
    onPrimary = Color(0xFF111111),
    primaryContainer = DarkSportsVoltDim,
    onPrimaryContainer = Color(0xFF111111),
    secondary = DarkRecoveryTeal,
    onSecondary = Color(0xFF111111),
    secondaryContainer = Color(0xFF1B2318), // Selected navigation item background
    onSecondaryContainer = DarkSportsVolt, // Highlight green for active navigation item
    tertiary = DarkLaserCrimson,
    onTertiary = Color.White,
    background = Color(0xFF0F0F0F), // CarbonBlack
    onBackground = Color(0xFFFFFFFF),
    surface = Color(0xFF0F0F0F), // Obsidian surface
    onSurface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFF1A1A1A), // Obsidian card
    onSurfaceVariant = Color(0xFFD4D4D4), // Soft light grey for secondary on cards
    outline = Color(0xFF262626), // BorderCarbon
    outlineVariant = Color(0xFFA3A3A3), // Muted Text for dark mode
    error = DarkLaserCrimson,
    errorContainer = Color(0xFF2D1619),
    onErrorContainer = DarkLaserCrimson
)
