package com.godseyeview.layerapi

/** WGS84 point. Altitude in metres above the ellipsoid. */
data class GeoPoint(val latitude: Double, val longitude: Double, val altitude: Double = 0.0) {
    init {
        require(latitude in -90.0..90.0) { "latitude out of range: $latitude" }
        require(longitude in -180.0..180.0) { "longitude out of range: $longitude" }
    }
}

/** One field of a generic detail card, supplied by a layer for its entity. */
data class CardField(val label: String, val value: String)

/** Anything a layer can place on the globe that the user can tap. */
data class Entity(
    val id: String,
    val layerId: String,
    val position: GeoPoint,
    val title: String,
    val cardFields: List<CardField> = emptyList(),
)
