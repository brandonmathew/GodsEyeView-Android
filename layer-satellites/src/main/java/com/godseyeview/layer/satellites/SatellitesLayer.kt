package com.godseyeview.layer.satellites

import com.godseyeview.layerapi.Layer
import com.godseyeview.layerapi.LayerDataSource
import com.godseyeview.layerapi.LayerSetting
import com.godseyeview.layerapi.QualityProfileHolder
import com.godseyeview.layerapi.ViewportState
import com.godseyeview.layerapi.VisibleCap
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.hours
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/** Class ids in upstream legend order (classes.js SATELLITE_CLASS_IDS). */
internal val SATELLITE_CLASS_IDS: List<String> = listOf(
    "stations", "imaging", "telecom", "starlink", "probes", "debris",
)

/** Upstream DEFAULT_SATELLITE_CLASSES (classes.js). */
internal val DEFAULT_SATELLITE_CLASSES: Map<String, Boolean> = linkedMapOf(
    "stations" to true,
    "imaging" to true,
    "telecom" to true,
    "starlink" to false,
    "probes" to true,
    "debris" to true,
)

private const val CACHE_MAX_AGE_MS: Long = 24L * 60L * 60L * 1000L

/** Build the catalog JSON pushed to the JS module:
 *  {"type":"catalog","generatedAtMs":...,"groups":{"stations":[
 *  {"name":..,"norad":25544,"l1":..,"l2":..,"epochMs":..}, ...]}} */
internal fun buildCatalogJson(
    groups: Map<String, List<TleEntry>>,
    generatedAtMs: Long = System.currentTimeMillis(),
): String {
    val root = buildJsonObject {
        put("type", "catalog")
        put("generatedAtMs", generatedAtMs)
        putJsonObject("groups") {
            for ((group, entries) in groups) {
                putJsonArray(group) {
                    for (entry in entries) {
                        add(
                            buildJsonObject {
                                put("name", entry.name)
                                put("norad", entry.noradId)
                                put("l1", entry.line1)
                                put("l2", entry.line2)
                                put("epochMs", TleParser.tleEpochMs(entry.line1))
                            },
                        )
                    }
                }
            }
        }
    }
    return root.toString()
}

internal fun buildClassesJson(visibility: Map<String, Boolean>): String {
    val root = buildJsonObject {
        put("type", "classes")
        putJsonObject("visibility") {
            for (id in SATELLITE_CLASS_IDS) {
                put(id, visibility[id] ?: DEFAULT_SATELLITE_CLASSES[id] ?: false)
            }
        }
    }
    return root.toString()
}

/** The viewport message pushed to satellites.js (Q25):
 *  {"type":"viewport","global":true} or
 *  {"type":"viewport","lat":..,"lon":..,"radiusDeg":..} — the JS module
 *  propagates/renders only satellites inside the cap between refilters. */
internal fun buildViewportJson(filterCap: VisibleCap?): String {
    val root = buildJsonObject {
        put("type", "viewport")
        if (filterCap == null || filterCap.isGlobal) {
            put("global", true)
        } else {
            put("lat", filterCap.centerLatitude)
            put("lon", filterCap.centerLongitude)
            put("radiusDeg", filterCap.radiusDeg)
        }
    }
    return root.toString()
}

/** Satellites (Stage 2, issue #27) — fetches and caches CelesTrak TLEs
 *  under [cacheDir] and pushes the catalog to the WebView's
 *  satellites.js module through [catalogSink] (a JSON payload for
 *  GlobeController.sendLayerMessage). Propagation and rendering live
 *  in the JS module (vendored satellite.js); this layer owns the
 *  catalog, its 24 h cache, and the class-visibility settings. */
