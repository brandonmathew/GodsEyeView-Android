package com.godseyeview.layerapi

import kotlin.time.Duration

/** Contract every data layer implements (plan Q21/Q26/Q29).
 *  A layer module depends on :layer-api only - never on the app, never on
 *  another layer. A layer declares *what* it draws and *how fresh* its data
 *  must be; the core owns fetching, caching, rendering and lifecycle. */
interface Layer {
    /** Unique, stable id, e.g. "flights". Used in state, share links, credits. */
    val id: String
    /** User-visible name (string resource key in the layer module, resolved by the shell). */
    val title: String
    /** How often the core refresh manager polls this layer's source while it is on. */
    val refreshInterval: Duration
    /** After this long without a successful fetch, the core marks the layer stale in the UI. */
    val freshnessTtl: Duration
    /** Attribution line for the Credits screen (plan Q32). */
    val credit: String
    /** The layer's data source. The core decides when [LayerDataSource.fetch] runs. */
    val dataSource: LayerDataSource

    /** Called by the core when the user turns the layer on, before the
     *  first fetch (Stage 2, issue #24). Layers with live connections
     *  (e.g. a WebSocket) open them here. Default: nothing. */
    fun onEnabled() {}

    /** Called by the core when the user turns the layer off, after its
     *  drawables are cleared. Live connections close here. */
    fun onDisabled() {}

    /** Extra detail-card fields for one selected entity, computed on the
     *  core's background executor at selection time (Stage 2, issue #24).
     *  For lookups that must not run for every entity (e.g. a per-aircraft
     *  route query). Returned fields are appended to the entity's own.
     *  Default: none. */
    fun enrich(entity: Entity): List<CardField> = emptyList()

    /** True when the layer renders itself through a JS module in the
     *  WebView (satellites, GNSS) and its [LayerDataSource] returns no
     *  drawables. The core must NOT call renderLayer for such a layer:
     *  the globe's renderLayer first clears the layer, which invokes
     *  the module's onClear and wipes its own rendering (found in
     *  Q25 work, issue #40 — in beta.5 this wiped the JS layers on
     *  every poll, right after their data sink landed). Clearing on
     *  disable still goes through clearLayer, as intended.
     *  Default: false. */
    val rendersViaJsModule: Boolean
        get() = false

    /** Viewport filtering opt-in (Q25, issue #40). When true, the core
     *  filters this layer's point drawables to the visible cap (camera
     *  horizon x the quality profile's margin) between fetch and render,
     *  and calls [onViewportChanged] on every refilter. Layers with
     *  small entity sets leave this off. Default: off. */
    val viewportFiltered: Boolean
        get() = false

    /** Called by the core whenever the viewport filter cap changes
     *  (hysteresis trigger or a layer poll — never per camera frame).
     *  For layers that render or subscribe outside the drawable path:
     *  a JS module re-derives its visible subset, a streaming layer
     *  re-derives its subscription area. [filterCap] is null only
     *  before the first camera report (no filtering). Default: nothing. */
    fun onViewportChanged(filterCap: VisibleCap?) {}

    /** Boolean settings the panel shows under the layer's row while it is
     *  enabled (upstream's layer-row params, e.g. satellite class
     *  visibility). Default: none. */
    val settings: List<LayerSetting>
        get() = emptyList()

    /** Current value of a setting from [settings]. */
    fun settingValue(id: String): Boolean = false

    /** Apply a panel toggle. The layer owns the state and any side
     *  effects (e.g. telling its JS module to hide a class). */
    fun setSetting(id: String, value: Boolean) {}
}

/** One boolean layer-row setting (Stage 2, issue #24). */
data class LayerSetting(
    val id: String,
    val label: String,
    val defaultValue: Boolean,
)

fun interface LayerDataSource {
    /** Return the layer's current drawables. Throwing marks the fetch failed;
     *  the core retries with backoff and keeps showing the last good data. */
    fun fetch(): List<Drawable>
}

/** Fetch lifecycle states the core reports for every layer (plan Q29). */
sealed interface FetchState {
    data object Idle : FetchState
    data object Loading : FetchState
    data class Fresh(val fetchedAtMillis: Long) : FetchState
    data class Stale(val fetchedAtMillis: Long) : FetchState
    data class Failed(val message: String?) : FetchState
}
