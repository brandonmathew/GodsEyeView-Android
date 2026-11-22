package com.godseyeview.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** Shared mobile UI primitives (Q24, issue #19). Beta.2's desktop-style
 *  SceneChrome — floating wordmark pill, separate status pills, and the
 *  twin glass dock slabs — is deleted; what remains here is the panel
 *  styling, the mono kicker, a standard top app bar, and the compact
 *  single-row mobile dock. All layout is designed for phone widths
 *  (360–412dp) first. */

/** Panel surface for chrome floating over the globe: near-opaque, hairline
 *  border, 16dp radius. */
@Composable
fun Modifier.glassPanel(): Modifier = this
    .clip(RoundedCornerShape(16.dp))
    .background(GevColors.PanelSolid)
    .border(1.dp, GevColors.GlassBorder, RoundedCornerShape(16.dp))

/** Small letterspaced mono label, e.g. "MISSION CONTROL · FIRST LAUNCH". */
@Composable
fun Kicker(
    text: String,
    modifier: Modifier = Modifier,
    color: androidx.compose.ui.graphics.Color = GevColors.TextSecondary,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = color,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

/** Standard mobile top app bar: optional back arrow, a title (with an
 *  optional one-line subtitle under it), and a trailing actions slot.
 *  Fixed 64dp height so every full-screen flow lines up. */
@Composable
fun GevTopBar(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    backContentDescription: String = "Back",
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = backContentDescription,
                    tint = GevColors.TextPrimary,
                )
            }
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 8.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = GevColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = GevColors.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        actions()
    }
}

/** One cell of the mobile dock: icon over a short label. The cell fills
 *  its share of the dock's width and the full dock height (≥56dp touch
 *  target); the label is a single line that can never wrap. */
@Composable
fun RowScope.DockCell(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    accent: Boolean = false,
) {
    val tint = if (accent) GevColors.Accent else GevColors.TextPrimary
    Column(
        modifier = Modifier
            .weight(1f)
            .height(64.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = tint,
            modifier = Modifier.size(24.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = tint,
            maxLines = 1,
            overflow = TextOverflow.Clip,
        )
    }
}
