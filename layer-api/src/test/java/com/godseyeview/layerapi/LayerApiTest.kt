package com.godseyeview.layerapi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import kotlin.time.Duration.Companion.seconds

class LayerApiTest {
    private fun fakeLayer(id: String) = object : Layer {
        override val id = id
        override val title = id
        override val refreshInterval = 30.seconds
        override val freshnessTtl = 90.seconds
        override val credit = "Test"
        override val dataSource = LayerDataSource { emptyList() }
    }

    @Test fun registryRejectsDuplicateIds() {
        val r = LayerRegistry()
        r.register(fakeLayer("flights"))
        try {
            r.register(fakeLayer("flights"))
            fail("duplicate id accepted")
        } catch (expected: IllegalArgumentException) {
        }
        assertEquals(1, r.all().size)
    }

    @Test fun geoPointValidatesRanges() {
        try { GeoPoint(91.0, 0.0); fail("latitude 91 accepted") }
        catch (expected: IllegalArgumentException) { }
        try { GeoPoint(0.0, -181.0); fail("longitude -181 accepted") }
        catch (expected: IllegalArgumentException) { }
    }

    @Test fun trackNeedsTwoPoints() {
        try { Track("l", "e", listOf(GeoPoint(0.0, 0.0))); fail("1-point track accepted") }
        catch (expected: IllegalArgumentException) { }
    }

    @Test fun systemClockIsLive() {
        val c = SystemWorldClock()
        assertTrue(c.isLive)
        assertTrue(c.nowMillis() > 0)
    }
}
