package com.godseyeview.layers

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.godseyeview.R
import com.godseyeview.core.ui.GevColors
import com.godseyeview.core.ui.glassPanel
import com.godseyeview.layerapi.FollowTarget

/** Detail card in upstream v0.3.0's context-card presentation (Q23;
 *  upstream ContextView.vue / EntityCard.vue): a glass panel, the layer
 *  named as a mono kicker over the title, fields as dim mono labels with
 *  primary values. Rendered by the core from the layer-supplied Entity —
 *  layers never draw their own card (unchanged plumbing). */
@Composable
fun EntityCard(
    manager: LayerManager,
    modifier: Modifier = Modifier,
) {
    val entity = manager.selectedEntity ?: return
    val following = manager.followTarget?.let {
        it.layerId == entity.layerId && it.entityId == entity.id
    } == true
    Surface(
        color = GevColors.Glass,
        modifier = modifier.fillMaxWidth().glassPanel(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = entity.layerId.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = GevColors.Accent,
                    )
                    Text(
                        text = entity.title,
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                TextButton(onClick = { manager.select(null) }) {
                    Text(stringResource(R.string.card_close))
                }
            }
            entity.cardFields.forEach { field ->
                Spacer(Modifier.height(6.dp))
                Row {
                    Text(
                        text = field.label.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = GevColors.TextSecondary,
                        modifier = Modifier.padding(end = 10.dp),
                    )
                    Text(
                        text = field.value,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            TextButton(
                onClick = {
                    if (following) {
                        manager.setFollow(null)
                    } else {
                        manager.setFollow(FollowTarget(entity.layerId, entity.id))
                    }
                },
            ) {
                Text(
                    stringResource(
                        if (following) R.string.card_stop_follow else R.string.card_follow,
                    ),
                )
            }
        }
    }
}
