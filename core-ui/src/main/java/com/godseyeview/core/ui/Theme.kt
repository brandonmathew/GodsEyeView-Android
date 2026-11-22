package com.godseyeview.core.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Identity tokens (Q24): the dark theme and the one cyan accent are
 *  identity basics carried from the original app — near-black blue
 *  background, `#00D4FF` accent, light `#E8EAED` text. Layout and
 *  components are original mobile designs, not transplants. Dark theme
 *  only. */
object GevColors {
    val BgDark = Color(0xFF0A0A0F)
    val Glass = Color(0xB80C0C14) // rgba(12, 12, 20, 0.72)
    val GlassBorder = Color(0x14FFFFFF) // rgba(255, 255, 255, 0.08)
    val GlassBorderHover = Color(0x26FFFFFF) // rgba(255, 255, 255, 0.15)
    /** Near-opaque panel for chrome that floats over the globe (top bar,
     *  dock, entity card): the globe tints through only slightly, so text
     *  contrast never depends on what is behind the panel. */
    val PanelSolid = Color(0xF20C0C14) // rgba(12, 12, 20, 0.95)
    val Accent = Color(0xFF00D4FF)
    val AccentDim = Color(0x2600D4FF) // rgba(0, 212, 255, 0.15)
    val TextPrimary = Color(0xFFE8EAED)
    val TextSecondary = Color(0xB3E8EAED) // 70%
    val TextDim = Color(0x8AE8EAED) // 54%
    val MenuBg = Color(0xFF12121C)
    val CardBg = Color(0xFF14141F)
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
    ) {
        // Root-cause fix for the beta.2 "black headings" defect (#19):
        // Text composables without an explicit colour fall back to
        // LocalContentColor, which is BLACK unless a Surface ancestor
        // sets it. Beta.2's mission picker had no Surface, so its
        // headings rendered near-black on the dark background. The theme
        // now always provides an opaque dark Surface at the root, so the
        // default content colour is the light onBackground everywhere.
        androidx.compose.material3.Surface(
            color = GevColors.BgDark,
            contentColor = GevColors.TextPrimary,
            modifier = Modifier.fillMaxSize(),
        ) {
            content()
        }
    }
}
