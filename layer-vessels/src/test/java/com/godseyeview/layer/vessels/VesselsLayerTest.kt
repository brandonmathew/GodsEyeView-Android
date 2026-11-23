package com.godseyeview.layer.vessels

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class VesselsLayerTest {
    private fun record(
        mmsi: Long,
        name: String? = null,
        shipType: Int? = null,
    ) = VesselRecord(
        mmsi = mmsi, name = name, shipType = shipType,
        lat = 30.42, lon = 32.34, sogKn = 12.34, cogDeg = 123.0, headingDeg = 124,
        destination = "ROTTERDAM", callsign = "H3RC", imo = 9811000L,
        lastReportMs = 1_700_000_000_000L, lastSeenMs = 1_700_000_000_000L,
    )

    @Test fun toPointsMapsEntityStyleAndCard() {
        val points = toPoints(listOf(record(366999999L, name = "EVER GIVEN", shipType = 70)), cap = 100)
        val point = points.single()
        assertEquals("ais-live-vessels", point.layerId)
        assertEquals("366999999", point.entity.id)
        assertEquals("EVER GIVEN", point.entity.title)
        assertEquals(30.42, point.entity.position.latitude, 1e-9)
        assertEquals("#39d5ff", point.style.colorHex)
        assertEquals(9, point.style.pixelSize)
        assertFalse(point.style.showLabel)

        val card = point.entity.cardFields.associate { it.label to it.value }
        assertEquals("366999999", card["MMSI"])
        assertEquals("CARGO", card["Type"])
        assertEquals("12.3 kn", card["Speed"])
        assertEquals("123°", card["Course"])
        assertEquals("124°", card["Heading"])
        assertEquals("ROTTERDAM", card["Destination"])
        assertEquals("H3RC", card["Callsign"])
        assertEquals("9811000", card["IMO"])
        // 2023-11-14T22:13:20Z
        assertEquals("22:13:20 UTC", card["Last report"])
    }

    @Test fun toPointsFallsBackToMmsiTitleAndKindColour() {
        val points = toPoints(listOf(record(123456789L, shipType = 82)), cap = 100)
        assertEquals("MMSI 123456789", points.single().entity.title)
        assertEquals("#ffb347", points.single().style.colorHex)
        assertEquals("TANKER", points.single().entity.cardFields.first { it.label == "Type" }.value)
    }

    @Test fun toPointsRespectsCap() {
        val records = (1L..50L).map { record(it) }
        assertEquals(7, toPoints(records, cap = 7).size)
        assertTrue(toPoints(records, cap = 0).isEmpty())
    }

    @Test fun fetchWithoutKeyThrows() {
        val layer = VesselsLayer { null }
        try {
            layer.dataSource.fetch()
            fail("fetch without a key succeeded")
        } catch (expected: IllegalStateException) {
            assertTrue(expected.message!!.contains("Power Up"))
        }
    }

    @Test fun fetchWithKeyButNoMessagesYetThrowsConnecting() {
        val layer = VesselsLayer { "some-key" }
        try {
            layer.dataSource.fetch()
            fail("fetch before any AIS message succeeded")
        } catch (expected: IllegalStateException) {
            assertTrue(expected.message!!.contains("Connecting to AISStream"))
        }
    }

    @Test fun layerIdentityMatchesShell() {
        val layer = VesselsLayer { null }
        assertEquals("ais-live-vessels", layer.id)
        assertEquals("Live Vessels", layer.title)
        assertEquals("AISStream", layer.credit)
    }
}
