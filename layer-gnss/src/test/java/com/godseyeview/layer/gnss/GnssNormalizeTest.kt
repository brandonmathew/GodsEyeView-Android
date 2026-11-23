package com.godseyeview.layer.gnss

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Port checks for upstream's normalizeGnssAircraft (records.js). */
class GnssNormalizeTest {

    private fun payload(vararg aircraft: String): JsonObject =
        Json.parseToJsonElement(
            """{"ac":[${aircraft.joinToString(",")}]}""",
        ).jsonObject

    /** A valid, non-degraded aircraft; pass null to omit a field, or a raw
     *  JSON snippet in [extra] to add/replace fields. */
    private fun ac(
        hex: String? = "\"abc123\"",
        type: String? = "\"adsb_icao\"",
        version: String? = "2",
        altBaro: String? = "10000",
        lat: String? = "21.0",
        lon: String? = "-157.0",
        nic: String? = "8",
        nacp: String? = "9",
        seenPos: String? = "5",
        extra: String = "",
    ): String {
        val fields = mutableListOf<String>()
        if (hex != null) fields += "\"hex\":$hex"
        if (type != null) fields += "\"type\":$type"
        if (version != null) fields += "\"version\":$version"
        if (altBaro != null) fields += "\"alt_baro\":$altBaro"
        if (lat != null) fields += "\"lat\":$lat"
        if (lon != null) fields += "\"lon\":$lon"
        if (nic != null) fields += "\"nic\":$nic"
        if (nacp != null) fields += "\"nac_p\":$nacp"
        if (seenPos != null) fields += "\"seen_pos\":$seenPos"
        if (extra.isNotEmpty()) fields += extra
        return fields.joinToString(",", "{", "}")
    }

    @Test
    fun `degraded by NACp 5`() {
        val rows = GnssNormalize.rows(payload(ac(nacp = "5")))!!
        assertEquals(1, rows.size)
        assertEquals(5, rows[0].nacp)
        assertTrue(rows[0].degraded)
        assertFalse(rows[0].gpsLost)
    }

    @Test
    fun `not degraded with nic 8 and nac_p 9`() {
        val rows = GnssNormalize.rows(payload(ac(nic = "8", nacp = "9")))!!
        assertEquals(1, rows.size)
        assertEquals(8, rows[0].nic)
        assertEquals(9, rows[0].nacp)
        assertFalse(rows[0].degraded)
    }

    @Test
    fun `NIC 0 with NACp 10 is not degraded`() {
        val rows = GnssNormalize.rows(payload(ac(nic = "0", nacp = "10")))!!
        assertEquals(1, rows.size)
        assertFalse(rows[0].degraded)
    }

    @Test
    fun `NIC 0 with NACp 7 is degraded`() {
        val rows = GnssNormalize.rows(payload(ac(nic = "0", nacp = "7")))!!
        assertEquals(1, rows.size)
        assertTrue(rows[0].degraded)
    }

    @Test
    fun `version 3 is skipped`() {
        assertTrue(GnssNormalize.rows(payload(ac(version = "3")))!!.isEmpty())
    }

    @Test
    fun `type tisb is skipped`() {
        assertTrue(GnssNormalize.rows(payload(ac(type = "\"tisb\"")))!!.isEmpty())
    }

    @Test
    fun `mlat type qualifies`() {
        val rows = GnssNormalize.rows(payload(ac(type = "\"mlat\"")))!!
        assertEquals(1, rows.size)
    }

    @Test
    fun `alt_baro ground is skipped`() {
        assertTrue(
            GnssNormalize.rows(payload(ac(altBaro = "\"ground\"")))!!.isEmpty(),
        )
    }

    @Test
    fun `seen_pos 61 is skipped`() {
        assertTrue(GnssNormalize.rows(payload(ac(seenPos = "61")))!!.isEmpty())
    }

    @Test
    fun `nic listed in tisb array nulls nic and row is skipped without other evidence`() {
        val rows = GnssNormalize.rows(
            payload(ac(nacp = null, extra = "\"tisb\":[\"nic\"]")),
        )!!
        assertTrue(rows.isEmpty())
    }

    @Test
    fun `nic listed in tisb array nulls nic but keeps row when nac_p present`() {
        val rows = GnssNormalize.rows(
            payload(ac(nacp = "9", extra = "\"tisb\":[\"nic\"]")),
        )!!
        assertEquals(1, rows.size)
        assertNull(rows[0].nic)
        assertEquals(9, rows[0].nacp)
        assertFalse(rows[0].degraded)
    }

    @Test
    fun `nac_p listed in mlat array nulls nacp`() {
        val rows = GnssNormalize.rows(
            payload(ac(nic = "8", extra = "\"mlat\":[\"nac_p\"]")),
        )!!
        assertEquals(1, rows.size)
        assertNull(rows[0].nacp)
        assertFalse(rows[0].degraded)
    }

    @Test
    fun `gpsOkBefore relocates to last good position and degrades`() {
        val rows = GnssNormalize.rows(
            payload(
                ac(
                    nic = null, nacp = null,
                    extra = "\"gpsOkBefore\":120,\"gpsOkLat\":22.5,\"gpsOkLon\":-158.25",
                ),
            ),
        )!!
        assertEquals(1, rows.size)
        assertTrue(rows[0].gpsLost)
        assertTrue(rows[0].degraded)
        assertEquals(22.5, rows[0].lat, 1e-9)
        assertEquals(-158.25, rows[0].lon, 1e-9)
    }

    @Test
    fun `duplicate hex is deduped keeping first`() {
        val rows = GnssNormalize.rows(
            payload(ac(nacp = "5"), ac(nacp = "9", lat = "30.0")),
        )!!
        assertEquals(1, rows.size)
        assertEquals(5, rows[0].nacp)
        assertEquals(21.0, rows[0].lat, 1e-9)
    }

    @Test
    fun `uppercase hex does not match the raw pattern`() {
        assertTrue(
            GnssNormalize.rows(payload(ac(hex = "\"ABC123\"")))!!.isEmpty(),
        )
    }

    @Test
    fun `payload without ac array returns null`() {
        val noAc = Json.parseToJsonElement("""{"now":123}""").jsonObject
        assertNull(GnssNormalize.rows(noAc))
        val acNotArray = Json.parseToJsonElement("""{"ac":{}}""").jsonObject
        assertNull(GnssNormalize.rows(acNotArray))
    }
}
