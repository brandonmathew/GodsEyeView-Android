package com.godseyeview.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.unit.dp

/** Translucent chip that sits over the globe (app title, basemap status). */
@Composable
fun GlobeChip(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.82f))
            .padding(horizontal = 14.dp, vertical = 8.dp),
    )
}

/** Bottom control bar over the globe: the core's camera controls live here. */
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
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.82f))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = onResetGlobe) { Text(resetLabel) }
        if (onGlobeStatus != null) {
            TextButton(onClick = onGlobeStatus) { Text(statusLabel) }
        }
        if (onSettings != null) {
            TextButton(onClick = onSettings) { Text(settingsLabel) }
        }
    }
}

/** Stage 3 action bar (search / location / share), same pill styling,
 *  stacked above the main control bar so neither overflows on phones. */
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
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.82f))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onSearch != null) {
            TextButton(onClick = onSearch) { Text(searchLabel) }
        }
        if (onMyLocation != null) {
            TextButton(onClick = onMyLocation) { Text(myLocationLabel) }
        }
        if (onShare != null) {
            TextButton(onClick = onShare) { Text(shareLabel) }
        }
        if (onLayers != null) {
            TextButton(onClick = onLayers) { Text(layersLabel) }
        }
    }
}