class SatellitesLayer(
    private val cacheDir: File,
    private val catalogSink: (String) -> Unit,
) : Layer {
    override val id: String = "satellites"
    override val title: String = "Satellites"
    override val refreshInterval = 6.hours
    override val freshnessTtl = 26.hours
    override val credit: String = "CelesTrak"
    override val dataSource: LayerDataSource = LayerDataSource {
        loadCatalogIfNeeded()
        emptyList()
    }

    /** Renders in its JS module (issue #40: the core must not
     *  drawable-render this layer — see the Layer API contract). */
    override val rendersViaJsModule: Boolean = true

    /** Q25 (issue #40): this layer renders in the WebView, so the core
     *  drawable filter cannot cull it — instead the core pushes each
     *  new filter cap here and the JS module re-derives its propagated
     *  subset. (The drawable path returns nothing for this layer.) */
    override val viewportFiltered: Boolean = true

    override fun onViewportChanged(filterCap: VisibleCap?) {
        catalogSink(buildViewportJson(filterCap))
    }

    private val classVisibility = ConcurrentHashMap<String, Boolean>().apply {
        putAll(DEFAULT_SATELLITE_CLASSES)
    }

    override val settings: List<LayerSetting> = SATELLITE_CLASS_IDS.map { classId ->
        LayerSetting(
            id = classId,
            label = classId.uppercase(),
            defaultValue = DEFAULT_SATELLITE_CLASSES[classId] ?: false,
        )
    }

    override fun settingValue(id: String): Boolean =
        classVisibility[id] ?: DEFAULT_SATELLITE_CLASSES[id] ?: false

    override fun setSetting(id: String, value: Boolean) {
        if (id !in DEFAULT_SATELLITE_CLASSES) return
        classVisibility[id] = value
        catalogSink(buildClassesJson(classVisibility))
        // Q25: the JS subset is cap x visible classes — re-push the
        // viewport so toggling a class re-derives the subset at once
        // instead of waiting for the next camera refilter.
        catalogSink(buildViewportJson(ViewportState.filterCap))
    }

    private fun metaFile(): File = File(cacheDir, "catalog-meta.txt")

    private fun groupFile(group: String): File = File(cacheDir, "tle-$group.txt")

    private fun readCachedGroup(group: String): List<TleEntry> {
        val file = groupFile(group)
        if (!file.isFile) return emptyList()
        return runCatching { TleParser.parse(file.readText()) }.getOrDefault(emptyList())
    }

    private fun loadCatalogIfNeeded() {
        val now = System.currentTimeMillis()
        val includeDense = QualityProfileHolder.caps.loadDenseCatalogues
        val groupsToLoad = Celestrak.GROUPS.filter { group ->
            group != "starlink" || includeDense
        }
        val cachedGeneratedAt: Long? = runCatching {
            metaFile().takeIf { it.isFile }?.readText()?.trim()?.toLongOrNull()
        }.getOrNull()
        val isFresh = cachedGeneratedAt != null &&
            (now - cachedGeneratedAt) in 0L until CACHE_MAX_AGE_MS

        val catalog = LinkedHashMap<String, List<TleEntry>>()
        var generatedAtMs = cachedGeneratedAt ?: now

        if (isFresh) {
            for (group in groupsToLoad) {
                val entries = readCachedGroup(group)
                if (entries.isNotEmpty()) catalog[group] = entries
            }
        } else {
            var anyFetchSucceeded = false
            for (group in groupsToLoad) {
                val fetched: List<TleEntry>? = runCatching {
                    val text = Celestrak.fetchGroup(group)
                    val parsed = TleParser.parse(text)
                    cacheDir.mkdirs()
                    groupFile(group).writeText(text)
                    parsed
                }.getOrNull()
                if (fetched != null) {
                    anyFetchSucceeded = true
                    if (fetched.isNotEmpty()) catalog[group] = fetched
                } else {
                    // Failure keeps any cached copy.
                    val cached = readCachedGroup(group)
                    if (cached.isNotEmpty()) catalog[group] = cached
                }
            }
            if (anyFetchSucceeded) {
                generatedAtMs = now
                runCatching {
                    cacheDir.mkdirs()
                    metaFile().writeText(now.toString())
                }
            }
        }

        if (catalog.values.all { it.isEmpty() }) {
            throw IllegalStateException("CelesTrak unavailable")
        }

        catalogSink(buildCatalogJson(catalog, generatedAtMs))
        catalogSink(buildClassesJson(classVisibility))
        // Q25: a fresh catalog starts unfiltered in the JS module;
        // immediately re-apply the current filter cap.
        catalogSink(buildViewportJson(ViewportState.filterCap))
    }
}
