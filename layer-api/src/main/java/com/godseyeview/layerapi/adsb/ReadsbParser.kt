package com.godseyeview.layerapi.adsb

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

/**
 * Parser for readsb-style responses (adsb.lol `/v2/...`), ported
 * faithfully from upstream `normalizeReadsbAircraft` in
 * `src/sources/live/aircraft.js` plus `readAdsbVerticalRateFpm` in
 * `src/sources/adsbFields.js` (vertical rate = `baro_rate` else
 * `geom_rate`).
 *
 * readsb reports imperial units: altitudes in feet, ground speed in
 * knots, vertical rates in feet/minute. Conversions: ft→m ×0.3048,
 * kt→m/s ×0.514444, fpm→m/s ×0.00508.
 */
object ReadsbParser {

    /**
     * @param snapshotNowMs the payload's snapshot time (`now`), supplied
     * by the caller. The normalised record carries no timestamps, so it
     * is accepted for parity with upstream and future track support.
     */
    fun parse(json: String, snapshotNowMs: Long): List<AdsbRecord> {
        @Suppress("UNUSED_VARIABLE") val snapshot = snapshotNowMs
        val root = Json.parseToJsonElement(json).jsonObject
        val ac = root["ac"] as? JsonArray ?: return emptyList()
        return ac.mapNotNull { element -> parseRow(element as? JsonObject ?: return@mapNotNull null) }
    }

    private fun parseRow(row: JsonObject): AdsbRecord? {
        fun at(key: String) = row[key]

        val id = at("hex").cleanTextOrNull()?.lowercase() ?: return null
        val latitude = at("lat").finiteDouble() ?: return null
        val longitude = at("lon").finiteDouble() ?: return null
        if (latitude !in -90.0..90.0 || longitude !in -180.0..180.0) return null

        // alt_baro may be the string "ground" instead of a number.
        val onGround = at("alt_baro").cleanTextOrNull()?.lowercase() == "ground"
        val baroFt = at("alt_baro").finiteDouble()
        val geoFt = at("alt_geom").finiteDouble()
        val speedKts = at("gs").finiteDouble()
        // Vertical rate: baro_rate else geom_rate (adsbFields.js).
        val rateFpm = at("baro_rate").finiteDouble() ?: at("geom_rate").finiteDouble()

        return AdsbRecord(
            id = id,
            callsign = at("flight").cleanTextOrNull(),
            originCountry = null,
            latitude = latitude,
            longitude = longitude,
            baroAltitudeM = baroFt?.let { it * 0.3048 },
            geoAltitudeM = geoFt?.let { it * 0.3048 },
            onGround = onGround,
            speedMps = speedKts?.let { it * 0.514444 },
            // No track (parked, slow): the true heading, where the nose points.
            courseDeg = at("track").finiteDouble() ?: at("true_heading").finiteDouble(),
            verticalRateMps = rateFpm?.let { it * 0.00508 },
            category = at("category").rawTextOrNull(),
            typeCode = at("t").cleanTextOrNull(),
            registration = at("r").cleanTextOrNull(),
            operator = at("ownOp").cleanTextOrNull(),
            squawk = Squawk.normalize(at("squawk").rawTextOrNull()),
            emergency = at("emergency").cleanTextOrNull()?.lowercase(),
        )
    }
}
