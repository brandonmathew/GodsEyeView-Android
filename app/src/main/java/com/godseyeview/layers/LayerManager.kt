package com.godseyeview.layers

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.godseyeview.core.globe.GlobeController
import com.godseyeview.layerapi.Drawable
import com.godseyeview.layerapi.Entity
import com.godseyeview.layerapi.FetchState
import com.godseyeview.layerapi.FollowTarget
import com.godseyeview.layerapi.Layer
import com.godseyeview.layerapi.LayerRegistry
import com.godseyeview.layerapi.MapPoint
import com.godseyeview.layerapi.SystemWorldClock
import com.godseyeview.layerapi.ViewportState
import com.godseyeview.layerapi.WorldClock
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.minutes

/** Stage 4 core fetch manager (plan Q29, issue #45). Owns every registered
 *  layer's lifecycle: enable/disable, polling at the layer's declared
 *  interval, retry with backoff on failure (last good data stays drawn),
 *  Fresh -> Stale marking at the layer's freshness TTL, and a global pause
 *  while the app is backgrounded. Fetches run on a single background
 *  executor; the globe controller posts its own calls to the UI thread.
 *  Also holds the entity store the detail card resolves picks from, the
 *  current selection, and the follow target (Q28). */
class LayerManager(
    val registry: LayerRegistry,
    private val controller: GlobeController,
    private val clock: WorldClock = SystemWorldClock(),
) {
    val states = mutableStateMapOf<String, FetchState>()
    val enabled = mutableStateMapOf<String, Boolean>()
    var selectedEntity by mutableStateOf<Entity?>(null)
        private set
    var followTarget by mutableStateOf<FollowTarget?>(null)
        private set

    private val executor = Executors.newSingleThreadScheduledExecutor()
    private val pending = mutableMapOf<String, ScheduledFuture<*>>()
    private val failures = mutableMapOf<String, Int>()
    private val entities = mutableMapOf<String, Map<String, Entity>>()
    /** Last UNfiltered fetch result per layer (Q25): a camera-driven
     *  refilter re-derives the rendered set from this without refetching. */
    private val lastDrawables = mutableMapOf<String, List<Drawable>>()
    @Volatile private var paused = false

    fun isEnabled(layerId: String): Boolean = enabled[layerId] == true

    /** First-run mission preset (issue #10): enable every registered
     *  layer whose id is in the preset. Ids with no registered layer yet
     *  are simply not matched — the stored preset is re-applied at every
     *  launch, so layers pick it up as they register in later stages. */
    fun applyPreset(layerIds: Collection<String>) {
        for (layer in registry.all()) {
            if (layer.id in layerIds && !isEnabled(layer.id)) {
                setEnabled(layer, true)
            }
        }
    }

    fun setEnabled(layer: Layer, on: Boolean) {
        enabled[layer.id] = on
        if (on) {
            failures[layer.id] = 0
            // Stage 2 (issue #24): live layers open connections here.
            runCatching { layer.onEnabled() }
            scheduleFetch(layer, 0)
        } else {
            pending.remove(layer.id)?.cancel(false)
            states[layer.id] = FetchState.Idle
            entities.remove(layer.id)
            lastDrawables.remove(layer.id)
            if (selectedEntity?.layerId == layer.id) select(null)
            if (followTarget?.layerId == layer.id) setFollow(null)
            controller.clearLayer(layer.id)
            runCatching { layer.onDisabled() }
        }
    }

    /** Stage 2 (issue #24): a JS-module layer (satellites, GNSS) renders
     *  itself in the WebView and reports its own picks with a ready-made
     *  card entity. Null deselects. */
    fun onPickedWithCard(layerId: String, entity: Entity?) {
        if (entity == null) {
            if (selectedEntity?.layerId == layerId) select(null)
            return
        }
        select(entity)
    }

    /** Stage 2 (issue #24): panel toggles for a layer's boolean settings.
     *  The layer owns the state; [settingsTick] just makes the panel
     *  recompose. */
    var settingsTick by mutableStateOf(0)
        private set

    fun setSetting(layer: Layer, settingId: String, value: Boolean) {
        layer.setSetting(settingId, value)
        settingsTick++
    }

    /** Backgrounding pauses polling; foregrounding refetches any enabled
     *  layer whose data is stale or missing. */
    fun setPaused(value: Boolean) {
        paused = value
        if (!value) {
            for (layer in registry.all()) {
                if (!isEnabled(layer.id)) continue
                val state = states[layer.id]
                val needsFetch = when (state) {
                    is FetchState.Fresh ->
                        clock.nowMillis() - state.fetchedAtMillis >
                            layer.freshnessTtl.inWholeMilliseconds
                    else -> true
                }
                if (needsFetch) scheduleFetch(layer, 0)
            }
        }
    }

    /** A globe pick (issue #44): resolve to the layer's entity, stripping
     *  the renderer's :track / :polygon id suffixes. Unknown = deselect. */
    fun onPicked(layerId: String, entityId: String?) {
        if (entityId == null) {
            select(null)
            return
        }
        val baseId = entityId.removeSuffix(":track").removeSuffix(":polygon")
        val entity = entities[layerId]?.get(baseId)
        select(entity)
        // Stage 2 (issue #24): selection enrichment (e.g. adsbdb route
        // for the one tapped aircraft) runs on the background executor;
        // the merged fields land only if the selection is still this
        // entity when the lookup returns.
        if (entity != null) {
            val layer = registry.get(layerId)
            if (layer != null) {
                executor.execute {
                    val extra = runCatching { layer.enrich(entity) }
                        .getOrDefault(emptyList())
                    if (extra.isNotEmpty() &&
                        selectedEntity?.layerId == entity.layerId &&
                        selectedEntity?.id == entity.id
                    ) {
                        selectedEntity = entity.copy(
                            cardFields = entity.cardFields + extra,
                        )
                    }
                }
            }
        }
    }

    fun select(entity: Entity?) {
        selectedEntity = entity
        if (entity == null && followTarget != null) setFollow(null)
    }

    fun setFollow(target: FollowTarget?) {
        followTarget = target
        // A null entityId is the stop signal on the JS side.
        controller.setFollow(target?.layerId ?: "", target?.entityId)
    }

    fun shutdown() {
        executor.shutdownNow()
    }

    private fun scheduleFetch(layer: Layer, delayMillis: Long) {
        pending.remove(layer.id)?.cancel(false)
        pending[layer.id] = executor.schedule(
            { runFetch(layer) },
            delayMillis,
            TimeUnit.MILLISECONDS,
        )
    }

    private fun runFetch(layer: Layer) {
        if (!isEnabled(layer.id)) return
        if (paused) {
            scheduleFetch(layer, layer.refreshInterval.inWholeMilliseconds)
            return
        }
        if (states[layer.id] !is FetchState.Fresh && states[layer.id] !is FetchState.Stale) {
            states[layer.id] = FetchState.Loading
        }
        try {
            val drawables = layer.dataSource.fetch()
            val now = clock.nowMillis()
            lastDrawables[layer.id] = drawables
            // Q25: a poll is a refilter trigger — refresh the filter
            // cap from the latest camera before filtering this result,
            // and let the other opted-in layers know it moved.
            if (ViewportState.onPoll()) notifyViewportChanged()
            renderFiltered(layer, drawables)
            states[layer.id] = FetchState.Fresh(now)
            failures[layer.id] = 0
            scheduleStaleFlip(layer, now)
            scheduleFetch(layer, layer.refreshInterval.inWholeMilliseconds)
        } catch (e: Exception) {
            val count = (failures[layer.id] ?: 0) + 1
            failures[layer.id] = count
            val hasData = entities[layer.id]?.isNotEmpty() == true
            if (!hasData) {
                states[layer.id] = FetchState.Failed(e.message)
            }
            val backoff = layer.refreshInterval.inWholeMilliseconds *
                (1L shl minOf(count, 3))
            scheduleFetch(layer, minOf(backoff, 5.minutes.inWholeMilliseconds))
        }
    }

    /** Camera-settle feed (Q25): MainActivity forwards every camera
     *  report here (already debounced in the WebView + moveEnd). The
     *  core's hysteresis decides whether this is a refilter; if it is,
     *  every enabled opted-in layer re-derives its rendered set from
     *  its last fetch — no refetch, no per-frame work. */
    fun onCameraView(latitude: Double, longitude: Double, heightMeters: Double) {
        if (!ViewportState.updateCamera(latitude, longitude, heightMeters)) return
        val cap = ViewportState.filterCap
        for (layer in registry.all()) {
            if (!layer.viewportFiltered || !isEnabled(layer.id)) continue
            runCatching { layer.onViewportChanged(cap) }
            val raw = lastDrawables[layer.id] ?: continue
            renderFiltered(layer, raw)
        }
    }

    /** Tell every enabled opted-in layer the filter cap changed
     *  (poll-driven path; camera-driven refilters notify inline). */
    private fun notifyViewportChanged() {
        val cap = ViewportState.filterCap
        for (layer in registry.all()) {
            if (!layer.viewportFiltered || !isEnabled(layer.id)) continue
            runCatching { layer.onViewportChanged(cap) }
        }
    }

    /** Filter (when the layer opted in) and render one fetch result,
     *  rebuilding the pick-resolution entity store from exactly what
     *  was rendered — so a count or a pick always matches the screen.
     *  Selection integrity (Q25): the selected entity is never filtered
     *  out from under its open card; it stays rendered and resolvable
     *  until the user deselects or disables the layer. */
    private fun renderFiltered(layer: Layer, drawables: List<Drawable>) {
        if (layer.rendersViaJsModule) {
            // JS-module layers draw themselves via layer messages;
            // renderLayer would clear their rendering (see Layer API).
            // Their picks arrive as ready-made cards, so the entity
            // store stays empty for them, as it was when their fetch
            // returned no drawables.
            entities[layer.id] = emptyMap()
            return
        }
        val toRender = filterDrawables(layer, drawables)
        entities[layer.id] = toRender.filterIsInstance<MapPoint>()
            .associate { it.entity.id to it.entity }
        controller.renderLayer(layer.id, toRender)
    }

    private fun filterDrawables(layer: Layer, drawables: List<Drawable>): List<Drawable> {
        if (!layer.viewportFiltered) return drawables
        val cap = ViewportState.filterCap ?: return drawables
        if (cap.isGlobal) return drawables
        val selected = selectedEntity?.takeIf { it.layerId == layer.id }
        val kept = drawables.filter { drawable ->
            // Only points are position-culled; tracks/polygons/labels
            // pass through (no opted-in layer emits them today).
            drawable !is MapPoint || cap.contains(drawable.entity.position)
        }
        if (selected == null || kept.any { it is MapPoint && it.entity.id == selected.id }) {
            return kept
        }
        // The selected entity left the cap: keep its point rendered so
        // the open card never points at nothing.
        val selectedPoint = drawables.firstOrNull {
            it is MapPoint && it.entity.id == selected.id
        } ?: return kept
        return kept + selectedPoint
    }

    /** Flip Fresh -> Stale when the TTL passes with no newer fetch. */
    private fun scheduleStaleFlip(layer: Layer, fetchedAtMillis: Long) {
        executor.schedule(
            {
                val state = states[layer.id]
                if (state is FetchState.Fresh && state.fetchedAtMillis == fetchedAtMillis) {
                    states[layer.id] = FetchState.Stale(fetchedAtMillis)
                }
            },
            layer.freshnessTtl.inWholeMilliseconds,
            TimeUnit.MILLISECONDS,
        )
    }
}
