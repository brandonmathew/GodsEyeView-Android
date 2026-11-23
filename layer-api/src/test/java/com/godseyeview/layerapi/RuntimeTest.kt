package com.godseyeview.layerapi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Stage 2 (issue #24): shared quality caps + view anchor. */
class RuntimeTest {
    @Test
    fun `caps scale down by tier and battery saver drops dense catalogues`() {
        val high = QualityCaps.forTier(QualityTier.HIGH)
        val balanced = QualityCaps.forTier(QualityTier.BALANCED)
        val saver = QualityCaps.forTier(QualityTier.BATTERY_SAVER)
        assertTrue(high.maxPointsPerLayer > balanced.maxPointsPerLayer)
        assertTrue(balanced.maxPointsPerLayer > saver.maxPointsPerLayer)
        assertTrue(high.loadDenseCatalogues)
        assertTrue(balanced.loadDenseCatalogues)
        assertFalse(saver.loadDenseCatalogues)
    }

    @Test
    fun `holder publishes the caps for the tier set`() {
        QualityProfileHolder.setTier(QualityTier.BATTERY_SAVER)
        assertEquals(
            QualityTier.BATTERY_SAVER,
            QualityProfileHolder.caps.tier,
        )
        QualityProfileHolder.setTier(QualityTier.BALANCED)
    }

    @Test
    fun `view anchor updates`() {
        ViewAnchor.update(21.3, -157.8)
        assertEquals(21.3, ViewAnchor.latitude, 1e-9)
        assertEquals(-157.8, ViewAnchor.longitude, 1e-9)
    }

    @Test
    fun `styled point keeps core defaults when unset`() {
        val point = MapPoint(
            layerId = "x",
            entity = Entity("e", "x", GeoPoint(0.0, 0.0), "t"),
        )
        assertEquals(null, point.style.colorHex)
        assertTrue(point.style.showLabel)
    }
}
