package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val PulseDarkColorScheme = darkColorScheme(
    primary = NeonCyan,
    onPrimary = Color(0xFF00363A),
    primaryContainer = Color(0xFF004F56),
    onPrimaryContainer = Color(0xFF70F5FF),
    secondary = NeonEmerald,
    onSecondary = Color(0xFF003822),
    secondaryContainer = Color(0xFF005234),
    onSecondaryContainer = Color(0xFF6CFFC2),
    tertiary = NeonPurple,
    background = DarkCanvas,
    onBackground = TextPrimary,
    surface = DarkSurface,
    onSurface = TextPrimary,
    surfaceVariant = DarkCardBg,
    onSurfaceVariant = TextSecondary,
    outline = DarkBorder
)

@Composable
fun PulseTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = PulseDarkColorScheme,
        typography = Typography,
        content = content
    )
}
