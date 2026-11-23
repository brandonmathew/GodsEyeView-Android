package com.godseyeview.layer.flights

import com.godseyeview.layerapi.VisibleCap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Q25 (issue #40): OpenSky URL derivation from the filter cap. */
class OpenskyUrlTest {

    @Test
    fun `no cap yet fetches global`() {
        assertEquals(FlightsLayer.OPENSKY_STATES_URL, openskyUrl(null))
    }

    @Test
    fun `hemisphere cap fetches global`() {
        assertEquals(FlightsLayer.OPENSKY_STATES_URL, openskyUrl(VisibleCap.GLOBAL))
    }

    @Test
    fun `sub hemisphere cap fetches its bounding box`() {
        val url = openskyUrl(VisibleCap(50.0, 10.0, 10.0))
        assertTrue(url.startsWith(FlightsLayer.OPENSKY_STATES_URL))
        assertTrue(url.contains("lamin=40.0000"))
        assertTrue(url.contains("lamax=60.0000"))
        assertTrue(url.contains("lomin=-5.6"))
        assertTrue(url.contains("lomax=25.6"))
    }

    @Test
    fun `antimeridian wrapping cap falls back to global fetch`() {
        // Two boxes cannot be one OpenSky query; the core's client-side
        // cap filter still applies to the global result.
        assertEquals(
            FlightsLayer.OPENSKY_STATES_URL,
            openskyUrl(VisibleCap(0.0, 179.0, 5.0)),
        )
    }
}
