package com.godseyeview.layerapi.adsb

/**
 * One normalised ADS-B aircraft observation, shared by the civilian
 * (Live Flights, issue #25) and military (issue #26) layers.
 *
 * Ported from upstream `src/sources/live/aircraft.js`
 * (`normalizeOpenSkyAircraft` / `normalizeReadsbAircraft`): lengths are
 * metres, speeds metres/second, angles degrees. Nullable fields are
 * unknown observations, never zero-filled.
 */
data class AdsbRecord(
    /** Lowercase ICAO24 hex address; the stable entity id. */
    val id: String,
    val callsign: String?,
    val originCountry: String?,
    val latitude: Double,
    val longitude: Double,
    val baroAltitudeM: Double?,
    val geoAltitudeM: Double?,
    val onGround: Boolean,
    val speedMps: Double?,
    val courseDeg: Double?,
    val verticalRateMps: Double?,
    /** OpenSky extended category (as text) or the readsb category string. */
    val category: String?,
    val typeCode: String?,
    val registration: String?,
    val operator: String?,
    /** Normalised Mode A squawk (trimmed string), null when blank/absent. */
    val squawk: String?,
    /** Lowercased ADS-B emergency field, null when absent/blank. */
    val emergency: String?,
) {
    /**
     * True when this record counts as an emergency: an emergency squawk
     * (7500/7600/7700) or a non-null ADS-B emergency field.
     */
    val isEmergency: Boolean
        get() = Squawk.isEmergencySquawk(squawk) || emergency != null
}

/** Squawk helpers (upstream `src/data/aircraftEmergency.js`). */
object Squawk {
    private val EMERGENCY_SQUAWKS = setOf("7500", "7600", "7700")

    /** Trim a raw squawk; null when blank/absent. */
    fun normalize(raw: String?): String? = raw?.trim()?.takeIf { it.isNotEmpty() }

    fun isEmergencySquawk(squawk: String?): Boolean =
        squawk != null && squawk in EMERGENCY_SQUAWKS

    fun isEmergency(record: AdsbRecord): Boolean = record.isEmergency
}

/** Top-level alias for [Squawk.isEmergencySquawk]. */
fun isEmergencySquawk(squawk: String?): Boolean = Squawk.isEmergencySquawk(squawk)

/** Top-level alias for [Squawk.normalize]. */
fun normalizeSquawk(raw: String?): String? = Squawk.normalize(raw)
