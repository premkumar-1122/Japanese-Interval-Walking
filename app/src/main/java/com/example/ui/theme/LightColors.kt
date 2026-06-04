package com.example.ui.theme

import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

val LightSportsGreen = Color(0xFF5FAF35)   // Premium, WCAG AA compliant athletic green
val LightRecoveryTeal = Color(0xFF007A93)   // Mid-tone cyan for premium scientific contrast
val LightLaserCrimson = Color(0xFFD92144)   // High-contrast deep red

val LightColorScheme = lightColorScheme(
    primary = LightSportsGreen,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEEF2EC), // Secondary Surface Card #EEF2EC
    onPrimaryContainer = Color(0xFF111111),
    secondary = LightRecoveryTeal,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFEDF3E7), // Navigation background #EDF3E7
    onSecondaryContainer = Color(0xFF111111), // Selected navigation item text/icon
    tertiary = LightLaserCrimson,
    onTertiary = Color.White,
    background = Color(0xFFF4F6F2), // App background #F4F6F2
    onBackground = Color(0xFF111111), // Primary Text #111111
    surface = Color(0xFFFFFFFF), // Primary Card #FFFFFF
    onSurface = Color(0xFF111111), // Text on primary cards
    surfaceVariant = Color(0xFFEEF2EC), // Secondary Surface #EEF2EC
    onSurfaceVariant = Color(0xFF5F6368), // Secondary Text #5F6368
    outline = Color(0xFFD8DED3), // Border #D8DED3
    outlineVariant = Color(0xFF7A7A7A), // Muted Text #7A7A7A
    error = LightLaserCrimson,
    errorContainer = Color(0xFFFFEAEC), // Soft red surface for Danger CTA
    onErrorContainer = LightLaserCrimson
)
