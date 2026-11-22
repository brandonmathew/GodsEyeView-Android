package com.godseyeview.search

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.Executors
import kotlin.math.cos
import kotlin.math.sqrt

/** One search result: a named point and the camera height that frames it. */
data class PlaceResult(
    val name: String,
    val detail: String,
    val longitude: Double,
    val latitude: Double,
    val heightMeters: Double,
)

/** Stage 3 keyless search (plan Q15): coordinates parse locally, a small
 *  bundled gazetteer resolves offline, Photon geocoding covers the rest,
 *  Nominatim is the fallback. No API key, no backend (Q13). */
object PlaceSearch {
    private val executor = Executors.newSingleThreadExecutor()

    /** Runs the full ladder off the calling thread; [onResults] fires once
     *  with bundled matches immediately merged ahead of remote ones. */
    fun search(query: String, onResults: (List<PlaceResult>) -> Unit) {
        parseCoordinates(query)?.let {
            onResults(listOf(it))
            return
        }
        val bundled = BundledPlaces.search(query)
        executor.execute {
            val remote = runCatching { photon(query) }
                .getOrNull()
                ?.takeIf { it.isNotEmpty() }
                ?: runCatching { nominatim(query) }.getOrNull()
                ?: emptyList()
            val merged = (bundled + remote).distinctBy {
                it.name.lowercase() + "@" +
                    (it.latitude * 10).toInt() + "," + (it.longitude * 10).toInt()
            }
            onResults(merged)
        }
        if (bundled.isNotEmpty()) onResults(bundled)
    }

    /** "21.3099, -157.8581" / "21.3099 -157.8581" - lat first; swaps when
     *  the first value can only be a longitude. */
    fun parseCoordinates(query: String): PlaceResult? {
        val parts = query.trim().split(Regex("[,\\s]+")).filter { it.isNotEmpty() }
        if (parts.size != 2) return null
        var first = parts[0].toDoubleOrNull() ?: return null
        var second = parts[1].toDoubleOrNull() ?: return null
        if (first !in -90.0..90.0 && first in -180.0..180.0 && second in -90.0..90.0) {
            val swap = first; first = second; second = swap
        }
        if (first !in -90.0..90.0 || second !in -180.0..180.0) return null
        return PlaceResult(
            name = "Coordinates",
            detail = String.format(java.util.Locale.US, "%.5f, %.5f", first, second),
            longitude = second,
            latitude = first,
            heightMeters = 20_000.0,
        )
    }

    private fun photon(query: String): List<PlaceResult> {
        val url = "https://photon.komoot.io/api/?limit=8&q=" +
            URLEncoder.encode(query, "UTF-8")
        val body = httpGet(url) ?: return emptyList()
        val features = JSONObject(body).optJSONArray("features") ?: return emptyList()
        val results = mutableListOf<PlaceResult>()
        for (i in 0 until features.length()) {
            val feature = features.getJSONObject(i)
            val coords = feature.getJSONObject("geometry").getJSONArray("coordinates")
            val props = feature.getJSONObject("properties")
            val name = props.optString("name").ifEmpty { props.optString("city") }
            if (name.isEmpty()) continue
            val detail = listOf(
                props.optString("city"), props.optString("state"), props.optString("country"),
            ).filter { it.isNotEmpty() && it != name }.distinct().joinToString(", ")
            val extent = props.optJSONArray("extent")
            val height = if (extent != null && extent.length() == 4) {
                heightForExtent(
                    minLon = extent.getDouble(0), maxLat = extent.getDouble(1),
                    maxLon = extent.getDouble(2), minLat = extent.getDouble(3),
                )
            } else {
                6_000.0
            }
            results += PlaceResult(name, detail, coords.getDouble(0), coords.getDouble(1), height)
        }
        return results
    }

    private fun nominatim(query: String): List<PlaceResult> {
        val url = "https://nominatim.openstreetmap.org/search?format=jsonv2&limit=8&q=" +
            URLEncoder.encode(query, "UTF-8")
        val body = httpGet(url) ?: return emptyList()
        val array = JSONArray(body)
        val results = mutableListOf<PlaceResult>()
        for (i in 0 until array.length()) {
            val item = array.getJSONObject(i)
            val display = item.optString("display_name")
            val name = display.substringBefore(",")
            if (name.isEmpty()) continue
            val detail = display.substringAfter(",", "").trim()
            val bbox = item.optJSONArray("boundingbox")
            val height = if (bbox != null && bbox.length() == 4) {
                heightForExtent(
                    minLat = bbox.getString(0).toDouble(),
                    maxLat = bbox.getString(1).toDouble(),
                    minLon = bbox.getString(2).toDouble(),
                    maxLon = bbox.getString(3).toDouble(),
                )
            } else {
                6_000.0
            }
            results += PlaceResult(
                name, detail,
                item.getDouble("lon"), item.getDouble("lat"), height,
            )
        }
        return results
    }

    /** Camera height that frames an extent: roughly its diagonal, clamped. */
    private fun heightForExtent(
        minLon: Double,
        maxLon: Double,
        minLat: Double,
        maxLat: Double,
    ): Double {
        val midLat = (minLat + maxLat) / 2.0
        val dx = (maxLon - minLon) * 111_320.0 * cos(Math.toRadians(midLat))
        val dy = (maxLat - minLat) * 110_540.0
        return (sqrt(dx * dx + dy * dy) * 0.9).coerceIn(400.0, 1_500_000.0)
    }

    private fun httpGet(url: String): String? {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 8_000
        connection.readTimeout = 10_000
        connection.setRequestProperty("User-Agent", "GodsEyeViewAndroid (keyless search)")
        return try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) null
            else connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }
}
