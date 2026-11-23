package com.godseyeview.layerapi

/** Layer drawing API v1 primitives (plan Q26): point, track, polygon, label.
 *  3D models and heatmaps arrive later through a versioned API extension. */
sealed interface Drawable {
    val layerId: String
}

/** Optional per-point styling (Stage 2, issue #24). Null fields keep the
 *  core's default rendering, so existing layers are unaffected. Colours
 *  are CSS hex strings ("#FF4444"), the form upstream's policies use. */
data class PointStyle(
    val colorHex: String? = null,
    val pixelSize: Int? = null,
    /** Bulk layers (thousands of points) turn labels off; upstream draws
     *  icons for the fleet and text only for the selected contact. */
    val showLabel: Boolean = true,
)

data class MapPoint(
    override val layerId: String,
    val entity: Entity,
    val style: PointStyle = PointStyle(),
) : Drawable

data class Track(
    override val layerId: String,
    val entityId: String,
    val points: List<GeoPoint>,
) : Drawable {
    init { require(points.size >= 2) { "a track needs at least 2 points" } }
}

data class PolygonRegion(
    override val layerId: String,
    val entityId: String,
    val vertices: List<GeoPoint>,
    /** Optional fill/outline colours (CSS hex); null keeps core defaults. */
    val fillColorHex: String? = null,
    val fillAlpha: Double = 0.25,
    val outlineColorHex: String? = null,
) : Drawable {
    init { require(vertices.size >= 3) { "a polygon needs at least 3 vertices" } }
}

data class MapLabel(
    override val layerId: String,
    val position: GeoPoint,
    val text: String,
) : Drawable
