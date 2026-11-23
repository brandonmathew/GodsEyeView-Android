package com.godseyeview.layerapi

import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin

/** Viewport filtering (Q25, issue #40): ONE core mechanism that limits
 *  what opted-in layers render to the area actually on screen.
 *
 *  The visible region from a camera at height h over a spherical Earth
 *  is a spherical cap: centre = the camera's sub-point, angular radius
 *  = acos(R / (R + h)) (the horizon), clamped to 90°. A point is in the
 *  cap when its angular distance from the centre is <= the radius.
 *  [ViewportState] owns the current cap, applies the shared quality
 *  profile's margin factor, and decides — with hysteresis — when a
 *  camera move is large enough to refilter, so panning does not churn
 *  renders or AIS resubscriptions. */

/** Mean Earth radius in metres (the horizon math's R). */
const val EARTH_RADIUS_M: Double = 6_371_000.0

/** A plain lat/lon rectangle, lon range normalised to -180..180 with
 *  minLon <= maxLon. A cap crossing the antimeridian yields TWO boxes. */
data class LatLonBox(
    val minLat: Double,
    val maxLat: Double,
    val minLon: Double,
    val maxLon: Double,
) {
    companion object {
        val GLOBAL = LatLonBox(-90.0, 90.0, -180.0, 180.0)
    }
}

/** A spherical cap: everything within [radiusDeg] of the centre. */
data class VisibleCap(
    val centerLatitude: Double,
    val centerLongitude: Double,
    val radiusDeg: Double,
) {
    init {
        require(centerLatitude in -90.0..90.0) { "cap centre latitude out of range" }
        require(radiusDeg in 0.0..90.0) { "cap radius out of range: $radiusDeg" }
    }

    val isGlobal: Boolean get() = radiusDeg >= 90.0

    /** Angular distance test on unit vectors — exact at the poles and
     *  across the antimeridian, where lat/lon arithmetic breaks. */
    fun contains(latitude: Double, longitude: Double): Boolean {
        if (isGlobal) return true
        return angularDistanceDeg(
            centerLatitude, centerLongitude, latitude, longitude,
        ) <= radiusDeg
    }

    fun contains(point: GeoPoint): Boolean = contains(point.latitude, point.longitude)

    /** This cap scaled by [factor] (the quality profile's margin),
     *  radius clamped to the 90° hemisphere. */
    fun withMargin(factor: Double): VisibleCap =
        copy(radiusDeg = minOf(90.0, radiusDeg * factor))

    /** Lat/lon bounding box(es) of this cap: one box normally, two when
     *  the box crosses the antimeridian, one full-longitude box when
     *  the cap reaches a pole, [LatLonBox.GLOBAL] at hemisphere size.
     *  The box is a fetch-side approximation; [contains] is the exact
     *  render-side test. */
    fun boundingBoxes(): List<LatLonBox> {
        if (isGlobal) return listOf(LatLonBox.GLOBAL)
        val minLat = maxOf(-90.0, centerLatitude - radiusDeg)
        val maxLat = minOf(90.0, centerLatitude + radiusDeg)
        if (minLat <= -90.0 || maxLat >= 90.0) {
            // Cap reaches a pole: every longitude is inside somewhere.
            return listOf(LatLonBox(minLat, maxLat, -180.0, 180.0))
        }
        // Max longitude deviation of a spherical cap (standard result):
        // sin(dLon) = sin(radius) / cos(centreLat).
        val sinDLon = sin(Math.toRadians(radiusDeg)) /
            cos(Math.toRadians(centerLatitude))
        if (sinDLon >= 1.0) {
            return listOf(LatLonBox(minLat, maxLat, -180.0, 180.0))
        }
        val dLon = Math.toDegrees(asin(sinDLon))
        val minLon = centerLongitude - dLon
        val maxLon = centerLongitude + dLon
        return when {
            minLon < -180.0 -> listOf(
                LatLonBox(minLat, maxLat, minLon + 360.0, 180.0),
                LatLonBox(minLat, maxLat, -180.0, maxLon),
            )
            maxLon > 180.0 -> listOf(
                LatLonBox(minLat, maxLat, minLon, 180.0),
                LatLonBox(minLat, maxLat, -180.0, maxLon - 360.0),
            )
            else -> listOf(LatLonBox(minLat, maxLat, minLon, maxLon))
        }
    }

    companion object {
        /** The cap visible from a camera [heightMeters] above the
         *  ellipsoid over the sub-point (centreLat, centreLon):
         *  radius = acos(R / (R + h)), clamped to 90°. */
        fun fromCameraHeight(
            centerLatitude: Double,
            centerLongitude: Double,
            heightMeters: Double,
        ): VisibleCap {
            val h = maxOf(0.0, heightMeters)
            val cosRadius = EARTH_RADIUS_M / (EARTH_RADIUS_M + h)
            val radiusDeg = Math.toDegrees(acos(cosRadius.coerceIn(-1.0, 1.0)))
            return VisibleCap(
                centerLatitude,
                normalizeLongitude(centerLongitude),
                minOf(90.0, radiusDeg),
            )
        }

        val GLOBAL = VisibleCap(0.0, 0.0, 90.0)
    }
}

