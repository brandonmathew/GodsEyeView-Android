package com.godseyeview.layerapi.adsb

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadsbParserTest {

    // Row 2 is on the ground (alt_baro "ground"); row 3 has only
    // geom_rate and only true_heading.
    private val fixture = """
        {
          "now": 1700000000.0,
          "ac": [
            {"hex": "A1B2C3", "flight": "AAL100 ", "lat": 30.0, "lon": -90.0,
             "alt_baro": 10000, "alt_geom": 10200, "gs": 100.0, "track": 90.0,
             "baro_rate": 1000, "geom_rate": 900,
             "t": "B738", "r": "N123AA", "ownOp": "American Airlines",
             "squawk": "1200", "category": "A3"},
            {"hex": "def456", "flight": "GND1", "lat": 21.3, "lon": -157.8,
             "alt_baro": "ground", "gs": 0.0, "squawk": "  "},
            {"hex": "789abc", "lat": 50.0, "lon": 8.0,
             "alt_baro": 5000, "geom_rate": -500, "true_heading": 45.0,
             "emergency": "GENERAL"}
          ]
        }
    """.trimIndent()

    private val records = ReadsbParser.parse(fixture, snapshotNowMs = 1_700_000_000_000L)

    @Test fun `all positioned rows parse`() {
        assertEquals(3, records.size)
        assertEquals("a1b2c3", records[0].id) // hex lowercased
    }

    @Test fun `normal row converts imperial units`() {
        val record = records[0]
        assertEquals("AAL100", record.callsign) // trimmed
        assertEquals(10000 * 0.3048, record.baroAltitudeM!!, 1e-6)
        assertEquals(10200 * 0.3048, record.geoAltitudeM!!, 1e-6)
        assertEquals(100.0 * 0.514444, record.speedMps!!, 1e-6)
        assertEquals(90.0, record.courseDeg!!, 1e-9)
        // baro_rate wins over geom_rate, fpm -> m/s.
        assertEquals(1000 * 0.00508, record.verticalRateMps!!, 1e-6)
        assertEquals("B738", record.typeCode)
        assertEquals("N123AA", record.registration)
        assertEquals("American Airlines", record.operator)
        assertEquals("1200", record.squawk)
        assertEquals("A3", record.category)
        assertFalse(record.onGround)
        assertFalse(record.isEmergency)
    }

    @Test fun `ground row has no baro altitude and blank squawk is null`() {
        val record = records[1]
        assertTrue(record.onGround)
        assertNull(record.baroAltitudeM)
        assertNull(record.squawk)
    }

    @Test fun `geom rate only row falls back to geom_rate and true_heading`() {
        val record = records[2]
        assertEquals(-500 * 0.00508, record.verticalRateMps!!, 1e-6)
        assertEquals(45.0, record.courseDeg!!, 1e-9)
        assertEquals("general", record.emergency) // lowercased
        assertTrue(record.isEmergency)
    }

    @Test fun `rows without position or hex are skipped`() {
        val json = """{"ac": [{"hex": "abc123"}, {"lat": 1.0, "lon": 2.0}]}"""
        assertTrue(ReadsbParser.parse(json, 0L).isEmpty())
    }

    @Test fun `emergency squawks are exactly 7500 7600 7700`() {
        assertTrue(Squawk.isEmergencySquawk("7500"))
        assertTrue(Squawk.isEmergencySquawk("7600"))
        assertTrue(Squawk.isEmergencySquawk("7700"))
        assertFalse(Squawk.isEmergencySquawk("2000"))
        assertFalse(Squawk.isEmergencySquawk(null))
    }
}
