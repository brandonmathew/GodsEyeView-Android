package com.godseyeview.layer.military

import com.godseyeview.layerapi.adsb.AdsbRecord
import com.godseyeview.layerapi.adsb.ReadsbParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class MilitaryLayerTest {

    // A mil-shaped adsb.lol payload (readsb rows, military fields such
    // as ownOp / type / registration), parsed through the shared codec.
    private val milFixture = """
        {
          "now": 1700000000.0,
          "ac": [
            {"hex": "AE1234", "flight": "RCH123 ", "lat": 38.0, "lon": -97.0,
             "alt_baro": 25000, "alt_geom": 25200, "gs": 400.0, "track": 180.0,
             "baro_rate": 0, "t": "C17", "r": "02-1101",
             "ownOp": "United States Air Force", "squawk": "2000",
             "category": "A5"},
            {"hex": "ae5678", "lat": 50.0, "lon": 10.0,
             "alt_baro": 30000, "gs": 450.0, "track": 90.0,
             "squawk": "7700", "t": "F16"}
          ]
        }
    """.trimIndent()

    private fun record(
        id: String,
        callsign: String?,
        squawk: String? = "2000",
    ) = AdsbRecord(
        id = id, callsign = callsign, originCountry = null,
        latitude = 10.0, longitude = 20.0, baroAltitudeM = 1000.0,
        geoAltitudeM = null, onGround = false, speedMps = 100.0,
        courseDeg = 45.0, verticalRateMps = null, category = null,
        typeCode = "C17", registration = null, operator = null,
        squawk = squawk, emergency = null,
    )

    @Test fun `mil fixture parses and maps to amber points`() {
        val records = ReadsbParser.parse(milFixture, snapshotNowMs(milFixture))
        assertEquals(2, records.size)
        val points = toPoints(records, cap = 100)
        // The 7700 emergency sorts first and is red; the other is amber.
        assertEquals("ae5678", points[0].entity.id)
        assertEquals("#FF4444", points[0].style.colorHex)
        assertEquals("ae1234", points[1].entity.id)
        assertEquals("#FFB800", points[1].style.colorHex)
        assertEquals(10, points[1].style.pixelSize)
        assertFalse(points[1].style.showLabel)
        assertEquals("military", points[1].layerId)
        assertEquals("RCH123", points[1].entity.title)
        // 25000 ft = 7620 m from the codec.
        assertEquals(7620.0, points[1].entity.position.altitude, 1e-6)
    }

    @Test fun `mil card fields carry type operator and formatted units`() {
        val records = ReadsbParser.parse(milFixture, snapshotNowMs(milFixture))
        val reach = records.first { it.id == "ae1234" }
        val fields = MilitaryCard.fieldsFor(reach).associate { it.label to it.value }
        assertEquals("AE1234", fields["ICAO24"])
        assertEquals("C17", fields["Type"])
        assertEquals("02-1101", fields["Registration"])
        assertEquals("United States Air Force", fields["Operator"])
        assertEquals("7,620 m · 25,000 ft", fields["Altitude"])
        assertEquals("741 km/h · 400 kt", fields["Speed"])
        assertEquals("180°", fields["Heading"])
        assertEquals("+0.0 m/s", fields["Vertical rate"])
        assertEquals("Airborne", fields["Status"])
    }

    @Test fun `title falls back to uppercase hex and cap applies`() {
        val points = toPoints(
            listOf(record("abc999", null), record("def888", "VIPER1")),
            cap = 1,
        )
        assertEquals(1, points.size)
        assertEquals("ABC999", points[0].entity.title)
    }
}
