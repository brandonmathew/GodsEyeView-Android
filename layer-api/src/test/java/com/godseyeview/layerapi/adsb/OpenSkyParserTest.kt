package com.godseyeview.layerapi.adsb

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenSkyParserTest {

    // Rows are positional state vectors (see OpenSkyParser). Row 2 has a
    // null position and must be skipped; row 3 squawks 7700.
    private val fixture = """
        {
          "time": 1700000000,
          "states": [
            ["ABC123", "UAL123  ", "United States", 1699999990, 1700000000,
             -157.8583, 21.3069, 3200.0, false, 236.11, 123.4, 12.5, null,
             3300.0, "2000", false, 0, 5],
            ["def456", "NOPOS", "Canada", 1699999990, 1700000000,
             null, null, 1000.0, false, 100.0, 90.0, 0.0, null,
             1100.0, "1200", false, 0, 2],
            ["789abc", "EMERG1", "Germany", 1699999990, 1700000000,
             8.6821, 50.1109, 5000.0, false, 200.0, 270.0, -3.0, null,
             5100.0, "7700", false, 0, null]
          ]
        }
    """.trimIndent()

    @Test fun `null position row is skipped`() {
        val records = OpenSkyParser.parse(fixture)
        assertEquals(2, records.size)
        assertEquals(listOf("abc123", "789abc"), records.map { it.id })
    }

    @Test fun `normal row maps by index`() {
        val record = OpenSkyParser.parse(fixture).first()
        assertEquals("abc123", record.id) // lowercased
        assertEquals("UAL123", record.callsign) // trimmed
        assertEquals("United States", record.originCountry)
        assertEquals(21.3069, record.latitude, 1e-9)
        assertEquals(-157.8583, record.longitude, 1e-9)
        assertEquals(3200.0, record.baroAltitudeM!!, 1e-9)
        assertEquals(3300.0, record.geoAltitudeM!!, 1e-9)
        assertFalse(record.onGround)
        assertEquals(236.11, record.speedMps!!, 1e-9)
        assertEquals(123.4, record.courseDeg!!, 1e-9)
        assertEquals(12.5, record.verticalRateMps!!, 1e-9)
        assertEquals("2000", record.squawk)
        assertEquals("5", record.category)
        assertNull(record.typeCode)
        assertNull(record.registration)
        assertNull(record.operator)
        assertNull(record.emergency)
        assertFalse(record.isEmergency)
    }

    @Test fun `emergency squawk row counts as emergency`() {
        val record = OpenSkyParser.parse(fixture).last()
        assertEquals("7700", record.squawk)
        assertTrue(Squawk.isEmergencySquawk(record.squawk))
        assertTrue(isEmergencySquawk(record.squawk))
        assertTrue(record.isEmergency)
        assertNull(record.category)
    }

    @Test fun `missing states yields empty list`() {
        assertTrue(OpenSkyParser.parse("""{"time": 1, "states": null}""").isEmpty())
        assertTrue(OpenSkyParser.parse("""{}""").isEmpty())
    }
}
