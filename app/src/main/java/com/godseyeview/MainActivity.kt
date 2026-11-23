package com.godseyeview

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.runtime.DisposableEffect
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.godseyeview.core.globe.Basemap
import com.godseyeview.core.globe.CameraViewState
import com.godseyeview.core.globe.CesiumGlobe
import com.godseyeview.core.globe.GlobeController
import com.godseyeview.core.globe.LastViewStore
import com.godseyeview.core.ui.GodsEyeTheme
import com.godseyeview.core.ui.glassPanel
import com.godseyeview.shell.CREDIT_STRIP_HEIGHT
import com.godseyeview.shell.DOCK_HEIGHT
import com.godseyeview.shell.GlobeDock
import com.godseyeview.shell.GlobeTopBar
import com.godseyeview.shell.MoreSheet
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        hideSystemBars()
        setContent {
            GodsEyeTheme { GlobeScreen() }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    /** Issue #32: immersive full-screen - the navigation bar was staying
     *  visible and covering the bottom of the app. Bars hide on entry and
     *  on focus return; an edge swipe reveals them transiently and they
     *  hide again on their own. */
    private fun hideSystemBars() {
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = androidx.core.view.WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior =
            androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }
}

/** Stage 2 (issue #24): parse a JS-module layer's card payload into an
 *  Entity for the core detail card. "{}" (or anything malformed) means
 *  deselect. Payload shape: {"id","title","latitude","longitude",
 *  "altitude","fields":[{"label","value"}]}. */
private fun parseBridgeCard(
    layerId: String,
    cardJson: String,
): com.godseyeview.layerapi.Entity? {
    val obj = runCatching { org.json.JSONObject(cardJson) }.getOrNull()
        ?: return null
    val id = obj.optString("id")
    if (id.isEmpty()) return null
    val fieldsJson = obj.optJSONArray("fields")
    val fields = buildList {
        if (fieldsJson != null) {
            for (i in 0 until fieldsJson.length()) {
                val f = fieldsJson.optJSONObject(i) ?: continue
                add(
                    com.godseyeview.layerapi.CardField(
                        f.optString("label"), f.optString("value"),
                    ),
                )
            }
        }
    }
    return runCatching {
        com.godseyeview.layerapi.Entity(
            id = id,
            layerId = layerId,
            position = com.godseyeview.layerapi.GeoPoint(
                latitude = obj.optDouble("latitude", 0.0),
                longitude = obj.optDouble("longitude", 0.0),
                altitude = obj.optDouble("altitude", 0.0),
            ),
            title = obj.optString("title", id),
            cardFields = fields,
        )
    }.getOrNull()
}

