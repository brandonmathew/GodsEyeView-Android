package com.godseyeview.layer.flights

import com.godseyeview.layerapi.Layer
import com.godseyeview.layerapi.LayerDataSource
import kotlin.time.Duration.Companion.seconds

/** Live Flights (Stage 2, issue #25) — shell registered by the Stage 2
 *  infra change (issue #24); the flights implementation fills in the
 *  data source, styling, card fields and adsbdb enrichment. */
class FlightsLayer : Layer {
    override val id: String = "flights"
    override val title: String = "Live Flights"
    override val refreshInterval = 30.seconds
    override val freshnessTtl = 90.seconds
    override val credit: String = "OpenSky Network · adsb.lol (ODbL)"
    override val dataSource: LayerDataSource = LayerDataSource { emptyList() }
}
