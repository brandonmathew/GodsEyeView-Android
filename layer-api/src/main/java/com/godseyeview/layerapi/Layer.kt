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
}

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
