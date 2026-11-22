package com.godseyeview.layers

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.godseyeview.core.globe.GlobeController
import com.godseyeview.layerapi.Entity
import com.godseyeview.layerapi.FetchState
import com.godseyeview.layerapi.FollowTarget
import com.godseyeview.layerapi.Layer
import com.godseyeview.layerapi.LayerRegistry
import com.godseyeview.layerapi.MapPoint
import com.godseyeview.layerapi.SystemWorldClock
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
            scheduleFetch(layer, 0)
        } else {
            pending.remove(layer.id)?.cancel(false)
            states[layer.id] = FetchState.Idle
            entities.remove(layer.id)
            if (selectedEntity?.layerId == layer.id) select(null)
            if (followTarget?.layerId == layer.id) setFollow(null)
            controller.clearLayer(layer.id)
        }
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
        select(entities[layerId]?.get(baseId))
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
            entities[layer.id] = drawables.filterIsInstance<MapPoint>()
                .associate { it.entity.id to it.entity }
            controller.renderLayer(layer.id, drawables)
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
