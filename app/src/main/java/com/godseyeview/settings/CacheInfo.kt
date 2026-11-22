package com.godseyeview.settings

import android.content.Context
import android.webkit.WebStorage
import java.io.File

/** Tile-cache accounting for plan Q19. The WebView HTTP cache (where
 *  Cesium's imagery tiles land) cannot be size-capped through any Android
 *  API, so the cap is enforced by measurement: Settings shows the cache
 *  directory size against the chosen limit, offers Clear, and the app
 *  clears on launch when the size is over the limit. */
object CacheInfo {
    fun sizeBytes(context: Context): Long = dirSize(context.cacheDir)

    fun sizeMb(context: Context): Double = sizeBytes(context) / 1_000_000.0

    private fun dirSize(dir: File?): Long {
        if (dir == null || !dir.exists()) return 0
        var total = 0L
        val stack = ArrayDeque<File>()
        stack.addLast(dir)
        while (stack.isNotEmpty()) {
            val current = stack.removeLast()
            val children = current.listFiles() ?: continue
            for (child in children) {
                if (child.isDirectory) stack.addLast(child) else total += child.length()
            }
        }
        return total
    }

    /** Clears WebView storage data; the HTTP cache itself is cleared through
     *  the live WebView (GlobeController.clearTileCache) because WebView
     *  owns it. The cache directory sweep covers anything left behind. */
    fun clear(context: Context) {
        WebStorage.getInstance().deleteAllData()
        context.cacheDir?.listFiles()?.forEach { it.deleteRecursively() }
    }
}
