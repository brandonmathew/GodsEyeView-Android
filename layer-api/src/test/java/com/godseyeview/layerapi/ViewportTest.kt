package com.godseyeview.layerapi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Q25 (issue #40): visible-cap geometry, point-in-cap, bounding
 *  boxes, margin, and the refilter hysteresis. */
class ViewportTest {

    @Before
    fun resetState() {
        ViewportState.reset()
        QualityProfileHolder.setTier(QualityTier.BALANCED)
    }

    // ---- Cap geometry: radius = acos(R / (R + h)) ----

    @Test
    fun `cap radius at ground level is zero`() {
        val cap = VisibleCap.fromCameraHeight(10.0, 20.0, 0.0)
        assertEquals(0.0, cap.radiusDeg, 1e-9)
    }

    @Test
    fun `cap radius at known heights`() {
        // Worked from R = 6,371 km.
        assertEquals(14.16, VisibleCap.fromCameraHeight(0.0, 0.0, 200_000.0).radiusDeg, 0.15)
        assertEquals(40.44, VisibleCap.fromCameraHeight(0.0, 0.0, 2_000_000.0).radiusDeg, 0.15)
        assertEquals(76.02, VisibleCap.fromCameraHeight(0.0, 0.0, 20_000_000.0).radiusDeg, 0.15)
    }

    @Test
    fun `cap radius approaches the hemisphere from orbit heights`() {
        // acos(R/(R+h)) tends to 90deg as h grows; it never exceeds it.
        val cap = VisibleCap.fromCameraHeight(0.0, 0.0, 1.0e12)
        assertTrue(cap.radiusDeg > 89.99)
        assertTrue(cap.radiusDeg <= 90.0)
        assertTrue(VisibleCap(0.0, 0.0, 90.0).isGlobal)
    }

    // ---- Point in cap ----

    @Test
    fun `contains centre and rejects antipode`() {
        val cap = VisibleCap(0.0, 0.0, 10.0)
        assertTrue(cap.contains(0.0, 0.0))
        assertTrue(cap.contains(0.0, 9.9))
        assertFalse(cap.contains(0.0, 10.1))
        assertFalse(cap.contains(0.0, 180.0))
    }

    @Test
    fun `contains across the antimeridian`() {
        val cap = VisibleCap(0.0, 179.9, 1.0)
        assertTrue(cap.contains(0.0, -179.95)) // 0.15 degrees away
        assertFalse(cap.contains(0.0, 178.0)) // 1.9 degrees away
    }

    @Test
    fun `contains near the pole at any longitude`() {
        val cap = VisibleCap(89.0, 0.0, 5.0)
        assertTrue(cap.contains(86.0, 120.0))
        assertTrue(cap.contains(86.0, -120.0))
        assertFalse(cap.contains(80.0, 0.0))
    }

    @Test
    fun `global cap contains everything`() {
        assertTrue(VisibleCap.GLOBAL.contains(-89.0, 179.0))
        assertTrue(VisibleCap.GLOBAL.contains(89.0, -179.0))
    }

    // ---- Margin ----

    @Test
    fun `margin scales radius and clamps at hemisphere`() {
        assertEquals(67.5, VisibleCap(0.0, 0.0, 50.0).withMargin(1.35).radiusDeg, 1e-9)
        assertEquals(90.0, VisibleCap(0.0, 0.0, 80.0).withMargin(1.35).radiusDeg, 1e-9)
    }

    @Test
    fun `margin factors come from the quality profile`() {
        assertEquals(1.35, QualityCaps.forTier(QualityTier.HIGH).viewportMarginFactor, 1e-9)
        assertEquals(1.20, QualityCaps.forTier(QualityTier.BALANCED).viewportMarginFactor, 1e-9)
        assertEquals(1.05, QualityCaps.forTier(QualityTier.BATTERY_SAVER).viewportMarginFactor, 1e-9)
    }

    // ---- Bounding boxes ----

    @Test
    fun `bounding box of a mid latitude cap`() {
        val boxes = VisibleCap(50.0, 10.0, 10.0).boundingBoxes()
        assertEquals(1, boxes.size)
        val box = boxes[0]
        assertEquals(40.0, box.minLat, 1e-9)
        assertEquals(60.0, box.maxLat, 1e-9)
        // dLon = asin(sin(10deg) / cos(50deg)) = 15.68 degrees.
        assertEquals(-5.68, box.minLon, 0.1)
        assertEquals(25.68, box.maxLon, 0.1)
    }

    @Test
    fun `bounding box wraps the antimeridian into two boxes`() {
        val boxes = VisibleCap(0.0, 179.0, 5.0).boundingBoxes()
        assertEquals(2, boxes.size)
        assertEquals(180.0, boxes[0].maxLon, 1e-9)
        assertEquals(-180.0, boxes[1].minLon, 1e-9)
        assertTrue(boxes[0].minLon > 170.0)
        assertTrue(boxes[1].maxLon < -170.0)
        // The two boxes together span ~2x the cap's lon half-width.
        val span = (boxes[0].maxLon - boxes[0].minLon) +
            (boxes[1].maxLon - boxes[1].minLon)
        assertEquals(10.01, span, 0.1)
    }

    @Test
    fun `bounding box reaching a pole spans all longitudes`() {
        val boxes = VisibleCap(85.0, 0.0, 10.0).boundingBoxes()
        assertEquals(1, boxes.size)
        assertEquals(75.0, boxes[0].minLat, 1e-9)
        assertEquals(90.0, boxes[0].maxLat, 1e-9)
        assertEquals(-180.0, boxes[0].minLon, 1e-9)
        assertEquals(180.0, boxes[0].maxLon, 1e-9)
    }

    @Test
    fun `global cap bounding box is the global box`() {
        assertEquals(listOf(LatLonBox.GLOBAL), VisibleCap.GLOBAL.boundingBoxes())
    }

    // ---- Hysteresis ----

    @Test
    fun `first camera report always refilters and applies margin`() {
        assertTrue(ViewportState.updateCamera(0.0, 0.0, 2_000_000.0))
        val filter = ViewportState.filterCap!!
        // 40.44deg camera radius x BALANCED margin 1.20.
        assertEquals(48.53, filter.radiusDeg, 0.2)
        assertEquals(0.0, filter.centerLatitude, 1e-9)
    }

    @Test
    fun `small centre move inside hysteresis band does not refilter`() {
        ViewportState.updateCamera(0.0, 0.0, 2_000_000.0)
        // 5deg moved; threshold is 25% of the 48.5deg filter radius.
        assertFalse(ViewportState.updateCamera(0.0, 5.0, 2_000_000.0))
        assertEquals(0.0, ViewportState.filterCap!!.centerLongitude, 1e-9)
    }

    @Test
    fun `large centre move refilters`() {
        ViewportState.updateCamera(0.0, 0.0, 2_000_000.0)
        assertTrue(ViewportState.updateCamera(0.0, 20.0, 2_000_000.0))
        assertEquals(20.0, ViewportState.filterCap!!.centerLongitude, 1e-9)
    }

    @Test
    fun `large radius change refilters, small one does not`() {
        ViewportState.updateCamera(0.0, 0.0, 2_000_000.0)
        // 2,000 km -> 2,200 km: radius change ~4%, inside the band.
        assertFalse(ViewportState.updateCamera(0.0, 0.0, 2_200_000.0))
        // 2,000 km -> 200 km: radius change ~65%, refilters.
        assertTrue(ViewportState.updateCamera(0.0, 0.0, 200_000.0))
        assertEquals(14.16 * 1.20, ViewportState.filterCap!!.radiusDeg, 0.2)
    }

    @Test
    fun `poll refreshes the filter cap from the latest camera`() {
        ViewportState.updateCamera(0.0, 0.0, 2_000_000.0)
        assertFalse(ViewportState.updateCamera(0.0, 5.0, 2_000_000.0))
        assertTrue(ViewportState.onPoll())
        assertEquals(5.0, ViewportState.filterCap!!.centerLongitude, 1e-9)
        // A second poll with no camera move changes nothing.
        assertFalse(ViewportState.onPoll())
    }

    @Test
    fun `no camera yet means no filter cap`() {
        assertEquals(null, ViewportState.filterCap)
        assertFalse(ViewportState.onPoll())
    }
}