/** Great-circle angular distance between two lat/lon points, degrees. */
fun angularDistanceDeg(
    lat1: Double,
    lon1: Double,
    lat2: Double,
    lon2: Double,
): Double {
    val p1 = Math.toRadians(lat1)
    val p2 = Math.toRadians(lat2)
    val y1 = cos(p1) * cos(Math.toRadians(lon1))
    val z1 = cos(p1) * sin(Math.toRadians(lon1))
    val x1 = sin(p1)
    val y2 = cos(p2) * cos(Math.toRadians(lon2))
    val z2 = cos(p2) * sin(Math.toRadians(lon2))
    val x2 = sin(p2)
    val dot = (x1 * x2 + y1 * y2 + z1 * z2).coerceIn(-1.0, 1.0)
    return Math.toDegrees(acos(dot))
}

/** Normalise a longitude to -180..180. */
fun normalizeLongitude(lon: Double): Double {
    var l = lon % 360.0
    if (l > 180.0) l -= 360.0
    if (l < -180.0) l += 360.0
    return l
}

/** Shared viewport state (Q25): the latest camera cap and the FILTER
 *  cap (camera cap x the quality profile's margin) that opted-in
 *  layers are currently filtered by. Before the first camera report
 *  the filter cap is null, meaning "no filtering" — everything renders,
 *  which is the only honest state before we know where the user is
 *  looking.
 *
 *  Hysteresis: a camera update triggers a refilter only when the
 *  centre moved more than [CENTER_MOVE_FRACTION] of the current filter
 *  radius, or the (margined) radius changed by more than
 *  [RADIUS_CHANGE_FRACTION]. A layer poll ([onPoll]) always refreshes
 *  the filter cap from the latest camera. */
object ViewportState {
    /** Centre move, as a fraction of the filter radius, that refilters. */
    const val CENTER_MOVE_FRACTION: Double = 0.25

    /** Relative radius change that refilters. */
    const val RADIUS_CHANGE_FRACTION: Double = 0.20

    @Volatile
    var cameraCap: VisibleCap? = null
        private set

    @Volatile
    var filterCap: VisibleCap? = null
        private set

    private fun margined(cap: VisibleCap): VisibleCap =
        cap.withMargin(QualityProfileHolder.caps.viewportMarginFactor)

    /** Feed a settled camera report. Returns true when the hysteresis
     *  says "refilter now" (and updates [filterCap]); false when the
     *  move is inside the hysteresis band and the existing filter cap
     *  stands. */
    @Synchronized
    fun updateCamera(
        latitude: Double,
        longitude: Double,
        heightMeters: Double,
    ): Boolean {
        val cap = VisibleCap.fromCameraHeight(latitude, longitude, heightMeters)
        cameraCap = cap
        val current = filterCap
        if (current == null) {
            filterCap = margined(cap)
            return true
        }
        val newRadius = margined(cap).radiusDeg
        val centreMoved = angularDistanceDeg(
            current.centerLatitude, current.centerLongitude,
            cap.centerLatitude, cap.centerLongitude,
        )
        val radiusChanged = if (current.radiusDeg > 0.0) {
            kotlin.math.abs(newRadius - current.radiusDeg) / current.radiusDeg
        } else {
            1.0
        }
        return if (
            centreMoved > CENTER_MOVE_FRACTION * current.radiusDeg ||
            radiusChanged > RADIUS_CHANGE_FRACTION
        ) {
            filterCap = margined(cap)
            true
        } else {
            false
        }
    }

    /** A scheduled layer poll arrived: refresh the filter cap from the
     *  latest camera unconditionally. Returns true when the filter cap
     *  actually changed. */
    @Synchronized
    fun onPoll(): Boolean {
        val cap = cameraCap ?: return false
        val newFilter = margined(cap)
        if (newFilter == filterCap) return false
        filterCap = newFilter
        return true
    }

    /** Test hook: forget all camera/filter state. */
    @Synchronized
    fun reset() {
        cameraCap = null
        filterCap = null
    }
}
