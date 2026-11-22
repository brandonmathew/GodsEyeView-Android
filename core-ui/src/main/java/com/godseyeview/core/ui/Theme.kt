package com.godseyeview.core.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Design tokens from upstream v0.3.0's src/ui/styles/foundation.css
 *  ("Dark Sci-Fi UI"): near-black blue background, glass panels, one cyan
 *  accent, mono kickers. Q23: v2's visible design comes from the original
 *  app, not from v1. Dark theme only, as upstream. */
object GevColors {
    val BgDark = Color(0xFF0A0A0F)
    val Glass = Color(0xB80C0C14) // rgba(12, 12, 20, 0.72)
    val GlassBorder = Color(0x14FFFFFF) // rgba(255, 255, 255, 0.08)
    val GlassBorderHover = Color(0x26FFFFFF) // rgba(255, 255, 255, 0.15)
    val Accent = Color(0xFF00D4FF)
    val AccentDim = Color(0x2600D4FF) // rgba(0, 212, 255, 0.15)
    val TextPrimary = Color(0xFFE8EAED)
    val TextSecondary = Color(0x80E8EAED) // 50%
    val TextDim = Color(0x4DE8EAED) // 30%
    val MenuBg = Color(0xFF12121C)
}

private val DarkColors = darkColorScheme(
    primary = GevColors.Accent,
    onPrimary = Color(0xFF00141A),
    background = GevColors.BgDark,
    onBackground = GevColors.TextPrimary,
    surface = GevColors.MenuBg,
    onSurface = GevColors.TextPrimary,
    surfaceVariant = Color(0xFF1A1A26),
    onSurfaceVariant = GevColors.TextSecondary,
    outline = GevColors.GlassBorderHover,
)

/** Upstream pairs Inter-style sans body text with JetBrains Mono labels and
 *  kickers (letterspaced, uppercase). No bundled fonts on Android: sans uses
 *  the platform sans, kickers/labels use the platform monospace. */
private val GevTypography = Typography(
    labelLarge = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        letterSpacing = 1.3.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        letterSpacing = 1.3.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Medium,
        fontSize = 10.sp,
        letterSpacing = 1.3.sp,
    ),
)

@Composable
fun GodsEyeTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColors,
        typography = GevTypography,
        content = content,
    )
}
