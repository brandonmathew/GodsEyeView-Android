package com.godseyeview.layers

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.godseyeview.layerapi.FollowTarget

/** Stage 4 generic detail card (plan Q28, issue #46): rendered by the core
 *  from the layer-supplied Entity - title + card fields, Follow toggle,
 *  Close. Layers never draw their own card. */
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
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
        shape = RoundedCornerShape(16.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = entity.title,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { manager.select(null) }) {
                    Text(stringResource(R.string.card_close))
                }
            }
            entity.cardFields.forEach { field ->
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "${field.label}: ${field.value}",
                    style = MaterialTheme.typography.bodyMedium,
                )
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
