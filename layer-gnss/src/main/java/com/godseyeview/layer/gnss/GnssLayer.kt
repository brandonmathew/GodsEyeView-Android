package com.godseyeview.layer.gnss

import com.godseyeview.layerapi.Layer
import com.godseyeview.layerapi.LayerDataSource
import com.godseyeview.layerapi.ViewAnchor
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/** GNSS Integrity (Stage 2, issue #29). Fetches the adsb.lol regional
 *  snapshot anchored on the current view (whole degrees, 250 nm radius —
 *  upstream server/providers/gnssIntegrity.js), normalises it with
 *  [GnssNormalize] (upstream's normalizeGnssAircraft port) and pushes rows
 *  to the WebView's H3 module through [rowsSink] (JSON for
 *  GlobeController.sendLayerMessage). The JS module owns accumulation,
 *  binning and rendering, so fetch() returns no drawables. */
class GnssLayer(
    private val rowsSink: (String) -> Unit,
) : Layer {
    override val id: String = "gnss-integrity"
    override val title: String = "GNSS Integrity"
    override val refreshInterval = 60.seconds
    override val freshnessTtl = 2.minutes
    override val credit: String = "adsb.lol (ODbL) · gpsjam.org method"

    /** Renders in its JS module (issue #40: the core must not
     *  drawable-render this layer — see the Layer API contract). */
    override val rendersViaJsModule: Boolean = true

    override val dataSource: LayerDataSource = LayerDataSource {
        val lat = Math.round(ViewAnchor.latitude)
        val lon = Math.round(ViewAnchor.longitude)
        val url = "https://api.adsb.lol/v2/lat/$lat/lon/$lon/dist/250"
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 15_000
        connection.readTimeout = 15_000
        connection.setRequestProperty(
            "User-Agent", "GodsEyeView-Android/2.0 (layer-gnss)",
        )
        try {
            val code = connection.responseCode
            if (code == 429) {
                throw IllegalStateException("GNSS feed rate limited (HTTP 429)")
            }
            if (code != HttpURLConnection.HTTP_OK) {
                throw IllegalStateException("GNSS feed unavailable (HTTP $code)")
            }
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val payload = Json.parseToJsonElement(body).jsonObject
            val rows = GnssNormalize.rows(payload)
                ?: throw IllegalStateException("Malformed GNSS integrity snapshot")
            val message = buildJsonObject {
                put("type", "rows")
                put("observedAtMs", observedAtMs(payload))
                put("rows", buildJsonArray {
                    for (row in rows) {
                        add(buildJsonObject {
                            put("hex", row.hex)
                            put("lat", row.lat)
                            put("lon", row.lon)
                            put("degraded", row.degraded)
                        })
                    }
                })
            }
            rowsSink(message.toString())
        } finally {
            connection.disconnect()
        }
        emptyList()
    }

    private fun observedAtMs(payload: JsonObject): Long {
        val now = (payload["now"] as? JsonPrimitive)
            ?.takeIf { !it.isString }?.doubleOrNull?.takeIf { it.isFinite() }
            ?: return System.currentTimeMillis()
        return if (now > 10_000_000_000.0) now.toLong() else (now * 1000).toLong()
    }
}
