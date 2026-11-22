package com.godseyeview.core.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Plan Q30: dark theme only.
private val DarkColors = darkColorScheme(
    primary = Color(0xFF7FD4FF),
    onPrimary = Color(0xFF06222E),
    background = Color(0xFF06090C),
    onBackground = Color(0xFFE8EEF2),
    surface = Color(0xFF10161B),
    onSurface = Color(0xFFE8EEF2),
    surfaceVariant = Color(0xFF1A232B),
    onSurfaceVariant = Color(0xFFA9B8C2),
)

@Composable
fun GodsEyeTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = DarkColors, content = content)
}
