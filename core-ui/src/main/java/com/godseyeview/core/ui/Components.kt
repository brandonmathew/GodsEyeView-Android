package com.godseyeview.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp

/** Shell chrome modelled on upstream v0.3.0's SceneChromeView.vue and
 *  foundation.css (Q23): glass panels (rgba(12,12,20,0.72), 1dp hairline
 *  border, 16dp radius), the GOD'S EYE VIEW wordmark with the accent on
 *  VIEW, the NO PLACE LEFT BEHIND subtitle, mono kickers, and a glass
 *  command dock. Replaces v1's pill chips and twin pill bars. */

@Composable
fun Modifier.glassPanel(): Modifier = this
    .clip(RoundedCornerShape(16.dp))
    .background(GevColors.Glass)
    .border(1.dp, GevColors.GlassBorder, RoundedCornerShape(16.dp))

/** Small letterspaced mono label, e.g. "ACTIVE STYLE", "MISSION CONTROL". */
@Composable
fun Kicker(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = GevColors.TextSecondary,
        modifier = modifier,
    )
}

/** The title bar (SceneChromeView #title-bar): wordmark + subtitle. */
@Composable
fun Wordmark(modifier: Modifier = Modifier) {
    Column(modifier = modifier.glassPanel().padding(horizontal = 14.dp, vertical = 10.dp)) {
        Text(
            text = buildAnnotatedString {
                append("GOD'S EYE ")
                withStyle(SpanStyle(color = GevColors.Accent)) { append("VIEW") }
            },
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = GevColors.TextPrimary,
        )
        Text(
            text = "NO PLACE LEFT BEHIND",
            style = MaterialTheme.typography.labelSmall,
            color = GevColors.TextSecondary,
        )
    }
}

/** Translucent chip that sits over the globe (basemap status, ACTIVE STYLE). */
@Composable
fun GlobeChip(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = GevColors.TextPrimary,
        modifier = modifier
            .glassPanel()
            .padding(horizontal = 14.dp, vertical = 8.dp),
    )
}

/** A labelled cell of the command dock: mono uppercase, accent when active. */
@Composable
private fun DockButton(label: String, onClick: () -> Unit) {
    TextButton(onClick = onClick) {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = GevColors.TextPrimary,
        )
    }
}

/** Bottom command dock over the globe: the core's actions in one glass
 *  bar, as upstream's command dock. Two rows on phones so nothing
 *  overflows: actions, then camera/system controls. */
@Composable
fun GlobeControlBar(
    onResetGlobe: () -> Unit,
    resetLabel: String,
    modifier: Modifier = Modifier,
    onGlobeStatus: (() -> Unit)? = null,
    statusLabel: String = "",
    onSettings: (() -> Unit)? = null,
    settingsLabel: String = "",
) {
    Row(
        modifier = modifier
            .glassPanel()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DockButton(resetLabel, onResetGlobe)
        if (onGlobeStatus != null) {
            DockButton(statusLabel, onGlobeStatus)
        }
        if (onSettings != null) {
            DockButton(settingsLabel, onSettings)
        }
    }
}

@Composable
fun GlobeActionBar(
    modifier: Modifier = Modifier,
    onSearch: (() -> Unit)? = null,
    searchLabel: String = "",
    onMyLocation: (() -> Unit)? = null,
    myLocationLabel: String = "",
    onShare: (() -> Unit)? = null,
    shareLabel: String = "",
    onLayers: (() -> Unit)? = null,
    layersLabel: String = "",
    onPowerUp: (() -> Unit)? = null,
    powerUpLabel: String = "",
) {
    Row(
        modifier = modifier
            .glassPanel()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onSearch != null) DockButton(searchLabel, onSearch)
        if (onMyLocation != null) DockButton(myLocationLabel, onMyLocation)
        if (onShare != null) DockButton(shareLabel, onShare)
        if (onLayers != null) DockButton(layersLabel, onLayers)
        if (onPowerUp != null) {
            TextButton(onClick = onPowerUp) {
                Text(
                    text = powerUpLabel.uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    color = GevColors.Accent,
                )
            }
        }
    }
}
