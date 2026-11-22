package com.godseyeview.layers

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.godseyeview.R
import com.godseyeview.layerapi.FetchState
import com.godseyeview.layerapi.Layer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Stage 4 layer panel (issue #46): every registered layer with its fetch
 *  state and an on/off switch. Surface wrap for the content color (#27). */
@Composable
fun LayerPanel(
    manager: LayerManager,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val layers = manager.registry.all()
    Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = modifier.fillMaxSize(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.layers_title),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onBack) { Text(stringResource(R.string.settings_back)) }
            }
            Spacer(Modifier.height(12.dp))
            if (layers.isEmpty()) {
                Text(
                    text = stringResource(R.string.layers_empty),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            layers.forEach { layer ->
                LayerRow(layer = layer, manager = manager)
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun LayerRow(layer: Layer, manager: LayerManager) {
    val enabled = manager.isEnabled(layer.id)
    val state = manager.states[layer.id]
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = layer.title, style = MaterialTheme.typography.titleMedium)
            Text(
                text = stateText(enabled, state),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(text = layer.credit, style = MaterialTheme.typography.bodySmall)
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
