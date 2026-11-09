package com.irama.player.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Amber = Color(0xFFFFB547)
val Coral = Color(0xFFFF7A6B)

private val IramaColors = darkColorScheme(
    primary = Amber,
    onPrimary = Color(0xFF2B1A00),
    primaryContainer = Color(0xFF3A2808),
    onPrimaryContainer = Color(0xFFFFDDB0),
    secondary = Coral,
    onSecondary = Color(0xFF2B0A06),
    secondaryContainer = Color(0xFF3A1A16),
    onSecondaryContainer = Color(0xFFFFDAD4),
    background = Color(0xFF0D0D11),
    onBackground = Color(0xFFECEAF0),
    surface = Color(0xFF0D0D11),
    onSurface = Color(0xFFECEAF0),
    surfaceVariant = Color(0xFF22222A),
    onSurfaceVariant = Color(0xFFA6A4B0),
    surfaceContainerLowest = Color(0xFF09090C),
    surfaceContainerLow = Color(0xFF121217),
    surfaceContainer = Color(0xFF16161C),
    surfaceContainerHigh = Color(0xFF1D1D24),
    surfaceContainerHighest = Color(0xFF25252D),
    outline = Color(0xFF45444F),
    outlineVariant = Color(0xFF2C2C35),
)

@Composable
fun IramaTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = IramaColors, content = content)
}
