package com.godseyeview.layerapi

/** Stage 2 (issue #24): the shared quality profile's caps for layers.
 *  Plan Q21: one shared internal quality profile drives every layer —
 *  caps live here, never hardcoded per layer. The app resolves the
 *  Stage 1 quality mode (High / Balanced / Battery Saver) to a tier and
 *  publishes it through [QualityProfileHolder]; layers read the current
 *  caps inside fetch(). */
enum class QualityTier { HIGH, BALANCED, BATTERY_SAVER }

data class QualityCaps(
    val tier: QualityTier,
    /** Upper bound on point entities a layer should hand to the renderer
     *  in one fetch. Layers with more data keep the first N (their own
     *  ordering decides which N matter most). */
    val maxPointsPerLayer: Int,
    /** Whether heavy optional catalogues (the Starlink shell) may load. */
    val loadDenseCatalogues: Boolean,
    /** Viewport filtering (Q25): the visible cap's angular radius is
     *  multiplied by this factor before filtering, so a margin of
     *  off-screen data is kept rendered and panning does not pop
     *  entities in at the edges. HIGH keeps the widest margin,
     *  BATTERY_SAVER the tightest. */
    val viewportMarginFactor: Double,
) {
    companion object {
        fun forTier(tier: QualityTier): QualityCaps = when (tier) {
            QualityTier.HIGH -> QualityCaps(tier, 20_000, true, 1.35)
            QualityTier.BALANCED -> QualityCaps(tier, 12_000, true, 1.20)
            QualityTier.BATTERY_SAVER -> QualityCaps(tier, 5_000, false, 1.05)
        }
    }
}

object QualityProfileHolder {
    @Volatile
    var caps: QualityCaps = QualityCaps.forTier(QualityTier.BALANCED)
        private set

    fun setTier(tier: QualityTier) {
        caps = QualityCaps.forTier(tier)
    }
}

/** The globe's current view anchor (centre of the camera view), fed by
 *  the app from camera reports (Stage 2, issue #24). Layers whose
 *  upstream source is anchored on the view (GNSS integrity, the flights
 *  regional fallback) read it inside fetch(). Defaults to (0, 0) before
 *  the first camera report. */
object ViewAnchor {
    @Volatile var latitude: Double = 0.0
    @Volatile var longitude: Double = 0.0

    fun update(latitude: Double, longitude: Double) {
        this.latitude = latitude
        this.longitude = longitude
    }
}
