package com.godseyeview.layer.military

import com.godseyeview.layerapi.Layer
import com.godseyeview.layerapi.LayerDataSource
import kotlin.time.Duration.Companion.seconds

/** Military Flights (Stage 2, issue #26) — shell registered by the
 *  Stage 2 infra change (issue #24); the military implementation fills
 *  in the adsb.lol /v2/mil data source and styling. */
class MilitaryLayer : Layer {
    override val id: String = "military"
    override val title: String = "Military Flights"
    override val refreshInterval = 15.seconds
    override val freshnessTtl = 60.seconds
    override val credit: String = "adsb.lol (ODbL)"
    override val dataSource: LayerDataSource = LayerDataSource { emptyList() }
}
