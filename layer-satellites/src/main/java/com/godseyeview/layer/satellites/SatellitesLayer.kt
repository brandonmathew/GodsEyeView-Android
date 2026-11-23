package com.godseyeview.layer.satellites

import com.godseyeview.layerapi.Layer
import com.godseyeview.layerapi.LayerDataSource
import java.io.File
import kotlin.time.Duration.Companion.hours

/** Satellites (Stage 2, issue #27) — shell registered by the Stage 2
 *  infra change (issue #24). The satellites implementation fetches and
 *  caches CelesTrak TLEs under [cacheDir] and pushes the catalog to the
 *  WebView's satellite.js module through [catalogSink] (a JSON payload
 *  for GlobeController.sendLayerMessage). */
class SatellitesLayer(
    private val cacheDir: File,
    private val catalogSink: (String) -> Unit,
) : Layer {
    override val id: String = "satellites"
    override val title: String = "Satellites"
    override val refreshInterval = 6.hours
    override val freshnessTtl = 26.hours
    override val credit: String = "CelesTrak"
    override val dataSource: LayerDataSource = LayerDataSource { emptyList() }

}
