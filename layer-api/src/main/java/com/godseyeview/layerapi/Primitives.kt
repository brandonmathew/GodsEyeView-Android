package com.godseyeview.layerapi

/** Layer drawing API v1 primitives (plan Q26): point, track, polygon, label.
 *  3D models and heatmaps arrive later through a versioned API extension. */
sealed interface Drawable {
    val layerId: String
}

data class MapPoint(
    override val layerId: String,
    val entity: Entity,
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
) : Drawable {
    init { require(vertices.size >= 3) { "a polygon needs at least 3 vertices" } }
}

data class MapLabel(
    override val layerId: String,
    val position: GeoPoint,
    val text: String,
) : Drawable
