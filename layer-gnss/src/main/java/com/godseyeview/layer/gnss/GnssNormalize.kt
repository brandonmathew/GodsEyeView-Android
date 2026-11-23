package com.godseyeview.layer.gnss

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull

/** One normalised aircraft integrity row (upstream `normalizeGnssAircraft`
 *  in src/layers/gnss/records.js). */
data class GnssRow(
    val hex: String,
    val lat: Double,
    val lon: Double,
    val nic: Int?,
    val nacp: Int?,
    val gpsLost: Boolean,
    val degraded: Boolean,
)

/** Port of upstream's `normalizeGnssAircraft` (records.js): reduces one
 *  adsb.lol / readsb `aircraft.json`-style payload to the integrity facts
 *  the GNSS layer needs. */
object GnssNormalize {
    private val HEX_PATTERN = Regex("^~?[0-9a-f]{6}$")
    private val QUALIFYING_TYPES = setOf("adsb_icao", "mlat")
    private const val NIC_THRESHOLD = 7
    private const val NACP_THRESHOLD = 8
    private const val MAX_POSITION_AGE_S = 60.0

    private fun finite(element: JsonElement?): Double? {
        val primitive = element as? JsonPrimitive ?: return null
        if (primitive.isString) return null
        return primitive.doubleOrNull?.takeIf { it.isFinite() }
    }

    private fun string(element: JsonElement?): String? {
        val primitive = element as? JsonPrimitive ?: return null
        return if (primitive.isString) primitive.content else null
    }

    private fun derived(aircraft: JsonObject, field: String): Boolean {
        fun contains(key: String): Boolean {
            val array = aircraft[key] as? JsonArray ?: return false
            return array.any { (it as? JsonPrimitive)?.content == field }
        }
        return contains("tisb") || contains("mlat")
    }

    /** Returns null when [payload] has no "ac" array. */
    fun rows(payload: JsonObject): List<GnssRow>? {
        val ac = payload["ac"] as? JsonArray ?: return null
        val rows = mutableListOf<GnssRow>()
        val seen = mutableSetOf<String>()
        for (element in ac) {
            val aircraft = element as? JsonObject ?: continue
            // Upstream tests the raw string against the pattern, which
            // only allows lowercase — replicate (no lowercasing first).
            val hex = string(aircraft["hex"]) ?: ""
            if (!HEX_PATTERN.matches(hex) || hex in seen) continue
            val type = string(aircraft["type"])
            if (type == null || type !in QUALIFYING_TYPES) continue
            val version = finite(aircraft["version"])
            if (version != 1.0 && version != 2.0) continue
            // alt_baro "ground" is a string, so finite() rejects it too.
            if (finite(aircraft["alt_baro"]) == null) continue
            val rawLat = finite(aircraft["lat"]) ?: continue
            val rawLon = finite(aircraft["lon"]) ?: continue
            val nic = finite(aircraft["nic"])
                ?.takeIf { !derived(aircraft, "nic") }?.toInt()
            val nacp = finite(aircraft["nac_p"])
                ?.takeIf { !derived(aircraft, "nac_p") }?.toInt()
            if (kotlin.math.abs(rawLat) > 90) continue
            if (kotlin.math.abs(rawLon) > 180) continue
            var lat = rawLat
            var lon = rawLon
            val age = finite(aircraft["seen_pos"])
            if (age == null || age < 0 || age > MAX_POSITION_AGE_S) continue
            val gpsOkBefore = finite(aircraft["gpsOkBefore"])
            val gpsLost = gpsOkBefore != null && gpsOkBefore > 0
            if (nic == null && nacp == null && !gpsLost) continue
            if (gpsLost) {
                val gpsOkLat = finite(aircraft["gpsOkLat"])
                val gpsOkLon = finite(aircraft["gpsOkLon"])
                if (gpsOkLat != null && kotlin.math.abs(gpsOkLat) <= 90 &&
                    gpsOkLon != null && kotlin.math.abs(gpsOkLon) <= 180
                ) {
                    lat = gpsOkLat
                    lon = gpsOkLon
                }
            }
            seen.add(hex)
            rows += GnssRow(
                hex = hex,
                lat = lat,
                lon = lon,
                nic = nic,
                nacp = nacp,
                gpsLost = gpsLost,
                degraded = gpsLost ||
                    (nacp != null && nacp < NACP_THRESHOLD) ||
                    (nic != null && nic < NIC_THRESHOLD &&
                        !(nic == 0 && nacp != null && nacp >= 10)),
            )
        }
        return rows
    }
}
