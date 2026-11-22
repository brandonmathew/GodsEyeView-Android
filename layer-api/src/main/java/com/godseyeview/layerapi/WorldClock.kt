package com.godseyeview.layerapi

/** Plan Q27: layers read world time from the core, never wall time directly.
 *  The core ships no timeline UI; the clock is an API contract only. */
interface WorldClock {
    /** Current world time as epoch milliseconds. Equals wall time while live. */
    fun nowMillis(): Long
    /** True while the world clock is at the live edge. Always true in the core. */
    val isLive: Boolean
}

class SystemWorldClock : WorldClock {
    override fun nowMillis(): Long = System.currentTimeMillis()
    override val isLive: Boolean = true
}
