package com.godseyeview.layer.flights

import com.godseyeview.layerapi.CardField
import com.godseyeview.layerapi.Drawable
import com.godseyeview.layerapi.Entity
import com.godseyeview.layerapi.GeoPoint
import com.godseyeview.layerapi.Layer
import com.godseyeview.layerapi.LayerDataSource
import com.godseyeview.layerapi.MapPoint
import com.godseyeview.layerapi.PointStyle
import com.godseyeview.layerapi.QualityProfileHolder
import com.godseyeview.layerapi.ViewAnchor
import com.godseyeview.layerapi.adsb.AdsbRecord
import com.godseyeview.layerapi.adsb.OpenSkyParser
import com.godseyeview.layerapi.adsb.ReadsbParser
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.Locale
import kotlin.time.Duration.Companion.seconds

/**
 * Live Flights (Stage 2, issue #25).
 *
 * Primary source: OpenSky Network global state vectors
 * (`/api/states/all?extended=1`, upstream policy POLL 30 s). On any
 * OpenSky failure the layer falls back to the adsb.lol regional point
 * query around the current view anchor (upstream's regional fallback).
 * If both fail, the OpenSky exception is thrown and the core marks the
 * layer Failed/stale.
 */
class FlightsLayer(
    private val http: HttpClient = HttpClient(),
) : Layer {
    override val id: String = "flights"
    override val title: String = "Live Flights"
    override val refreshInterval = 30.seconds
    override val freshnessTtl = 90.seconds
    override val credit: String = "OpenSky Network · adsb.lol (ODbL)"

    private val enrichment = AdsbdbEnrichment(http)

    override val dataSource: LayerDataSource = LayerDataSource { fetchFlights() }

    override fun enrich(entity: Entity): List<CardField> = enrichment.enrich(entity)

    private fun fetchFlights(): List<Drawable> {
        val cap = QualityProfileHolder.caps.maxPointsPerLayer
        var openskyError: Exception? = null
        try {
            val body = http.get(OPENSKY_STATES_URL)
            val records = OpenSkyParser.parse(body)
            if (records.isEmpty()) {
                throw IllegalStateException("OpenSky returned no usable aircraft")
            }
            return toPoints(records, cap, id)
        } catch (e: Exception) {
            openskyError = e
        }

        // Regional fallback around the view anchor (250 nm, as upstream).
        try {
            val url = String.format(
                Locale.US,
                "https://api.adsb.lol/v2/lat/%.2f/lon/%.2f/dist/250",
                ViewAnchor.latitude,
                ViewAnchor.longitude,
            )
            val body = http.get(url)
            val records = ReadsbParser.parse(body, snapshotNowMs(body))
            return toPoints(records, cap, id)
        } catch (fallbackError: Exception) {
            throw openskyError ?: fallbackError
        }
    }

    companion object {
        const val OPENSKY_STATES_URL =
            "https://opensky-network.org/api/states/all?extended=1"
    }
}

/** adsb.lol `now` is epoch seconds (fractional); >1e10 means it is already ms. */
internal fun snapshotNowMs(json: String): Long {
    val now = try {
        Json.parseToJsonElement(json).jsonObject["now"]?.jsonPrimitive?.doubleOrNull
    } catch (e: Exception) {
        null
    } ?: return System.currentTimeMillis()
    return if (now > 1e10) now.toLong() else (now * 1000).toLong()
}

/**
 * Records → styled points: emergencies first (stable order otherwise),
 * capped at [cap]. White points, red for emergencies; labels off for
 * this bulk layer (upstream draws text only for the selected contact).
 */
internal fun toPoints(records: List<AdsbRecord>, cap: Int, layerId: String = "flights"): List<MapPoint> {
    val ordered = records.sortedByDescending { it.isEmergency } // stable sort
    return ordered.take(cap.coerceAtLeast(0)).map { record ->
        MapPoint(
            layerId = layerId,
            entity = Entity(
                id = record.id,
                layerId = layerId,
                position = GeoPoint(
                    latitude = record.latitude,
                    longitude = record.longitude,
                    altitude = record.baroAltitudeM ?: record.geoAltitudeM ?: 0.0,
                ),
                title = record.callsign?.takeIf { it.isNotBlank() }
                    ?: record.id.uppercase(Locale.US),
                cardFields = FlightCard.fieldsFor(record),
            ),
            style = PointStyle(
                colorHex = if (record.isEmergency) "#FF4444" else "#FFFFFF",
                pixelSize = 9,
                showLabel = false,
            ),
        )
    }
}
