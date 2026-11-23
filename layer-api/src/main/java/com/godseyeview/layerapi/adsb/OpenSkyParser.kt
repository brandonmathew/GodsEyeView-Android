package com.godseyeview.layerapi.adsb

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

/**
 * Parser for the OpenSky Network state-vector response
 * (`GET /api/states/all?extended=1`), ported faithfully from upstream
 * `normalizeOpenSkyAircraft` in `src/sources/live/aircraft.js`.
 *
 * State rows are positional:
 * 0 icao24, 1 callsign, 2 origin_country, 5 longitude, 6 latitude,
 * 7 baro_altitude (m), 8 on_ground, 9 velocity (m/s), 10 true_track,
 * 11 vertical_rate (m/s), 13 geo_altitude (m), 14 squawk,
 * 17 category (extended=1 only).
 */
object OpenSkyParser {

    fun parse(json: String): List<AdsbRecord> {
        val root = Json.parseToJsonElement(json).jsonObject
        val states = root["states"] as? JsonArray ?: return emptyList()
        return states.mapNotNull { row -> parseRow(row as? JsonArray ?: return@mapNotNull null) }
    }

    private fun parseRow(row: JsonArray): AdsbRecord? {
        fun at(index: Int) = row.getOrNull(index)

        val id = at(0).cleanTextOrNull()?.lowercase() ?: return null
        // Longitude [5] / latitude [6]: a row without either position is
        // skipped (upstream: contacts still broadcasting without a fix).
        val longitude = at(5).finiteDouble() ?: return null
        val latitude = at(6).finiteDouble() ?: return null
        if (latitude !in -90.0..90.0 || longitude !in -180.0..180.0) return null

        return AdsbRecord(
            id = id,
            callsign = at(1).cleanTextOrNull(),
            originCountry = at(2).cleanTextOrNull(),
            latitude = latitude,
            longitude = longitude,
            baroAltitudeM = at(7).finiteDouble(),
            geoAltitudeM = at(13).finiteDouble(),
            onGround = at(8).booleanOrNullValue() == true,
            speedMps = at(9).finiteDouble(),
            courseDeg = at(10).finiteDouble(),
            verticalRateMps = at(11).finiteDouble(),
            category = at(17)?.let { element ->
                // Extended category is numeric upstream (finite(row[17]));
                // keep its plain text form ("5", not "5.0").
                val number = element.finiteDouble()
                when {
                    number != null && number == number.toLong().toDouble() ->
                        number.toLong().toString()
                    number != null -> number.toString()
                    else -> element.cleanTextOrNull()
                }
            },
            typeCode = null,
            registration = null,
            operator = null,
            squawk = Squawk.normalize(at(14).rawTextOrNull()),
            // State vectors carry no ADS-B emergency field.
            emergency = null,
        )
    }
}
