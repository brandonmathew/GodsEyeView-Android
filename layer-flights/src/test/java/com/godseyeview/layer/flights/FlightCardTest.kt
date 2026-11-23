package com.godseyeview.layer.flights

import com.godseyeview.layerapi.adsb.AdsbRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class FlightCardTest {

    private fun record(
        id: String = "abc123",
        callsign: String? = "UAL123",
        squawk: String? = "2000",
        emergency: String? = null,
        onGround: Boolean = false,
    ) = AdsbRecord(
        id = id,
        callsign = callsign,
        originCountry = "United States",
        latitude = 21.3069,
        longitude = -157.8583,
        baroAltitudeM = 3200.0,
        geoAltitudeM = 3300.0,
        onGround = onGround,
        speedMps = 236.11,
        courseDeg = 123.4,
        verticalRateMps = 12.5,
        category = "5",
        typeCode = "B738",
        registration = "N123UA",
        operator = "United Airlines",
        squawk = squawk,
        emergency = emergency,
    )

    @Test fun `fields are in order with formatted units`() {
        val fields = FlightCard.fieldsFor(record())
        assertEquals(
            listOf(
                "ICAO24", "Origin", "Type", "Registration", "Operator",
                "Altitude", "Speed", "Heading", "Vertical rate", "Squawk", "Status",
            ),
            fields.map { it.label },
        )
        val byLabel = fields.associate { it.label to it.value }
        assertEquals("ABC123", byLabel["ICAO24"])
        assertEquals("United States", byLabel["Origin"])
        assertEquals("B738", byLabel["Type"])
        // 3200 m = 10498.7 ft, rounded.
        assertEquals("3,200 m · 10,499 ft", byLabel["Altitude"])
        // 236.11 m/s = 850 km/h, 459 kt.
        assertEquals("850 km/h · 459 kt", byLabel["Speed"])
        assertEquals("123°", byLabel["Heading"])
        assertEquals("+12.5 m/s", byLabel["Vertical rate"])
        assertEquals("2000", byLabel["Squawk"])
        assertEquals("Airborne", byLabel["Status"])
    }

    @Test fun `negative vertical rate and on-ground status`() {
        val fields = FlightCard.fieldsFor(
            record(onGround = true).copy(verticalRateMps = -3.0),
        ).associate { it.label to it.value }
        assertEquals("-3.0 m/s", fields["Vertical rate"])
        assertEquals("On ground", fields["Status"])
    }

    @Test fun `unknown fields are omitted except icao and status`() {
        val sparse = AdsbRecord(
            id = "def456", callsign = null, originCountry = null,
            latitude = 0.0, longitude = 0.0, baroAltitudeM = null,
            geoAltitudeM = null, onGround = true, speedMps = null,
            courseDeg = null, verticalRateMps = null, category = null,
            typeCode = null, registration = null, operator = null,
            squawk = null, emergency = null,
        )
        assertEquals(
            listOf("ICAO24", "Status"),
            FlightCard.fieldsFor(sparse).map { it.label },
        )
    }

    @Test fun `emergencies sort first and cap applies`() {
        val normal1 = record(id = "111111", callsign = "NORM1")
        val normal2 = record(id = "222222", callsign = "NORM2")
        val emergency = record(id = "333333", callsign = "EMERG", squawk = "7700")
        val points = toPoints(listOf(normal1, normal2, emergency), cap = 2)
        assertEquals(2, points.size)
        assertEquals("333333", points[0].entity.id) // emergency first
        assertEquals("#FF4444", points[0].style.colorHex)
        assertEquals(9, points[0].style.pixelSize)
        assertFalse(points[0].style.showLabel)
        assertEquals("111111", points[1].entity.id) // stable order kept
        assertEquals("#FFFFFF", points[1].style.colorHex)
    }

    @Test fun `title falls back to uppercase hex without a callsign`() {
        val points = toPoints(listOf(record(callsign = null)), cap = 10)
        assertEquals("ABC123", points[0].entity.title)
        val named = toPoints(listOf(record()), cap = 10)
        assertEquals("UAL123", named[0].entity.title)
        assertEquals(3200.0, named[0].entity.position.altitude, 1e-9)
    }

    @Test fun `snapshot now converts seconds and passes through ms`() {
        assertEquals(1_700_000_000_500L, snapshotNowMs("""{"now": 1700000000.5}"""))
        assertEquals(1_700_000_000_500L, snapshotNowMs("""{"now": 1700000000500}"""))
    }
}
