package com.godseyeview.layer.vessels

import com.godseyeview.layerapi.CardField
import com.godseyeview.layerapi.Entity
import com.godseyeview.layerapi.GeoPoint
import com.godseyeview.layerapi.Layer
import com.godseyeview.layerapi.LayerDataSource
import com.godseyeview.layerapi.MapPoint
import com.godseyeview.layerapi.PointStyle
import com.godseyeview.layerapi.LatLonBox
import com.godseyeview.layerapi.QualityProfileHolder
import com.godseyeview.layerapi.ViewportState
import com.godseyeview.layerapi.VisibleCap
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.time.Duration.Companion.seconds

/** Live Vessels (Stage 2, issue #28) — live AIS positions streamed from
 *  AISStream over a per-phone WebSocket (plan Q2: no backend, the user's
 *  own key from [keyProvider], read lazily so a key added in Power Up is
 *  picked up on the next enable). The socket opens in [onEnabled]; the
 *  core's refresh polls [dataSource], which snapshots the store the
 *  socket thread keeps merging into. */
class VesselsLayer(
    private val keyProvider: () -> String?,
) : Layer {
    override val id: String = "ais-live-vessels"
    override val title: String = "Live Vessels"
    override val refreshInterval = 30.seconds
    override val freshnessTtl = 90.seconds
    override val credit: String = "AISStream"

    internal val store = VesselStore()
    internal val client = AisStreamClient(store)

    /** Q25 (issue #40): the core cap-filters this layer's snapshot
     *  before render, and the AIS subscription itself follows the
     *  filter cap's bounding box (see [onViewportChanged]). */
    override val viewportFiltered: Boolean = true

    /** The subscription area for the current filter cap: its bounding
     *  box(es) — two on an antimeridian wrap — or the global box
     *  before the first camera report. */
    internal fun desiredBoxes(): List<LatLonBox> =
        ViewportState.filterCap?.boundingBoxes()?.takeIf { it.isNotEmpty() }
            ?: listOf(LatLonBox.GLOBAL)

    override fun onViewportChanged(filterCap: VisibleCap?) {
        client.updateBoundingBoxes(desiredBoxes())
    }

    override fun onEnabled() {
        val key = keyProvider()?.takeIf { it.isNotBlank() } ?: return
        client.updateBoundingBoxes(desiredBoxes())
        client.start(key)
    }

    override fun onDisabled() {
        client.stop()
    }

    override val dataSource: LayerDataSource = LayerDataSource {
        val key = keyProvider()
        if (key.isNullOrBlank()) {
            throw IllegalStateException("AISStream key needed — add it in Power Up")
        }
        // Q25 poll path: the core refreshes the filter cap before this
        // fetch runs; keep the subscription area in step (no-op when
        // the boxes did not change).
        client.updateBoundingBoxes(desiredBoxes())
        if (!client.everReceived) {
            throw IllegalStateException("Connecting to AISStream…")
        }
        toPoints(
            records = store.snapshot(System.currentTimeMillis()),
            cap = QualityProfileHolder.caps.maxPointsPerLayer,
        )
    }
}

private val UTC_TIME: DateTimeFormatter =
    DateTimeFormatter.ofPattern("HH:mm:ss 'UTC'", Locale.US).withZone(ZoneOffset.UTC)

/** Map store records to drawables, capped at [cap] (the shared quality
 *  profile's per-layer point cap). Internal so tests can drive the exact
 *  mapping fetch() returns. */
internal fun toPoints(records: List<VesselRecord>, cap: Int): List<MapPoint> =
    records.asSequence()
        .filter {
            val la = it.lat
            val lo = it.lon
            la != null && lo != null && la in -90.0..90.0 && lo in -180.0..180.0
        }
        .take(cap.coerceAtLeast(0))
        .map { record ->
            val kind = vesselKind(record.shipType)
            MapPoint(
                layerId = "ais-live-vessels",
                entity = Entity(
                    id = record.mmsi.toString(),
                    layerId = "ais-live-vessels",
                    position = GeoPoint(record.lat!!, record.lon!!),
                    title = record.name ?: "MMSI ${record.mmsi}",
                    cardFields = cardFields(record),
                ),
                style = PointStyle(colorHex = kind.colorHex, pixelSize = 9, showLabel = false),
            )
        }
        .toList()

/** Detail-card fields for one vessel; only fields actually known. */
internal fun cardFields(record: VesselRecord): List<CardField> {
    val fields = mutableListOf<CardField>()
    fields += CardField("MMSI", record.mmsi.toString())
    record.shipType?.let { fields += CardField("Type", vesselTypeLabel(it)) }
    record.sogKn?.let { fields += CardField("Speed", String.format(Locale.US, "%.1f kn", it)) }
    record.cogDeg?.let { fields += CardField("Course", "${Math.round(it).toInt() % 360}°") }
    record.headingDeg?.let { fields += CardField("Heading", "$it°") }
    record.destination?.let { fields += CardField("Destination", it) }
    record.callsign?.let { fields += CardField("Callsign", it) }
    record.imo?.let { fields += CardField("IMO", it.toString()) }
    if (record.lastReportMs > 0) {
        fields += CardField("Last report", UTC_TIME.format(Instant.ofEpochMilli(record.lastReportMs)))
    }
    return fields
}
