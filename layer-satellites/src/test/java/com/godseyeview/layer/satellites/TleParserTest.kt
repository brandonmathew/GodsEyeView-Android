package com.godseyeview.layer.satellites

import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TleParserTest {

    private val issLine1 =
        "1 25544U 98067A   24001.50000000  .00016717  00000-0  10270-3 0  9000"
    private val issLine2 =
        "2 25544  51.6400 208.9163 0006703  69.9862  25.2906 15.49560532    10"
    private val noaaLine1 =
        "1 33591U 09005A   24001.50000000  .00016717  00000-0  10270-3 0  9000"
    private val noaaLine2 =
        "2 33591  99.1000 100.0000 0010000  10.0000  20.0000 14.10000000    10"

    @Test
    fun `parses named entries, skips malformed pair, parses no-name entry`() {
        val text = listOf(
            "ISS (ZARYA)",
            issLine1,
            issLine2,
            "NOAA 19",
            noaaLine1,
            noaaLine2,
            // Malformed: NORAD ids disagree between line 1 and line 2.
            "BROKEN SAT",
            "1 99999U 24001A   24001.50000000  .00000000  00000-0  00000-0 0  9000",
            "2 88888  51.6400 208.9163 0006703  69.9862  25.2906 15.49560532    10",
            // No name line.
            "1 40000U 15001A   24001.50000000  .00000000  00000-0  00000-0 0  9000",
            "2 40000  55.0000 100.0000 0010000  10.0000  20.0000 14.00000000    10",
        ).joinToString("\n")

        val entries = TleParser.parse(text)

        assertEquals(3, entries.size)
        assertEquals("ISS (ZARYA)", entries[0].name)
        assertEquals(25544, entries[0].noradId)
        assertEquals(issLine1, entries[0].line1)
        assertEquals(issLine2, entries[0].line2)
        assertEquals("NOAA 19", entries[1].name)
        assertEquals(33591, entries[1].noradId)
        assertEquals("", entries[2].name)
        assertEquals(40000, entries[2].noradId)
    }

    @Test
    fun `tleEpochMs matches java time for ISS epoch`() {
        // ISS fixture: epoch year 24, day 001.50000000 -> 2024-01-01T12:00Z.
        val expected = LocalDateTime.of(2024, 1, 1, 12, 0, 0)
            .toInstant(ZoneOffset.UTC)
            .toEpochMilli()
        assertEquals(expected, TleParser.tleEpochMs(issLine1))
    }

    @Test
    fun `tleEpochMs maps pre-57 years to 1900s`() {
        val line1 =
            "1 25544U 98067A   99002.00000000  .00016717  00000-0  10270-3 0  9000"
        val expected = LocalDateTime.of(1999, 1, 2, 0, 0, 0)
            .toInstant(ZoneOffset.UTC)
            .toEpochMilli()
        assertEquals(expected, TleParser.tleEpochMs(line1))
    }

    @Test
    fun `buildCatalogJson includes groups entries and epochMs`() {
        val groups = mapOf(
            "stations" to listOf(TleEntry("ISS (ZARYA)", 25544, issLine1, issLine2)),
            "weather" to listOf(TleEntry("NOAA 19", 33591, noaaLine1, noaaLine2)),
        )
        val json = buildCatalogJson(groups, generatedAtMs = 123456789L)
        val root = Json.parseToJsonElement(json).jsonObject

        assertEquals("catalog", root["type"]?.jsonPrimitive?.content)
        assertEquals(123456789L, root["generatedAtMs"]?.jsonPrimitive?.long)
        val groupsObj = root["groups"]!!.jsonObject
        assertEquals(setOf("stations", "weather"), groupsObj.keys)

        val station = groupsObj["stations"]!!.jsonArray.single().jsonObject
        assertEquals("ISS (ZARYA)", station["name"]?.jsonPrimitive?.content)
        assertEquals(25544, station["norad"]?.jsonPrimitive?.int)
        assertEquals(issLine1, station["l1"]?.jsonPrimitive?.content)
        assertEquals(issLine2, station["l2"]?.jsonPrimitive?.content)
        assertEquals(
            TleParser.tleEpochMs(issLine1),
            station["epochMs"]?.jsonPrimitive?.long,
        )
        assertTrue(groupsObj["weather"]!!.jsonArray.size == 1)
    }

    @Test
    fun `celestrak groups match upstream list`() {
        assertEquals(
            listOf(
                "stations", "visual", "gps-ops", "glo-ops", "galileo", "geo",
                "starlink", "resource", "weather", "planet", "sar", "science",
            ),
            Celestrak.GROUPS,
        )
    }

    @Test
    fun `settings default to upstream class defaults`() {
        val layer = SatellitesLayer(
            cacheDir = java.io.File("/tmp/gev2-sat-test"),
            catalogSink = {},
        )
        assertEquals(6, layer.settings.size)
        assertTrue(layer.settingValue("stations"))
        assertTrue(layer.settingValue("imaging"))
        assertTrue(layer.settingValue("telecom"))
        assertEquals(false, layer.settingValue("starlink"))
        assertTrue(layer.settingValue("probes"))
        assertTrue(layer.settingValue("debris"))
    }

    @Test
    fun `setSetting pushes classes payload`() {
        val pushed = mutableListOf<String>()
        val layer = SatellitesLayer(
            cacheDir = java.io.File("/tmp/gev2-sat-test"),
            catalogSink = { pushed += it },
        )
        layer.setSetting("starlink", true)
        assertTrue(layer.settingValue("starlink"))
        assertEquals(1, pushed.size)
        val root = Json.parseToJsonElement(pushed.single()).jsonObject
        assertEquals("classes", root["type"]?.jsonPrimitive?.content)
        val visibility = root["visibility"]!!.jsonObject
        assertEquals(
            "true",
            visibility["starlink"]?.jsonPrimitive?.content,
        )
    }
}
