package com.godseyeview.core.globe

import android.webkit.WebView

/** Kotlin side of the bridge. Kotlin owns state; the WebView only renders
 *  (plan, Architecture). Calls made before the page signals ready are queued
 *  and flushed in order once it does. */
class GlobeController {
    private var webView: WebView? = null
    private var ready = false
    private val pending = ArrayDeque<String>()

    fun attach(view: WebView) {
        webView = view
    }

    fun detach(view: WebView) {
        if (webView === view) {
            webView = null
            ready = false
            pending.clear()
        }
    }

    fun onPageReady(
        initialView: CameraViewState,
        initialBasemap: Basemap = Basemap.ESRI,
        initialQualityId: String = "balanced",
        initialIonToken: String? = null,
        initialGoogleKey: String? = null,
    ) {
        ready = true
        eval("window.GEV && window.GEV.setView(${initialView.toJson()})")
        if (!initialIonToken.isNullOrEmpty()) {
            // Token first: a saved photorealistic basemap needs it in place.
            eval("window.GEV && window.GEV.setIonToken(${org.json.JSONObject.quote(initialIonToken)})")
        }
        if (!initialGoogleKey.isNullOrEmpty()) {
            // v2 Q9 tier 3: the user's own Google key enables the direct
            // Google 3D tileset basemap (loader activates on selection).
            eval("window.GEV && window.GEV.setGoogleKey(${org.json.JSONObject.quote(initialGoogleKey)})")
        }
        if (initialBasemap != Basemap.ESRI) {
            // The page boots on Esri; re-apply the saved choice so the globe
            // and the basemap chip agree from the first frame (issue #18).
            eval("window.GEV && window.GEV.setBasemap('${initialBasemap.id}')")
        }
        eval("window.GEV && window.GEV.setQuality('$initialQualityId')")
        flush()
    }

    fun setView(view: CameraViewState) = eval("window.GEV && window.GEV.setView(${view.toJson()})")

    fun resetGlobe() = eval("window.GEV && window.GEV.resetGlobe()")

    fun setBasemap(basemap: Basemap) = eval("window.GEV && window.GEV.setBasemap('${basemap.id}')")

    fun setQuality(effectiveModeId: String) =
        eval("window.GEV && window.GEV.setQuality('$effectiveModeId')")

    /** Issue #19: hand the page the user's Cesium ion token (or clear it). */
    fun setIonToken(token: String?) =
        eval("window.GEV && window.GEV.setIonToken(${org.json.JSONObject.quote(token ?: "")})")

    /** v2 Q9 tier 3: hand the page the user's Google Maps key (or clear). */
    fun setGoogleKey(key: String?) =
        eval("window.GEV && window.GEV.setGoogleKey(${org.json.JSONObject.quote(key ?: "")})")

    /** Issue #33: search fly-to + temporary pin. */
    fun flyTo(longitude: Double, latitude: Double, heightMeters: Double) =
        eval(
            "window.GEV && window.GEV.flyTo(" +
                "${fmt(longitude)}, ${fmt(latitude)}, ${fmt(heightMeters)})",
        )

    fun showPin(longitude: Double, latitude: Double, label: String) =
        eval(
            "window.GEV && window.GEV.showPin(" +
                "${fmt(longitude)}, ${fmt(latitude)}, ${org.json.JSONObject.quote(label)})",
        )

    /** Issue #34: the My Location dot (separate from the search pin). */
    fun showLocationDot(longitude: Double, latitude: Double) =
        eval(
            "window.GEV && window.GEV.showLocationDot(" +
                "${fmt(longitude)}, ${fmt(latitude)})",
        )

    /** Issue #44: draw a layer's current drawables (layer-api v1
     *  primitives), replacing whatever the layer drew before. */
    fun renderLayer(layerId: String, drawables: List<com.godseyeview.layerapi.Drawable>) {
        val array = org.json.JSONArray()
        for (drawable in drawables) array.put(drawableJson(drawable))
        eval(
            "window.GEV && window.GEV.renderLayer(" +
                "${org.json.JSONObject.quote(layerId)}, $array)",
        )
    }

    fun clearLayer(layerId: String) =
        eval("window.GEV && window.GEV.clearLayer(${org.json.JSONObject.quote(layerId)})")

    /** Issue #44/#46: follow a layer entity with the camera (or stop). */
    fun setFollow(layerId: String, entityId: String?) =
        eval(
            "window.GEV && window.GEV.setFollow(" +
                (entityId?.let { org.json.JSONObject.quote("$layerId:$it") } ?: "null") +
                ")",
        )

    private fun drawableJson(
        drawable: com.godseyeview.layerapi.Drawable,
    ): org.json.JSONObject {
        fun pointJson(p: com.godseyeview.layerapi.GeoPoint) =
            org.json.JSONObject()
                .put("latitude", p.latitude)
                .put("longitude", p.longitude)
                .put("altitude", p.altitude)
        fun pointsJson(points: List<com.godseyeview.layerapi.GeoPoint>) =
            org.json.JSONArray().also { arr -> points.forEach { arr.put(pointJson(it)) } }
        return when (drawable) {
            is com.godseyeview.layerapi.MapPoint -> org.json.JSONObject()
                .put("type", "point")
                .put(
                    "entity",
                    org.json.JSONObject()
                        .put("id", drawable.entity.id)
                        .put("title", drawable.entity.title)
                        .put("position", pointJson(drawable.entity.position)),
                )
            is com.godseyeview.layerapi.Track -> org.json.JSONObject()
                .put("type", "track")
                .put("entityId", drawable.entityId)
                .put("points", pointsJson(drawable.points))
            is com.godseyeview.layerapi.PolygonRegion -> org.json.JSONObject()
                .put("type", "polygon")
                .put("entityId", drawable.entityId)
                .put("vertices", pointsJson(drawable.vertices))
            is com.godseyeview.layerapi.MapLabel -> org.json.JSONObject()
                .put("type", "label")
                .put("text", drawable.text)
                .put("position", pointJson(drawable.position))
        }
    }

    private fun fmt(value: Double): String =
        String.format(java.util.Locale.US, "%.6f", value)

    /** Issue #12 diagnostics: ask the page to send its current globe state. */
    fun reportDiagnostics() = eval("window.GEV && window.GEV.reportDiagnostics()")

    /** Issue #20: drop the WebView HTTP cache (imagery tiles). */
    fun clearTileCache() {
        val view = webView ?: return
        view.post { view.clearCache(true) }
    }

    private fun eval(js: String) {
        val view = webView
        if (view == null || !ready) {
            pending.addLast(js)
            return
        }
        view.post { view.evaluateJavascript(js, null) }
    }

    private fun flush() {
        val view = webView ?: return
        while (pending.isNotEmpty()) {
            val js = pending.removeFirst()
            view.post { view.evaluateJavascript(js, null) }
        }
    }
}
