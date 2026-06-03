package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = SportsVolt,
    onPrimary = TextDarkBlack,
    primaryContainer = SportsVoltDim,
    onPrimaryContainer = TextDarkBlack,
    secondary = RecoveryTeal,
    onSecondary = TextDarkBlack,
    tertiary = LaserCrimson,
    onTertiary = Color.White,
    background = CarbonBlack,
    onBackground = TextOnObsidian,
    surface = ObsidianSurface,
    onSurface = TextOnObsidian,
    surfaceVariant = ObsidianCard,
    onSurfaceVariant = TextOnObsidian,
    outline = BorderCarbon,
    error = LaserCrimson,
    errorContainer = Color(0xFF2D1619), // Soft red surface for dark mode Danger CTA
    onErrorContainer = LaserCrimson
)

private val LightColorScheme = lightColorScheme(
    primary = SportsVolt, // Keeps exact same Volt primary CTA brand DNA
    onPrimary = TextDarkBlack, // Heavy high-contrast text on Volt primary buttons
    primaryContainer = SportsVoltDim,
    onPrimaryContainer = TextDarkBlack,
    secondary = RecoveryTeal, // Cyan scientific highlights
    onSecondary = TextDarkBlack,
    tertiary = LaserCrimson,
    onTertiary = Color.White,
    background = MinimalLightBg, // #F5F7F4 - Flat matte Sage Slate Background
    onBackground = TextDarkBlack, // Premium bold readability dark text #111111
    surface = MinimalLightSurface, // #FFFFFF - Primary Elevated surface for cards
    onSurface = TextDarkBlack,
    surfaceVariant = MinimalLightAltBg, // #F0F3EF - Secondary surface for subtle items
    onSurfaceVariant = TextDarkBlack,
    outline = MinimalLightBorder, // Premium borders #E2E6E2
    error = SystemError,
    errorContainer = Color(0xFFFFEAEC), // Soft red surface for light mode Danger CTA (no harsh blocks)
    onErrorContainer = Color(0xFFB3261E)
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
