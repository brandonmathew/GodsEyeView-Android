package com.godseyeview.core.globe

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CameraViewStateTest {
    @Test fun jsonRoundTrip() {
        val v = CameraViewState(-157.85, 21.31, 5_000.0, 40.0, -45.0, 0.0)
        val back = CameraViewState.fromJson(v.toJson())
        assertEquals(v, back)
    }

    @Test fun badJsonIsNull() {
        assertNull(CameraViewState.fromJson("not json"))
        assertNull(CameraViewState.fromJson("{}"))
    }

    @Test fun basemapFallbackIsEsri() {
        assertEquals(Basemap.ESRI, Basemap.fromId("nonsense"))
        assertEquals(Basemap.OSM, Basemap.fromId("osm"))
    }
}