@Composable
private fun GlobeScreen() {
    val context = LocalContext.current
    val store = remember { LastViewStore(context) }
    val settingsStore = remember { com.godseyeview.settings.SettingsStore(context) }
    val tokenStore = remember { com.godseyeview.settings.TokenStore(context) }
    var hasToken by remember { mutableStateOf(tokenStore.hasToken()) }
    val initialToken = remember { tokenStore.loadToken() }
    /* v2 Q9 tier 3 + Q2 KeyVault: the user's Google key is loaded from the
       KeyVault at launch and handed to the globe (GEV.setGoogleKey).
       Provider keys are entered in the Power Up panel (issue #5); changes
       there are pushed live through controller.setGoogleKey/setIonToken. */
    val keyVault = remember { com.godseyeview.settings.KeyVault(context) }
    val initialGoogleKey = remember {
        keyVault.load(com.godseyeview.settings.ProviderKey.GOOGLE)
    }
    var qualityMode by remember {
        mutableStateOf(settingsStore.loadQualityMode())
    }
    var cacheLimitMb by remember { mutableStateOf(settingsStore.loadCacheLimitMb()) }
    var cacheSizeMb by remember {
        mutableStateOf(com.godseyeview.settings.CacheInfo.sizeMb(context))
    }
    val effectiveQuality = com.godseyeview.settings.QualityResolver
        .effectiveMode(context, qualityMode)
    val controller = remember { GlobeController() }
    /* Issue #34: My Location - tap-only, single fix, never on launch (Q16). */
    var locationStatus by remember { mutableStateOf("") }
    fun flyToDeviceLocation() {
        val manager = context.getSystemService(android.content.Context.LOCATION_SERVICE)
            as android.location.LocationManager
        val providers = listOf(
            android.location.LocationManager.GPS_PROVIDER,
            android.location.LocationManager.NETWORK_PROVIDER,
        )
        val lastKnown = providers.firstNotNullOfOrNull { provider ->
            runCatching { manager.getLastKnownLocation(provider) }.getOrNull()
        }
        fun useFix(loc: android.location.Location) {
            locationStatus = ""
            controller.flyTo(loc.longitude, loc.latitude, 3_000.0)
            controller.showLocationDot(loc.longitude, loc.latitude)
        }
        val active = providers.firstOrNull { provider ->
            runCatching { manager.isProviderEnabled(provider) }.getOrDefault(false)
        }
        if (active == null) {
            if (lastKnown != null) useFix(lastKnown)
            else locationStatus = context.getString(R.string.location_unavailable)
            return
        }
        runCatching {
            manager.getCurrentLocation(
                active, null,
                androidx.core.content.ContextCompat.getMainExecutor(context),
            ) { loc ->
                when {
                    loc != null -> useFix(loc)
                    lastKnown != null -> useFix(lastKnown)
                    else -> locationStatus = context.getString(R.string.location_unavailable)
                }
            }
        }.onFailure {
            if (lastKnown != null) useFix(lastKnown)
            else locationStatus = context.getString(R.string.location_unavailable)
        }
    }
    val locationPermissionLauncher =
        androidx.activity.compose.rememberLauncherForActivityResult(
            androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
        ) { granted ->
            if (granted) flyToDeviceLocation()
            else locationStatus = context.getString(R.string.location_denied)
        }
    // Plan Q19 cap enforcement: over the limit at launch -> clear once.
    androidx.compose.runtime.LaunchedEffect(Unit) {
        if (cacheSizeMb > cacheLimitMb) {
            controller.clearTileCache()
            com.godseyeview.settings.CacheInfo.clear(context)
            cacheSizeMb = com.godseyeview.settings.CacheInfo.sizeMb(context)
        }
    }
    val initialView = remember { store.load() ?: CameraViewState.FULL_EARTH }
    /* Issue #35: the live camera state, for Share - the same reports
       that persist the last view. */
    var currentView by remember { mutableStateOf(initialView) }
    var basemap by remember { mutableStateOf(store.loadBasemap()) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val globeErrorText = stringResource(R.string.globe_error)
    // Issue #12 diagnostics: latest globe state JSON from the page plus the
    // WebView console lines, shown by the Globe status button.
    var diagnostics by remember { mutableStateOf<String?>(null) }
    val consoleLines = remember { mutableListOf<String>() }
    var showDiagnostics by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showPowerUp by remember { mutableStateOf(false) }
    var hasGoogleKey by remember { mutableStateOf(initialGoogleKey != null) }
    var powerUpCount by remember {
        mutableStateOf(com.godseyeview.settings.powerUpSetCount(context))
    }
    var showCredits by remember { mutableStateOf(false) }
    var showSearch by remember { mutableStateOf(false) }
    var showLayers by remember { mutableStateOf(false) }
    var showMore by remember { mutableStateOf(false) }

    /* Stage 4 (issues #45/#47): the layer registry + fetch manager. Layer
       modules register here at startup; with none registered the manager
       simply idles and the Layers panel shows its empty state. */
    val layerManager = remember {
        com.godseyeview.layers.LayerManager(
            registry = com.godseyeview.layerapi.LayerRegistry().apply {
                // Stage 2 Movement layers (issues #25-#29).
                register(com.godseyeview.layer.flights.FlightsLayer())
                register(com.godseyeview.layer.military.MilitaryLayer())
                register(
                    com.godseyeview.layer.satellites.SatellitesLayer(
                        cacheDir = java.io.File(context.filesDir, "tle-cache"),
                        catalogSink = { json ->
                            controller.sendLayerMessage("satellites", json)
                        },
                    ),
                )
                register(
                    com.godseyeview.layer.vessels.VesselsLayer(
                        keyProvider = {
                            keyVault.load(
                                com.godseyeview.settings.ProviderKey.AISSTREAM,
                            )
                        },
                    ),
                )
                register(
                    com.godseyeview.layer.gnss.GnssLayer(
                        rowsSink = { json ->
                            controller.sendLayerMessage("gnss-integrity", json)
                        },
                    ),
                )
            },
            controller = controller,
        )
    }
    /* Stage 2 (issue #24): publish the resolved quality profile's caps
       for layers whenever the effective mode changes (Q21 — one shared
       profile, set in Settings, read by every layer). */
    androidx.compose.runtime.LaunchedEffect(effectiveQuality) {
        com.godseyeview.layerapi.QualityProfileHolder.setTier(
            when (effectiveQuality) {
                com.godseyeview.settings.QualityMode.HIGH ->
                    com.godseyeview.layerapi.QualityTier.HIGH
                com.godseyeview.settings.QualityMode.BATTERY_SAVER ->
                    com.godseyeview.layerapi.QualityTier.BATTERY_SAVER
                else -> com.godseyeview.layerapi.QualityTier.BALANCED
            },
        )
    }
    /* First-run mission picker (issue #10, Q20): shown until the user
       ticks "Don't show this again" (upstream's show policy); the chosen
       mission's layer preset is stored and applied at every launch. */
    val firstRunStore = remember { com.godseyeview.firstrun.FirstRunStore(context) }
    var showFirstRun by remember { mutableStateOf(firstRunStore.shouldShow()) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        layerManager.applyPreset(firstRunStore.presetLayerIds())
    }
    /* Stage 5 in-app updater (issue #54, plan Q23): silent check once per
       launch; Settings can re-check manually, and switching the channel
       re-checks immediately. */
    val updateManager = remember {
        com.godseyeview.update.UpdateManager(context)
    }
    var updateChannel by remember {
        mutableStateOf(
            settingsStore.loadUpdateChannel() ?: updateManager.defaultChannel(),
        )
    }
    var availableUpdate by remember {
        mutableStateOf<com.godseyeview.update.UpdateInfo?>(null)
    }
    var updateStatus by remember { mutableStateOf("") }
    var downloadProgress by remember { mutableStateOf<Int?>(null) }
    val updatesCheckingText = stringResource(R.string.updates_checking)
    val updatesUpToDateText = stringResource(R.string.updates_up_to_date)
    val updatesCheckFailedText = stringResource(R.string.updates_check_failed)
    val updatesAvailableFormat = context.getString(R.string.updates_available)
    val updatesDownloadFailedText = stringResource(R.string.updates_download_failed)
    val updatesChecksumFailedText = stringResource(R.string.updates_checksum_failed)
    fun runUpdateCheck(manual: Boolean) {
        if (manual) updateStatus = updatesCheckingText
        updateManager.check(
            updateChannel,
            onResult = { info ->
                if (info != null) availableUpdate = info
                if (manual) {
                    updateStatus = if (info != null) {
                        String.format(java.util.Locale.US, updatesAvailableFormat, info.version)
                    } else {
                        updatesUpToDateText
                    }
                }
            },
            onError = {
                if (manual) updateStatus = updatesCheckFailedText
            },
        )
    }
    androidx.compose.runtime.LaunchedEffect(Unit) { runUpdateCheck(manual = false) }

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, layerManager) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            when (event) {
                androidx.lifecycle.Lifecycle.Event.ON_STOP -> layerManager.setPaused(true)
                androidx.lifecycle.Lifecycle.Event.ON_START -> layerManager.setPaused(false)
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            layerManager.shutdown()
        }
    }

    fun selectBasemap(selected: Basemap) {
        basemap = selected
        store.saveBasemap(selected)
        controller.setBasemap(selected)
    }

    fun basemapLabel(selected: Basemap): String = context.getString(
        when (selected) {
            Basemap.OSM -> R.string.basemap_osm
            Basemap.PHOTOREALISTIC -> R.string.basemap_photorealistic
            Basemap.GOOGLE_DIRECT -> R.string.basemap_google_direct
            Basemap.ESRI -> R.string.basemap_esri
        },
    )

    fun requestLocation() {
        if (androidx.core.content.ContextCompat.checkSelfPermission(
                context, android.Manifest.permission.ACCESS_FINE_LOCATION,
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            flyToDeviceLocation()
        } else {
            locationPermissionLauncher.launch(
                android.Manifest.permission.ACCESS_FINE_LOCATION,
            )
        }
    }

    fun shareView() {
        val lat = String.format(java.util.Locale.US, "%.5f", currentView.latitude)
        val lon = String.format(java.util.Locale.US, "%.5f", currentView.longitude)
        val height = String.format(java.util.Locale.US, "%,.0f", currentView.heightMeters)
        val text = listOf(
            context.getString(R.string.share_header),
            "${context.getString(R.string.share_latitude)}: $lat",
            "${context.getString(R.string.share_longitude)}: $lon",
            "${context.getString(R.string.share_height)}: $height m",
            "${context.getString(R.string.share_basemap)}: ${basemapLabel(basemap)}",
            "https://www.google.com/maps/search/?api=1&query=$lat,$lon",
        ).joinToString("\n")
        val sendIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(android.content.Intent.EXTRA_TEXT, text)
            putExtra(
                android.content.Intent.EXTRA_SUBJECT,
                context.getString(R.string.share_subject),
            )
        }
        context.startActivity(android.content.Intent.createChooser(sendIntent, null))
    }

    /* Q24 (issue #19): the first-run mission picker is a standalone
       opaque flow — the globe shell is not composed behind it at all, so
       nothing can bleed through or overlap it. */
    if (showFirstRun) {
        com.godseyeview.firstrun.MissionPickerScreen(
            initialMission = firstRunStore.chosenMission(),
            onEnter = { mission, suppress ->
                firstRunStore.choose(mission, suppress)
                layerManager.applyPreset(mission.layerIds)
                showFirstRun = false
            },
            onSkip = { suppress ->
                firstRunStore.dismiss(suppress)
                showFirstRun = false
            },
        )
        return
    }

    Box(modifier = Modifier.fillMaxSize()) {
        CesiumGlobe(
            controller = controller,
            initialView = initialView,
            // A saved photorealistic basemap without a token cannot load;
            // boot on Esri instead of leaving chip and globe disagreeing.
            initialBasemap = if (
                (basemap == Basemap.PHOTOREALISTIC && initialToken == null) ||
                (basemap == Basemap.GOOGLE_DIRECT && initialGoogleKey == null)
            ) {
                Basemap.ESRI
            } else {
                basemap
            },
            initialQualityId = effectiveQuality.id,
            initialIonToken = initialToken,
            initialGoogleKey = initialGoogleKey,
            onCameraChanged = { view ->
                currentView = view
                store.save(view)
                // Stage 2 (issue #24): view-anchored layers (GNSS, the
                // flights regional fallback) read the anchor in fetch().
                com.godseyeview.layerapi.ViewAnchor.update(
                    view.latitude, view.longitude,
                )
                // Q25 (issue #40): viewport filtering — the core's
                // hysteresis decides whether this settled camera report
                // refilters the opted-in layers.
                layerManager.onCameraView(
                    view.latitude, view.longitude, view.heightMeters,
                )
            },
            onBasemapChanged = { newBasemap ->
                basemap = newBasemap
                store.saveBasemap(newBasemap)
            },
            onGlobeError = {
                scope.launch { snackbar.showSnackbar(globeErrorText) }
            },
            onDiagnostics = { json -> diagnostics = json },
            onConsoleMessage = { line ->
                consoleLines.add(line)
                if (consoleLines.size > 50) consoleLines.removeAt(0)
            },
            onEntityPicked = { layerId, entityId ->
                layerManager.onPicked(layerId, entityId)
            },
            onEntityCard = { layerId, cardJson ->
                layerManager.onPickedWithCard(
                    layerId, parseBridgeCard(layerId, cardJson),
                )
            },
            // The WebView stops above the dock plus a clear strip, so
            // the Cesium credit line at the page's bottom edge sits in
            // its own strip and the dock never overlaps it (#19).
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = DOCK_HEIGHT + CREDIT_STRIP_HEIGHT),
        )
        // Q24 mobile shell: ONE compact top bar — wordmark left, single
        // basemap/style chip-menu right. No overlapping pills.
        GlobeTopBar(
            basemap = basemap,
            basemapOptions = buildList {
                add(Basemap.ESRI)
                add(Basemap.OSM)
                if (hasToken) add(Basemap.PHOTOREALISTIC)
                if (hasGoogleKey) add(Basemap.GOOGLE_DIRECT)
            },
            onBasemapSelected = { selectBasemap(it) },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(horizontal = 12.dp)
                .padding(top = 12.dp),
        )
        // Entity card + transient location status float above the
        // credit strip, never under the dock.
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 12.dp)
                .padding(bottom = DOCK_HEIGHT + CREDIT_STRIP_HEIGHT + 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            com.godseyeview.layers.EntityCard(manager = layerManager)
            if (locationStatus.isNotEmpty()) {
                Text(
                    text = locationStatus,
                    style = androidx.compose.material3.MaterialTheme.typography.labelLarge,
                    color = com.godseyeview.core.ui.GevColors.TextPrimary,
                    modifier = Modifier
                        .glassPanel()
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }
        }
        // ONE compact single-row dock. Secondary actions live in the
        // More bottom sheet (see below), not a second slab.
        GlobeDock(
            onSearch = { showSearch = true },
            onLocate = { requestLocation() },
            onShare = { shareView() },
            onLayers = { showLayers = true },
            onMore = { showMore = true },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 12.dp)
                .padding(bottom = 12.dp),
        )
        if (showMore) {
            MoreSheet(
                showPowerUp = powerUpCount < com.godseyeview.settings.POWER_UP_TOTAL,
                powerUpSetCount = powerUpCount,
                powerUpTotal = com.godseyeview.settings.POWER_UP_TOTAL,
                onPowerUp = {
                    showMore = false
                    showPowerUp = true
                },
                onResetGlobe = {
                    showMore = false
                    layerManager.select(null)
                    controller.resetGlobe()
                },
                onGlobeStatus = {
                    showMore = false
                    controller.reportDiagnostics()
                    showDiagnostics = true
                },
                onSettings = {
                    showMore = false
                    showSettings = true
                },
                onDismiss = { showMore = false },
            )
        }
        availableUpdate?.let { info ->
            androidx.compose.material3.AlertDialog(
                onDismissRequest = {
                    if (downloadProgress == null) availableUpdate = null
                },
                title = { Text(stringResource(R.string.updates_dialog_title)) },
                text = {
                    Text(
                        if (downloadProgress != null) {
                            stringResource(
                                R.string.updates_downloading,
                                downloadProgress ?: 0,
                            )
                        } else {
                            stringResource(R.string.updates_dialog_body, info.version)
                        },
                    )
                },
                confirmButton = {
                    androidx.compose.material3.TextButton(
                        enabled = downloadProgress == null,
                        onClick = {
                            downloadProgress = 0
                            updateManager.download(
                                info,
                                onProgress = { downloadProgress = it },
                                onDone = { file ->
                                    downloadProgress = null
                                    availableUpdate = null
                                    context.startActivity(updateManager.installIntent(file))
                                },
                                onError = { message ->
                                    downloadProgress = null
                                    availableUpdate = null
                                    scope.launch {
                                        snackbar.showSnackbar(
                                            if (message == "checksum") {
                                                updatesChecksumFailedText
                                            } else {
                                                updatesDownloadFailedText
                                            },
                                        )
                                    }
                                },
                            )
                        },
                    ) { Text(stringResource(R.string.updates_download)) }
                },
                dismissButton = {
                    androidx.compose.material3.TextButton(
                        enabled = downloadProgress == null,
                        onClick = { availableUpdate = null },
                    ) { Text(stringResource(R.string.updates_later)) }
                },
            )
        }
        if (showLayers) {
            com.godseyeview.layers.LayerPanel(
                manager = layerManager,
                onBack = { showLayers = false },
            )
        }
        if (showSearch) {
            com.godseyeview.search.SearchScreen(
                onResultSelected = { result ->
                    controller.flyTo(result.longitude, result.latitude, result.heightMeters)
                    controller.showPin(result.longitude, result.latitude, result.name)
                    showSearch = false
                },
                onBack = { showSearch = false },
            )
        }
        if (showSettings) {
            com.godseyeview.settings.SettingsScreen(
                basemapLabel = basemapLabel(basemap),
                basemap = basemap,
                onBasemapSelected = { selected -> selectBasemap(selected) },
                qualityMode = qualityMode,
                onQualitySelected = { selected ->
                    qualityMode = selected
                    settingsStore.saveQualityMode(selected)
                    controller.setQuality(
                        com.godseyeview.settings.QualityResolver
                            .effectiveMode(context, selected).id,
                    )
                },
                cacheSummary = stringResource(
                    R.string.settings_cache_size,
                    String.format(java.util.Locale.US, "%.1f", cacheSizeMb),
                ),
                cacheLimitMb = cacheLimitMb,
                onCacheLimitSelected = { limit ->
                    cacheLimitMb = limit
                    settingsStore.saveCacheLimitMb(limit)
                },
                onClearCache = {
                    controller.clearTileCache()
                    com.godseyeview.settings.CacheInfo.clear(context)
                    cacheSizeMb = com.godseyeview.settings.CacheInfo.sizeMb(context)
                },
                powerUpSetCount = powerUpCount,
                onOpenPowerUp = { showPowerUp = true },
                hasGoogleKey = hasGoogleKey,
                hasToken = hasToken,
                updateChannel = updateChannel,
                onUpdateChannelSelected = { selected ->
                    updateChannel = selected
                    settingsStore.saveUpdateChannel(selected)
                    runUpdateCheck(manual = true)
                },
                updateStatus = updateStatus,
                onCheckUpdates = { runUpdateCheck(manual = true) },
                onOpenCredits = { showCredits = true },
                effectiveQualityLabel = when (effectiveQuality) {
                    com.godseyeview.settings.QualityMode.HIGH ->
                        stringResource(R.string.settings_quality_high)
                    com.godseyeview.settings.QualityMode.BALANCED ->
                        stringResource(R.string.settings_quality_balanced)
                    com.godseyeview.settings.QualityMode.BATTERY_SAVER ->
                        stringResource(R.string.settings_quality_battery)
                    com.godseyeview.settings.QualityMode.AUTO -> ""
                },
                onBack = { showSettings = false },
            )
        }
        if (showPowerUp) {
            com.godseyeview.settings.PowerUpScreen(
                onIonTokenChanged = { token ->
                    hasToken = token != null
                    controller.setIonToken(token)
                },
                onGoogleKeyChanged = { key ->
                    hasGoogleKey = key != null
                    controller.setGoogleKey(key)
                    // Boot-guard mirror: if the Google key was removed
                    // while Google-direct is the live basemap, fall back
                    // to Esri so chip and globe cannot disagree.
                    if (key == null && basemap == Basemap.GOOGLE_DIRECT) {
                        basemap = Basemap.ESRI
                        store.saveBasemap(Basemap.ESRI)
                        controller.setBasemap(Basemap.ESRI)
                    }
                },
                onBack = {
                    showPowerUp = false
                    powerUpCount = com.godseyeview.settings.powerUpSetCount(context)
                },
            )
        }
        if (showCredits) {
            com.godseyeview.settings.CreditsScreen(
                onBack = { showCredits = false },
            )
        }
        if (showDiagnostics) {
            val reportText = buildString {
                append(diagnostics ?: stringResource(R.string.diagnostics_none))
                if (consoleLines.isNotEmpty()) {
                    append("\n\nWebView console:\n")
                    append(consoleLines.joinToString("\n"))
                }
            }
            AlertDialog(
                onDismissRequest = { showDiagnostics = false },
                title = { Text(stringResource(R.string.diagnostics_title)) },
                text = {
                    Text(
                        text = reportText,
                        modifier = Modifier.verticalScroll(rememberScrollState()),
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        val clipboard =
                            context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("globe status", reportText))
                    }) { Text(stringResource(R.string.diagnostics_copy)) }
                },
                dismissButton = {
                    TextButton(onClick = { showDiagnostics = false }) {
                        Text(stringResource(R.string.diagnostics_close))
                    }
                },
            )
        }
        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = DOCK_HEIGHT + CREDIT_STRIP_HEIGHT + 8.dp),
        )
    }
}
