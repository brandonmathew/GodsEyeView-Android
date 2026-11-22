package com.godseyeview.layers

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.godseyeview.R
import com.godseyeview.core.ui.GevColors
import com.godseyeview.layerapi.FetchState
import com.godseyeview.layerapi.Layer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** The Data Layers panel, presented the way upstream v0.3.0's
 *  layerPanel.js presents it (Q23): layers grouped under the original's
 *  PANEL_GROUPS headings in the original's order, with a layer finder
 *  filter (upstream's LayerFinder). Presentation order and grouping are
 *  a panel concern — exactly as upstream keeps PANEL_GROUPS in the panel
 *  code, independent of registration order. Rows keep the core's fetch
 *  state line and credit from beta.1's manager plumbing. */
private val PANEL_GROUPS: List<Pair<String, List<String>>> = listOf(
    "Movement" to listOf(
        "satellites", "flights", "military", "local-adsb",
        "ais-live-vessels", "traffic", "transit", "rail", "bikeshare",
    ),
    "Cameras" to listOf("cctv", "recent-imagery", "street-level"),
    "Infrastructure" to listOf(
        "alpr-cameras", "military-installations", "drone-airspace",
        "local-datacenters", "telegeography-submarine-cables",
        "local-dams", "sea-marks", "ski-pistes",
    ),
    "Events" to listOf(
        "rocket-launches", "earthquakes", "local-firms",
        "fire-perimeters", "gnss-integrity", "s2-ghostmaps",
        "shipwrecks", "news",
    ),
    "Wildlife" to listOf("wildlife-sightings"),
    "Weather" to listOf(
        "air-quality", "wind", "weather-radar", "weather-satellite",
        "weather-lightning", "weather-cyclones",
    ),
    "Utilities" to listOf("directions", "osm-places"),
    "Audio" to listOf("radio"),
)

private const val OTHER_GROUP = "Other layers"
private const val LIBRARY_GROUP = "Library"

private fun groupOf(layer: Layer): String {
    // Upstream: Data Library layers follow every core group.
    if (layer.id.startsWith("library-")) return LIBRARY_GROUP
    return PANEL_GROUPS.firstOrNull { layer.id in it.second }?.first ?: OTHER_GROUP
}

private fun groupOrder(group: String): Int = when (group) {
    LIBRARY_GROUP -> PANEL_GROUPS.size + 1
    OTHER_GROUP -> PANEL_GROUPS.size
    else -> PANEL_GROUPS.indexOfFirst { it.first == group }.takeIf { it >= 0 }
        ?: PANEL_GROUPS.size
}

@Composable
fun LayerPanel(
    manager: LayerManager,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var finder by remember { mutableStateOf("") }
    val layers = manager.registry.all()
    val visible = layers.filter {
        finder.isBlank() || it.title.contains(finder, ignoreCase = true)
    }
    val grouped = visible.groupBy { groupOf(it) }
        .entries.sortedBy { groupOrder(it.key) }
    Surface(
        color = GevColors.BgDark,
        contentColor = GevColors.TextPrimary,
        modifier = modifier.fillMaxSize(),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            com.godseyeview.core.ui.GevTopBar(
                title = stringResource(R.string.layers_title),
                subtitle = stringResource(R.string.layers_kicker),
                onBack = onBack,
                backContentDescription = stringResource(R.string.settings_back),
            )
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
            ) {
            OutlinedTextField(
                value = finder,
                onValueChange = { finder = it },
                label = { Text(stringResource(R.string.layers_finder_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            if (layers.isEmpty()) {
                Text(
                    text = stringResource(R.string.layers_empty),
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else if (visible.isEmpty()) {
                Text(
                    text = stringResource(R.string.layers_finder_none),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            grouped.forEach { (group, groupLayers) ->
                Spacer(Modifier.height(14.dp))
                Text(
                    text = group.uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    color = GevColors.Accent,
                )
                Spacer(Modifier.height(6.dp))
                groupLayers.forEach { layer ->
                    LayerRow(layer = layer, manager = manager)
                    Spacer(Modifier.height(8.dp))
                }
            }
            }
        }
    }
}

@Composable
private fun LayerRow(layer: Layer, manager: LayerManager) {
    val enabled = manager.isEnabled(layer.id)
    val state = manager.states[layer.id]
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = layer.title,
                style = MaterialTheme.typography.titleMedium,
                color = GevColors.TextPrimary,
            )
            Text(
                text = stateText(enabled, state),
                style = MaterialTheme.typography.bodyMedium,
                color = GevColors.TextSecondary,
            )
            Text(
                text = layer.credit,
                style = MaterialTheme.typography.bodySmall,
                color = GevColors.TextSecondary,
            )
        }
        Switch(
            checked = enabled,
            onCheckedChange = { manager.setEnabled(layer, it) },
        )
    }
}

@Composable
private fun stateText(enabled: Boolean, state: FetchState?): String {
    if (!enabled) return stringResource(R.string.layers_off)
    val timeFormat = SimpleDateFormat("HH:mm", Locale.US)
    return when (state) {
        is FetchState.Fresh ->
            stringResource(R.string.layers_updated, timeFormat.format(Date(state.fetchedAtMillis)))
        is FetchState.Stale ->
            stringResource(R.string.layers_stale, timeFormat.format(Date(state.fetchedAtMillis)))
        is FetchState.Failed ->
            state.message?.let { stringResource(R.string.layers_failed_detail, it) }
                ?: stringResource(R.string.layers_failed)
        FetchState.Loading -> stringResource(R.string.layers_updating)
        else -> stringResource(R.string.layers_updating)
    }
}
