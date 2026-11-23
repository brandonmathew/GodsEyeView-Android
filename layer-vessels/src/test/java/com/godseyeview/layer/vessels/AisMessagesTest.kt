package com.godseyeview.layer.vessels

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AisMessagesTest {

    @Test fun positionReportParses() {
        val update = AisMessages.parse(
            """
            {"MessageType":"PositionReport",
             "MetaData":{"MMSI":366999999,"ShipName":"EVER GIVEN","latitude":30.42,"longitude":32.34,"time_utc":"2026-11-22 10:00:00"},
             "Message":{"PositionReport":{"UserID":366999999,"Latitude":30.42,"Longitude":32.34,
               "Sog":12.3,"Cog":123.4,"TrueHeading":124,"NavigationalStatus":0}}}
            """.trimIndent(),
        )
        val pos = update as AisUpdate.Position
        assertEquals(366999999L, pos.mmsi)
        assertEquals(30.42, pos.lat, 1e-9)
        assertEquals(32.34, pos.lon, 1e-9)
        assertEquals(12.3, pos.sogKn!!, 1e-9)
        assertEquals(123.4, pos.cogDeg!!, 1e-9)
        assertEquals(124, pos.headingDeg)
        assertEquals(0, pos.navStatus)
        assertEquals("EVER GIVEN", pos.name)
    }

    @Test fun positionSentinelsBecomeNull() {
        val update = AisMessages.parse(
            """
            {"MessageType":"PositionReport",
             "MetaData":{"MMSI":123456789,"latitude":1.0,"longitude":2.0},
             "Message":{"PositionReport":{"Latitude":1.0,"Longitude":2.0,
               "Sog":102.3,"Cog":360,"TrueHeading":511,"NavigationalStatus":15}}}
            """.trimIndent(),
        )
        val pos = update as AisUpdate.Position
        assertNull(pos.sogKn)
        assertNull(pos.cogDeg)
        assertNull(pos.headingDeg)
        assertEquals(15, pos.navStatus)
    }

    @Test fun positionFallsBackToMetaData() {
        val update = AisMessages.parse(
            """
            {"MessageType":"StandardClassBPositionReport",
             "MetaData":{"MMSI":222333444,"latitude":-33.8,"longitude":151.2},
             "Message":{"StandardClassBPositionReport":{"Sog":0.0,"Cog":45.0,"TrueHeading":46}}}
            """.trimIndent(),
        )
        val pos = update as AisUpdate.Position
        assertEquals(-33.8, pos.lat, 1e-9)
        assertEquals(151.2, pos.lon, 1e-9)
    }

    @Test fun extendedClassBCarriesName() {
        val update = AisMessages.parse(
            """
            {"MessageType":"ExtendedClassBPositionReport",
             "MetaData":{"MMSI":555666777,"latitude":10.0,"longitude":20.0},
             "Message":{"ExtendedClassBPositionReport":{"Latitude":10.0,"Longitude":20.0,"Name":"CLASS B BOAT   "}}}
            """.trimIndent(),
        )
        val pos = update as AisUpdate.Position
        assertEquals("CLASS B BOAT", pos.name)
    }

    @Test fun shipStaticDataParses() {
        val update = AisMessages.parse(
            """
            {"MessageType":"ShipStaticData",
             "MetaData":{"MMSI":366999999,"ShipName":"EVER GIVEN","latitude":30.42,"longitude":32.34},
             "Message":{"ShipStaticData":{"ImoNumber":9811000,"CallSign":"H3RC","Name":"EVER GIVEN",
               "Type":70,"Destination":"ROTTERDAM","MaximumStaticDraught":14.5}}}
            """.trimIndent(),
        )
        val stat = update as AisUpdate.Static
        assertEquals(366999999L, stat.mmsi)
        assertEquals("EVER GIVEN", stat.name)
        assertEquals("H3RC", stat.callsign)
        assertEquals(9811000L, stat.imo)
        assertEquals(70, stat.shipType)
        assertEquals("ROTTERDAM", stat.destination)
    }

    @Test fun staticDataReportMergesParts() {
        val update = AisMessages.parse(
            """
            {"MessageType":"StaticDataReport",
             "MetaData":{"MMSI":777888999,"latitude":51.9,"longitude":4.4},
             "Message":{"StaticDataReport":{
               "ReportA":{"Name":"HARBOUR TUG","CallSign":"PB1234","ImoNumber":1234567},
               "ReportB":{"ShipType":52,"Destination":"ANTWERP"}}}}
            """.trimIndent(),
        )
        val stat = update as AisUpdate.Static
        assertEquals(777888999L, stat.mmsi)
        assertEquals("HARBOUR TUG", stat.name)
        assertEquals("PB1234", stat.callsign)
        assertEquals(1234567L, stat.imo)
        assertEquals(52, stat.shipType)
        assertEquals("ANTWERP", stat.destination)
    }

    @Test fun invalidReportPartContributesNothing() {
        val update = AisMessages.parse(
            """
            {"MessageType":"StaticDataReport",
             "MetaData":{"MMSI":777888999},
             "Message":{"StaticDataReport":{
               "ReportA":{"Valid":false,"Name":"GHOST"},
               "ReportB":{"ShipType":31}}}}
            """.trimIndent(),
        )
        val stat = update as AisUpdate.Static
        assertNull(stat.name)
        assertEquals(31, stat.shipType)
    }

    @Test fun unknownAndGarbledReturnNull() {
        assertNull(AisMessages.parse("""{"MessageType":"AidsToNavigationReport","MetaData":{"MMSI":1},"Message":{}}"""))
        assertNull(AisMessages.parse("not json"))
        assertNull(AisMessages.parse("""{"MessageType":"PositionReport","Message":{"PositionReport":{}}}"""))
        assertNull(AisMessages.parse(""))
        assertTrue(AisMessages.parse("""{"foo":1}""") == null)
    }
}
