package com.godseyeview.layer.vessels

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VesselStoreTest {
    private val mmsi = 366999999L
    private val static = AisUpdate.Static(
        mmsi = mmsi, name = "EVER GIVEN", callsign = "H3RC",
        imo = 9811000L, shipType = 70, destination = "ROTTERDAM",
    )
    private val position = AisUpdate.Position(
        mmsi = mmsi, lat = 30.42, lon = 32.34,
        sogKn = 12.3, cogDeg = 123.0, headingDeg = 124, navStatus = 0, name = null,
    )

    @Test fun staticThenPositionYieldsFullRecord() {
        val store = VesselStore()
        store.apply(static, nowMs = 1_000)
        store.apply(position, nowMs = 2_000)
        val record = store.snapshot(2_000).single()
        assertEquals("EVER GIVEN", record.name)
        assertEquals("H3RC", record.callsign)
        assertEquals(9811000L, record.imo)
        assertEquals(70, record.shipType)
        assertEquals("ROTTERDAM", record.destination)
        assertEquals(30.42, record.lat!!, 1e-9)
        assertEquals(12.3, record.sogKn!!, 1e-9)
        assertEquals(2_000L, record.lastReportMs)
    }

    @Test fun positionThenStaticYieldsFullRecord() {
        val store = VesselStore()
        store.apply(position, nowMs = 1_000)
        store.apply(static, nowMs = 2_000)
        val record = store.snapshot(2_000).single()
        assertEquals("EVER GIVEN", record.name)
        assertEquals(70, record.shipType)
        assertEquals(30.42, record.lat!!, 1e-9)
        // A static report must not restamp the position time.
        assertEquals(1_000L, record.lastReportMs)
    }

    @Test fun partialStaticDoesNotBlankKnownValues() {
        val store = VesselStore()
        store.apply(static, nowMs = 1_000)
        store.apply(position, nowMs = 1_500)
        store.apply(AisUpdate.Static(mmsi, null, null, null, null, "SINGAPORE"), nowMs = 2_000)
        val record = store.snapshot(2_000).single()
        assertEquals("EVER GIVEN", record.name)
        assertEquals("SINGAPORE", record.destination)
    }

    @Test fun positionlessRecordNotInSnapshot() {
        val store = VesselStore()
        store.apply(static, nowMs = 1_000)
        assertTrue(store.snapshot(1_000).isEmpty())
    }

    @Test fun staleRecordDroppedAtThirtyMinutes() {
        val store = VesselStore()
        store.apply(position, nowMs = 0)
        assertEquals(1, store.snapshot(VesselStore.STALE_MS).size)
        assertTrue(store.snapshot(VesselStore.STALE_MS + 1).isEmpty())
        assertEquals(0, store.size())
    }
}
