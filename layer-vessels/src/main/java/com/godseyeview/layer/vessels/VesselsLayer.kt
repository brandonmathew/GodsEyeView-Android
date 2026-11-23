package com.godseyeview.layer.vessels

import com.godseyeview.layerapi.Layer
import com.godseyeview.layerapi.LayerDataSource
import kotlin.time.Duration.Companion.seconds

/** Live Vessels (Stage 2, issue #28) — shell registered by the Stage 2
 *  infra change (issue #24). The vessels implementation opens the
 *  per-phone AISStream WebSocket in onEnabled using the user's key from
 *  [keyProvider] (KeyVault, read lazily so a key added in Power Up is
 *  picked up on the next enable). */
class VesselsLayer(
    private val keyProvider: () -> String?,
) : Layer {
    override val id: String = "ais-live-vessels"
    override val title: String = "Live Vessels"
    override val refreshInterval = 30.seconds
    override val freshnessTtl = 90.seconds
    override val credit: String = "AISStream"
    override val dataSource: LayerDataSource = LayerDataSource { emptyList() }

}
