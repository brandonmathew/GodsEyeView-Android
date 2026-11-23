package com.godseyeview.layer.gnss

import com.godseyeview.layerapi.Layer
import com.godseyeview.layerapi.LayerDataSource
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/** GNSS Integrity (Stage 2, issue #29) — shell registered by the Stage
 *  2 infra change (issue #24). The GNSS implementation fetches the
 *  adsb.lol regional snapshot, normalises it (upstream's
 *  normalizeGnssAircraft port) and pushes rows to the WebView's H3
 *  module through [rowsSink] (JSON for GlobeController
 *  .sendLayerMessage). */
class GnssLayer(
    private val rowsSink: (String) -> Unit,
) : Layer {
    override val id: String = "gnss-integrity"
    override val title: String = "GNSS Integrity"
    override val refreshInterval = 60.seconds
    override val freshnessTtl = 2.minutes
    override val credit: String = "adsb.lol (ODbL) · gpsjam.org method"
    override val dataSource: LayerDataSource = LayerDataSource { emptyList() }

}
