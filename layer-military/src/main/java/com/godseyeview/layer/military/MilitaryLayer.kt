package com.godseyeview.layer.military

import com.godseyeview.layerapi.Drawable
import com.godseyeview.layerapi.Entity
import com.godseyeview.layerapi.GeoPoint
import com.godseyeview.layerapi.Layer
import com.godseyeview.layerapi.LayerDataSource
import com.godseyeview.layerapi.MapPoint
import com.godseyeview.layerapi.PointStyle
import com.godseyeview.layerapi.QualityProfileHolder
import com.godseyeview.layerapi.adsb.AdsbRecord
import com.godseyeview.layerapi.adsb.ReadsbParser
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.Locale
import kotlin.time.Duration.Companion.seconds

/**
 * Military Flights (Stage 2, issue #26).
 *
 * Source: adsb.lol `/v2/mil` (upstream policy POLL 15 s), parsed with
 * the shared readsb codec in layer-api. Throwing from fetch marks the
 * layer Failed/stale in the core.
 */
class MilitaryLayer(
    private val http: HttpClient = HttpClient(),
) : Layer {
    override val id: String = "military"
    override val title: String = "Military Flights"
    override val refreshInterval = 15.seconds
    override val freshnessTtl = 60.seconds
    override val credit: String = "adsb.lol (ODbL)"

    override val dataSource: LayerDataSource = LayerDataSource { fetchMilitary() }

    private fun fetchMilitary(): List<Drawable> {
        val body = http.get(MIL_URL)
        val records = ReadsbParser.parse(body, snapshotNowMs(body))
        return toPoints(records, QualityProfileHolder.caps.maxPointsPerLayer, id)
    }

    companion object {
        const val MIL_URL = "https://api.adsb.lol/v2/mil"
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
 * capped at [cap]. Amber points, red for emergencies; labels off for
 * this bulk layer. Title is the callsign, else the uppercase hex.
 */
internal fun toPoints(records: List<AdsbRecord>, cap: Int, layerId: String = "military"): List<MapPoint> {
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
                cardFields = MilitaryCard.fieldsFor(record),
            ),
            style = PointStyle(
                colorHex = if (record.isEmergency) "#FF4444" else "#FFB800",
                pixelSize = 10,
                showLabel = false,
            ),
        )
    }
}
