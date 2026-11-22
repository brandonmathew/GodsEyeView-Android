package com.godseyeview.core.globe

import java.util.Locale

/** Camera state shared across the Kotlin<->JS bridge and persisted so the
 *  app reopens exactly where the user left it (plan Q18). Serialised by hand
 *  (no org.json) so the class stays pure and unit-testable off-device. */
data class CameraViewState(
    val longitude: Double,
    val latitude: Double,
    val heightMeters: Double,
    val headingDegrees: Double = 0.0,
    val pitchDegrees: Double = -90.0,
    val rollDegrees: Double = 0.0,
) {
    fun toJson(): String = String.format(
        Locale.US,
        "{\"longitude\":%.7f,\"latitude\":%.7f,\"height\":%.2f," +
            "\"heading\":%.4f,\"pitch\":%.4f,\"roll\":%.4f}",
        longitude, latitude, heightMeters, headingDegrees, pitchDegrees, rollDegrees,
    )

    companion object {
        /** Full Earth from orbit - the first-launch view (plan Q18). */
        val FULL_EARTH = CameraViewState(
            longitude = 0.0, latitude = 18.0, heightMeters = 22_000_000.0,
            headingDegrees = 0.0, pitchDegrees = -90.0, rollDegrees = 0.0,
        )

        private val NUMBER = Regex("\"(longitude|latitude|height|heading|pitch|roll)\"\\s*:\\s*(-?[0-9.eE+]+)")

        fun fromJson(json: String): CameraViewState? {
            val values = NUMBER.findAll(json).associate { it.groupValues[1] to it.groupValues[2].toDoubleOrNull() }
            val longitude = values["longitude"] ?: return null
            val latitude = values["latitude"] ?: return null
            val height = values["height"] ?: return null
            return CameraViewState(
                longitude = longitude,
                latitude = latitude,
                heightMeters = height,
                headingDegrees = values["heading"] ?: 0.0,
                pitchDegrees = values["pitch"] ?: -90.0,
                rollDegrees = values["roll"] ?: 0.0,
            )
        }
    }
}

enum class Basemap(val id: String) {
    ESRI("esri"), OSM("osm"), PHOTOREALISTIC("photorealistic"), GOOGLE_DIRECT("google");

    companion object {
        fun fromId(id: String?): Basemap = entries.firstOrNull { it.id == id } ?: ESRI
    }
}
