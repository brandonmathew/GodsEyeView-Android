'use strict';
/* God's Eye View Android v2 - core globe (Stage 1 foundation).
   CesiumJS 1.138.0 (v2 plan Q6 - the version upstream v0.3.0's lockfile
   installs; v1 ran 1.147). Adapted from v1's proven globe.js: every API
   used here exists in 1.138 (Viewer baseLayer option, CustomHeightmap-
   TerrainProvider, CesiumTerrainProvider/Cesium3DTileset fromIonAssetId,
   Cesium3DTileset.fromUrl - all present since <=1.104).
   Basemap ladder, all three tiers (v2 plan Q9): (1) keyless Esri World
   Imagery + Terrarium terrain, OSM fallback; (2) user ion token ->
   Cesium World Terrain + Google Photorealistic via ion; (3) user Google
   Maps key -> direct Google 3D Tiles from tile.googleapis.com.
   Terrain swaps stay probe-gated (v1 issue #12 rule): assign only after
   a first tile/sample succeeds, revert on repeated errors - terrain
   must never block the globe. */
  var GEV2_BUILD = 'GodsEyeView-v2 cesium-1.138.0';
(function () {
  var bridge = window.AndroidBridge || {
    onGlobeReady: function () {}, onCameraChanged: function () {},
    onBasemapChanged: function () {}, onError: function () {},
    onDiagnostics: function () {}, onEntityPicked: function () {},
    onEntityCard: function () {}
  };

  /* Issue #12 diagnostics: every JS error is forwarded to the app instead
     of dying silently in the WebView (onError was never called before). */
  var jsErrors = [];
  function noteError(text) {
    jsErrors.push(String(text).slice(0, 300));
    if (jsErrors.length > 20) jsErrors.shift();
    bridge.onError(String(text).slice(0, 300));
  }
  window.addEventListener('error', function (e) {
    noteError('window.onerror: ' + (e.message || 'unknown') +
      (e.filename ? ' @ ' + e.filename + ':' + e.lineno : ''));
  });
  window.addEventListener('unhandledrejection', function (e) {
    noteError('unhandledrejection: ' + (e.reason && e.reason.message || e.reason || 'unknown'));
  });

  var ESRI_URL = 'https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}';

  var viewer = new Cesium.Viewer('cesiumContainer', {
    animation: false, timeline: false, baseLayerPicker: false,
    geocoder: false, homeButton: false, sceneModePicker: false,
    navigationHelpButton: false, fullscreenButton: false,
    infoBox: false, selectionIndicator: false,
    baseLayer: false, terrainProvider: new Cesium.EllipsoidTerrainProvider()
  });
  viewer.scene.globe.enableLighting = false;
  viewer.scene.fog.enabled = true;

  /* Camera matching with the original God's Eye View (issue #38,
     Brandon's decision 2026-11-22; original:
     src/render/cesium/applicationViewer.js):
     1. Touch pinch zoom: Cesium's zoomFactor x3 on coarse pointers -
        a phone pinch spans fewer pixels than a desktop gesture.
     2. Zoom-out cap: the globe never shrinks below 30% of the screen
        height. Cesium sets no limit; the original derives the maximum
        distance from the camera fovy each frame and never lets the
        limit sit below the camera's current height. */
  var cameraController = viewer.scene.screenSpaceCameraController;
  if (window.matchMedia && window.matchMedia('(pointer: coarse)').matches) {
    cameraController.zoomFactor *= 3;
  }
  var MIN_GLOBE_SHARE = 0.3;
  function zoomOutLimit(fovy) {
    var radius = Cesium.Ellipsoid.WGS84.maximumRadius;
    var angular = Math.atan(MIN_GLOBE_SHARE * Math.tan(fovy / 2));
    return radius / Math.sin(angular) - radius;
  }
  viewer.scene.preRender.addEventListener(function () {
    var fovy = viewer.camera.frustum.fovy;
    if (viewer.scene.mode !== Cesium.SceneMode.SCENE3D || !isFinite(fovy)) {
      cameraController.maximumZoomDistance = Number.POSITIVE_INFINITY;
      return;
    }
    var currentHeight = viewer.camera.positionCartographic
      ? viewer.camera.positionCartographic.height
      : 0;
    cameraController.maximumZoomDistance = Math.max(zoomOutLimit(fovy), currentHeight);
  });

  var credits = viewer.scene.frameState.creditDisplay;
  function addCredit(text, href) {
    credits.addStaticCredit(new Cesium.Credit(
      href ? '<a href="' + href + '" target="_blank" rel="noopener">' + text + '</a>' : text, true));
  }
  addCredit('Imagery © Esri &mdash; Source: Esri, Maxar, Earthstar Geographics', 'https://www.esri.com');
  addCredit('© OpenStreetMap contributors', 'https://www.openstreetmap.org/copyright');

  var currentBasemap = 'esri';
  var esriErrorCount = 0;
  var fellBack = false;

  function makeOsmLayer() {
    return new Cesium.ImageryLayer(new Cesium.OpenStreetMapImageryProvider({
      url: 'https://tile.openstreetmap.org/'
    }));
  }
  function makeEsriLayer() {
    var provider = new Cesium.UrlTemplateImageryProvider({
      url: ESRI_URL, maximumLevel: 18,
      credit: 'Imagery © Esri'
    });
    provider.errorEvent.addEventListener(function () {
      esriErrorCount++;
      if (!fellBack && currentBasemap === 'esri' && esriErrorCount >= 8) {
        fellBack = true;
        setBasemap('osm');
        bridge.onBasemapChanged('osm');
      }
    });
    return new Cesium.ImageryLayer(provider);
  }

  var baseLayer = makeEsriLayer();
  viewer.imageryLayers.add(baseLayer);

  function setBasemap(id) {
    if (id === currentBasemap) return;
    if (id === 'google') {
      if (!googleKey) {
        googleError = 'google basemap selected without a Google key';
        return;
      }
      baseLayer.show = false;
      if (photoTileset) photoTileset.show = false;
      currentBasemap = 'google';
      ensureGoogleTileset();
      if (googleTileset) googleTileset.show = true;
      return;
    }
    if (googleTileset) googleTileset.show = false;
    if (id === 'photorealistic') {
      if (!ionTokenSet) {
        ionError = 'photorealistic selected without an ion token';
        return;
      }
      baseLayer.show = false;
      currentBasemap = 'photorealistic';
      ensurePhotoTileset();
      if (photoTileset) photoTileset.show = true;
      return;
    }
    if (photoTileset) photoTileset.show = false;
    baseLayer.show = true;
    var replacement = id === 'osm' ? makeOsmLayer() : makeEsriLayer();
    viewer.imageryLayers.add(replacement, 0);
    viewer.imageryLayers.remove(baseLayer, true);
    baseLayer = replacement;
    currentBasemap = id;
  }

  /* Terrain (issue #12 root cause, verified by bisect): the AWS Terrain
     Tiles quantized-mesh dataset is gone from its bucket (layer.json
     404s, no tiles/ prefix exists). CesiumTerrainProvider.fromUrl on it
     resolved into a provider that never became ready, and the globe
     surface waits for terrain readiness forever - zero tiles, no error.
     Stage 1 therefore stays on the ellipsoid, which renders. Working
     terrain is Stage 2 (issue #15). terrainErrorCount stays in the
     diagnostics as a canary: it must remain 0 now that nothing swaps. */
  var terrainActive = 'ellipsoid';
  var terrainErrorCount = 0;

  /* Terrarium terrain (issue #15, keyless default of the locked ladder).
     The S3 bucket sends no CORS header, so tiles are fetched through the
     app's own origin: the page requests terrain/{z}/{x}/{y}.png relative
     to itself and CesiumGlobe's shouldInterceptRequest proxies the bytes
     from S3 natively. Decode + provider shape were PROVEN in the desktop
     repro before this integration (sampleTerrain at level 2: Everest
     5122 m, mid-Pacific -4286 m). The swap follows the issue #12 rule:
     assign only after a probe tile decodes through the real path, and
     revert to the ellipsoid if the provider reports repeated errors -
     a terrain failure must never block the globe. */
  var terrainProbe = 'not run';
  var TERRARIUM_MAX_LEVEL = 15;
  function terrariumTileUrl(z, x, y) {
    return 'terrain/' + z + '/' + x + '/' + y + '.png';
  }
  function decodeTerrariumTile(url) {
    return new Promise(function (resolve, reject) {
      var img = new Image();
      img.onload = function () {
        try {
          var canvas = document.createElement('canvas');
          canvas.width = 256; canvas.height = 256;
          var ctx = canvas.getContext('2d', { willReadFrequently: true });
          ctx.drawImage(img, 0, 0);
          var px = ctx.getImageData(0, 0, 256, 256).data;
          var heights = new Float32Array(256 * 256);
          for (var i = 0; i < 256 * 256; i++) {
            heights[i] = px[i * 4] * 256 + px[i * 4 + 1] + px[i * 4 + 2] / 256 - 32768;
          }
          resolve(heights);
        } catch (e) { reject(e); }
      };
      img.onerror = function () { reject(new Error('terrarium tile load failed: ' + url)); };
      img.src = url;
    });
  }
  function enableTerrarium() {
    decodeTerrariumTile(terrariumTileUrl(2, 1, 1)).then(function () {
      var provider = new Cesium.CustomHeightmapTerrainProvider({
        width: 256,
        height: 256,
        tilingScheme: new Cesium.WebMercatorTilingScheme(),
        callback: function (x, y, level) {
          if (level > TERRARIUM_MAX_LEVEL) return undefined;
          return decodeTerrariumTile(terrariumTileUrl(level, x, y));
        }
      });
      provider.errorEvent.addEventListener(function () {
        terrainErrorCount++;
        if (terrainErrorCount >= 6 && terrainActive === 'terrarium') {
          viewer.terrainProvider = new Cesium.EllipsoidTerrainProvider();
          terrainActive = 'ellipsoid (terrarium failed, reverted)';
        }
      });
      viewer.terrainProvider = provider;
      terrainActive = 'terrarium';
      terrainProbe = 'ok';
      addCredit('Terrain: Terrarium © Mapzen, AWS Terrain Tiles', 'https://github.com/tilezen/joerd/blob/master/docs/attribution.md');
    }).catch(function (e) {
      terrainProbe = 'failed: ' + (e && e.message || e);
    });
  }

  function reportCamera() {
    var cam = viewer.camera;
    var carto = Cesium.Cartographic.fromCartesian(cam.positionWC);
    if (!carto) return;
    bridge.onCameraChanged(JSON.stringify({
      longitude: Cesium.Math.toDegrees(carto.longitude),
      latitude: Cesium.Math.toDegrees(carto.latitude),
      height: carto.height,
      heading: Cesium.Math.toDegrees(cam.heading),
      pitch: Cesium.Math.toDegrees(cam.pitch),
      roll: Cesium.Math.toDegrees(cam.roll)
    }));
  }
  var reportTimer = null;
  viewer.camera.changed.addEventListener(function () {
    if (reportTimer) clearTimeout(reportTimer);
    reportTimer = setTimeout(reportCamera, 250);
  });
  viewer.camera.moveEnd.addEventListener(reportCamera);

  function setView(v) {
    viewer.camera.setView({
      destination: Cesium.Cartesian3.fromDegrees(v.longitude, v.latitude, Math.max(v.height, 10)),
      orientation: {
        heading: Cesium.Math.toRadians(v.heading || 0),
        pitch: Cesium.Math.toRadians(v.pitch == null ? -90 : v.pitch),
        roll: Cesium.Math.toRadians(v.roll || 0)
      }
    });
  }

  var tilesQueued = -1;
  viewer.scene.globe.tileLoadProgressEvent.addEventListener(function (queued) {
    tilesQueued = queued;
  });

  /* Issue #12 round-2 probes. The beta.2 report showed: imagery errors 0,
     terrain active with only 2 tile errors, no JS errors, globe visible as
     a black disk with an atmosphere rim. So the surface occludes but draws
     black. These probes split the remaining causes: worker processing,
     image loading, WebGL capabilities, and what a real rendered frame
     actually contains (sampled inside postRender, not between frames). */
  var probe = {
    frame: 'not sampled yet',
    workerBlob: 'pending',
    workerModule: 'pending',
    imageLoad: 'pending',
    webgl: 'pending',
    providersReady: 'pending'
  };

  (function probeWebgl() {
    try {
      var canvas = document.createElement('canvas');
      var gl2 = canvas.getContext('webgl2');
      var gl = gl2 || canvas.getContext('webgl');
      if (!gl) { probe.webgl = 'NO WebGL context at all'; return; }
      var dbg = gl.getExtension('WEBGL_debug_renderer_info');
      probe.webgl = {
        version: gl2 ? 'WebGL2' : 'WebGL1 only',
        renderer: dbg ? gl.getParameter(dbg.UNMASKED_RENDERER_WEBGL) : gl.getParameter(gl.RENDERER),
        maxTextureSize: gl.getParameter(gl.MAX_TEXTURE_SIZE)
      };
    } catch (e) { probe.webgl = 'threw: ' + (e && e.message || e); }
  })();

  (function probeWorkers() {
    try {
      var blob = new Blob(['onmessage=function(e){postMessage("ok")}'], { type: 'text/javascript' });
      var bw = new Worker(URL.createObjectURL(blob));
      var done = false;
      bw.onmessage = function () { done = true; probe.workerBlob = 'ok'; bw.terminate(); };
      bw.onerror = function (e) { probe.workerBlob = 'error: ' + (e.message || 'unknown'); };
      bw.postMessage('go');
      setTimeout(function () { if (!done && probe.workerBlob === 'pending') probe.workerBlob = 'timeout (no reply in 3s)'; }, 3000);
    } catch (e) { probe.workerBlob = 'threw: ' + (e && e.message || e); }
    try {
      var mw = new Worker(window.CESIUM_BASE_URL + 'Workers/createVerticesFromHeightmap.js', { type: 'module' });
      var settled = false;
      mw.onerror = function (e) { settled = true; probe.workerModule = 'error: ' + (e.message || 'unknown'); mw.terminate(); };
      setTimeout(function () {
        if (!settled) { probe.workerModule = 'loaded, no error in 3s (treated as ok)'; mw.terminate(); }
      }, 3000);
    } catch (e) { probe.workerModule = 'threw: ' + (e && e.message || e); }
  })();

  (function probeImage() {
    var img = new Image();
    img.crossOrigin = 'anonymous';
    var done = false;
    img.onload = function () { done = true; probe.imageLoad = 'ok (' + img.naturalWidth + 'x' + img.naturalHeight + ')'; };
    img.onerror = function () { probe.imageLoad = 'ERROR loading Esri tile z2'; };
    img.src = 'https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/2/1/1';
    setTimeout(function () { if (!done && probe.imageLoad === 'pending') probe.imageLoad = 'timeout (no load in 5s)'; }, 5000);
  })();

  var frameCount = 0;
  viewer.scene.postRender.addEventListener(function () {
    frameCount++;
    if (frameCount % 30 !== 0) return;
    try {
      var surface = viewer.scene.globe._surface;
      var gl = viewer.scene.context._gl;
      var w = viewer.canvas.width, h = viewer.canvas.height;
      var pts = [];
      for (var gy = 1; gy <= 5; gy++) for (var gx = 1; gx <= 5; gx++) {
        pts.push([Math.floor(w * gx / 6), Math.floor(h * gy / 6)]);
      }
      var dark = 0, total = 0;
      var one = new Uint8Array(4);
      pts.forEach(function (p) {
        gl.readPixels(p[0], p[1], 1, 1, gl.RGBA, gl.UNSIGNED_BYTE, one);
        total++;
        if (one[0] < 12 && one[1] < 12 && one[2] < 12) dark++;
      });
      probe.frame = {
        tilesToRenderInFrame: surface && surface._tilesToRender ? surface._tilesToRender.length : -1,
        darkPixelsOf25: dark,
        frame: frameCount
      };
      probe.providersReady = {
        imagery: baseLayer.imageryProvider ? !!baseLayer.imageryProvider.ready : 'no provider',
        terrain: viewer.terrainProvider ? !!viewer.terrainProvider.ready : 'no provider'
      };
    } catch (e) {
      probe.frame = 'sampling threw: ' + (e && e.message || e);
    }
  });

  /* Issue #12 diagnostics: the actual globe state, so a phone report
     carries values instead of guesses. Sent once 6s after ready (after
     the terrain swap has had its chance) and on demand from the app. */
  function collectDiagnostics() {
    var cam = viewer.camera;
    var carto = Cesium.Cartographic.fromCartesian(cam.positionWC);
    var surface = viewer.scene.globe._surface;
    return {
      quality: activeQuality,
      terrainProbe: terrainProbe,
      ionState: ionState,
      ionError: ionError,
      photoTileset: photoTilesetState,
      gev2Build: GEV2_BUILD,
      googleTileset: googleTilesetState,
      googleError: googleError,
      basemap: currentBasemap,
      esriImageryErrors: esriErrorCount,
      terrainActive: terrainActive,
      terrainTileErrors: terrainErrorCount,
      tilesQueued: tilesQueued,
      tilesToRender: surface && surface._tilesToRender ? surface._tilesToRender.length : -1,
      cameraHeightMeters: carto ? Math.round(carto.height) : -1,
      cameraLon: carto ? +Cesium.Math.toDegrees(carto.longitude).toFixed(4) : null,
      cameraLat: carto ? +Cesium.Math.toDegrees(carto.latitude).toFixed(4) : null,
      globeShow: viewer.scene.globe.show,
      canvasPixels: viewer.canvas.width + 'x' + viewer.canvas.height,
      probeFrame: probe.frame,
      probeWorkerBlob: probe.workerBlob,
      probeWorkerModuleCesium: probe.workerModule,
      probeImageLoad: probe.imageLoad,
      probeWebgl: probe.webgl,
      probeProvidersReady: probe.providersReady,
      jsErrors: jsErrors
    };
  }
  function sendDiagnostics() {
    bridge.onDiagnostics(JSON.stringify(collectDiagnostics(), null, 1));
  }

  /* Quality modes (plan Q20): Kotlin resolves Auto from device specs and
     sends the effective mode; the page only applies render knobs.
     resolutionScale is relative to the WebView's own pixel ratio. */
  var activeQuality = 'balanced';
  function setQuality(mode) {
    var presets = {
      high: { resolutionScale: 1.0, maximumScreenSpaceError: 1.5, tileCacheSize: 400 },
      balanced: { resolutionScale: 0.75, maximumScreenSpaceError: 2.0, tileCacheSize: 200 },
      battery_saver: { resolutionScale: 0.5, maximumScreenSpaceError: 3.0, tileCacheSize: 100 }
    };
    var preset = presets[mode] || presets.balanced;
    activeQuality = presets[mode] ? mode : 'balanced';
    viewer.resolutionScale = preset.resolutionScale;
    viewer.scene.globe.maximumScreenSpaceError = preset.maximumScreenSpaceError;
    viewer.scene.globe.tileCacheSize = preset.tileCacheSize;
  }

  /* Search support (issue #33): fly-to + a temporary pin. The pin is a
     plain Cesium entity - the layer system's real markers are Stage 4. */
  var searchPin = null;
  /* Issue #42: fly with flyToBoundingSphere so the TARGET ends up at the
     center of the screen. Plain camera.flyTo treats the destination as
     the camera's own position - the camera ended up directly above the
     target pitched at -55 degrees, leaving the target 35 degrees off
     the view axis (off-center, at low heights off-screen). The range
     converts the requested camera height to a slant distance at -55. */
  function flyTo(lon, lat, height) {
    var pitch = Cesium.Math.toRadians(-55);
    var range = height / Math.sin(Cesium.Math.toRadians(55));
    viewer.camera.flyToBoundingSphere(
      new Cesium.BoundingSphere(Cesium.Cartesian3.fromDegrees(lon, lat, 0), 0),
      {
        offset: new Cesium.HeadingPitchRange(0, pitch, range),
        duration: 2.2
      }
    );
  }
  function showPin(lon, lat, label) {
    clearPin();
    searchPin = viewer.entities.add({
      position: Cesium.Cartesian3.fromDegrees(lon, lat),
      point: {
        pixelSize: 12,
        color: Cesium.Color.fromCssColorString('#7FD4FF'),
        outlineColor: Cesium.Color.WHITE,
        outlineWidth: 2,
        heightReference: Cesium.HeightReference.CLAMP_TO_GROUND
      },
      label: {
        text: label,
        font: '14px sans-serif',
        fillColor: Cesium.Color.WHITE,
        outlineColor: Cesium.Color.BLACK,
        outlineWidth: 2,
        style: Cesium.LabelStyle.FILL_AND_OUTLINE,
        pixelOffset: new Cesium.Cartesian2(0, -22),
        heightReference: Cesium.HeightReference.CLAMP_TO_GROUND
      }
    });
  }
  function clearPin() {
    if (searchPin) {
      viewer.entities.remove(searchPin);
      searchPin = null;
    }
  }

  /* My Location dot (issue #34): its own entity, separate from the
     search pin - it marks where the user is, not a search result. */
  var locationDot = null;
  function showLocationDot(lon, lat) {
    if (locationDot) viewer.entities.remove(locationDot);
    locationDot = viewer.entities.add({
      position: Cesium.Cartesian3.fromDegrees(lon, lat),
      point: {
        pixelSize: 14,
        color: Cesium.Color.fromCssColorString('#2E7CF6'),
        outlineColor: Cesium.Color.WHITE,
        outlineWidth: 3,
        heightReference: Cesium.HeightReference.CLAMP_TO_GROUND
      }
    });
  }

  /* Layer rendering (issue #44): drawables arrive as JSON from the core.
     Entity ids are namespaced "layerId:entityId" so tap picking can route
     back to the owning layer. The search pin and location dot keep their
     own non-namespaced entities and are never layer picks. */
  var layerEntityIds = {}; // layerId -> [cesium entity id]
  function layerPointColor() { return Cesium.Color.fromCssColorString('#FFD166'); }
  function layerLineColor() { return Cesium.Color.fromCssColorString('#7FD4FF'); }
  function labelStyle(text, dy) {
    return {
      text: text, font: '13px sans-serif',
      fillColor: Cesium.Color.WHITE, outlineColor: Cesium.Color.BLACK,
      outlineWidth: 2, style: Cesium.LabelStyle.FILL_AND_OUTLINE,
      pixelOffset: new Cesium.Cartesian2(0, dy),
      heightReference: Cesium.HeightReference.CLAMP_TO_GROUND
    };
  }
  function renderLayer(layerId, drawables) {
    /* Follow survives a refresh: remember the tracked entity id, untrack
       before its entity is replaced, re-track the new one after. */
    var trackedId = viewer.trackedEntity ? viewer.trackedEntity.id : null;
    clearLayer(layerId);
    var ids = [];
    drawables.forEach(function (d, index) {
      var ent = null;
      if (d.type === 'point') {
        /* Stage 2 (issue #24): per-point styling from the layer —
           colour / pixel size / label on-off; defaults as before. */
        var st = d.style || {};
        var pointOpts = {
          id: layerId + ':' + d.entity.id,
          position: Cesium.Cartesian3.fromDegrees(
            d.entity.position.longitude, d.entity.position.latitude,
            d.entity.position.altitude || 0),
          point: {
            pixelSize: st.pixelSize || 11,
            color: st.color
              ? Cesium.Color.fromCssColorString(st.color) : layerPointColor(),
            outlineColor: Cesium.Color.WHITE, outlineWidth: 2,
            heightReference: Cesium.HeightReference.CLAMP_TO_GROUND
          }
        };
        if (st.showLabel !== false) {
          pointOpts.label = labelStyle(d.entity.title, -20);
        }
        ent = viewer.entities.add(pointOpts);
      } else if (d.type === 'track') {
        ent = viewer.entities.add({
          id: layerId + ':' + d.entityId + ':track',
          polyline: {
            positions: d.points.map(function (p) {
              return Cesium.Cartesian3.fromDegrees(p.longitude, p.latitude, p.altitude || 0);
            }),
            width: 3, material: layerLineColor()
          }
        });
      } else if (d.type === 'polygon') {
        var pst = d.style || {};
        var fill = pst.fillColor
          ? Cesium.Color.fromCssColorString(pst.fillColor) : layerLineColor();
        ent = viewer.entities.add({
          id: layerId + ':' + d.entityId + ':polygon',
          polygon: {
            hierarchy: new Cesium.PolygonHierarchy(d.vertices.map(function (p) {
              return Cesium.Cartesian3.fromDegrees(p.longitude, p.latitude, p.altitude || 0);
            })),
            material: fill.withAlpha(
              typeof pst.fillAlpha === 'number' ? pst.fillAlpha : 0.25),
            outline: true,
            outlineColor: pst.outlineColor
              ? Cesium.Color.fromCssColorString(pst.outlineColor) : layerLineColor()
          }
        });
      } else if (d.type === 'label') {
        ent = viewer.entities.add({
          id: layerId + ':label:' + index,
          position: Cesium.Cartesian3.fromDegrees(
            d.position.longitude, d.position.latitude, d.position.altitude || 0),
          label: labelStyle(d.text, 0)
        });
      }
      if (ent) ids.push(ent.id);
    });
    layerEntityIds[layerId] = ids;
    if (trackedId && ids.indexOf(trackedId) >= 0) {
      viewer.trackedEntity = viewer.entities.getById(trackedId);
    }
  }
  function clearLayer(layerId) {
    /* Stage 2 (issue #24): JS-module layers clear their own rendering. */
    var mod = window.GEVLayers && window.GEVLayers[layerId];
    if (mod && mod.onClear) {
      try { mod.onClear(); } catch (e) { noteError('layer ' + layerId + ' onClear: ' + e.message); }
    }
    var ids = layerEntityIds[layerId] || [];
    if (viewer.trackedEntity && ids.indexOf(viewer.trackedEntity.id) >= 0) {
      viewer.trackedEntity = undefined;
    }
    ids.forEach(function (id) {
      var ent = viewer.entities.getById(id);
      if (ent) viewer.entities.remove(ent);
    });
    delete layerEntityIds[layerId];
  }
  function setFollow(fullEntityId) {
    if (!fullEntityId) {
      viewer.trackedEntity = undefined;
      return;
    }
    var ent = viewer.entities.getById(fullEntityId);
    if (ent) viewer.trackedEntity = ent;
  }

  /* Tap picking (issue #44): a picked layer entity reports its layer +
     entity id; anything else (globe, pin, dot) reports a deselect. */
  var pickHandler = new Cesium.ScreenSpaceEventHandler(viewer.scene.canvas);
  pickHandler.setInputAction(function (movement) {
    var picked = viewer.scene.pick(movement.position);
    /* Entity picks carry an Entity object whose .id is the namespaced
       string; primitive picks (Stage 2 JS-module layers) carry the
       namespaced string as the primitive's id directly. JS-module
       layers may also install their own pick handling and report a
       ready-made card through bridge.onEntityCard — if a module claims
       this pick, the generic route stays out of the way. */
    var rawId = picked && picked.id;
    var id = typeof rawId === 'string' ? rawId : (rawId && rawId.id);
    if (typeof id === 'string' && id.indexOf(':') > 0) {
      var sep = id.indexOf(':');
      var layerId = id.substring(0, sep);
      var mod = window.GEVLayers && window.GEVLayers[layerId];
      if (mod && mod.onPick && mod.onPick(id.substring(sep + 1), movement.position)) {
        return;
      }
      bridge.onEntityPicked(layerId, id.substring(sep + 1));
    } else {
      bridge.onEntityPicked('', '');
    }
  }, Cesium.ScreenSpaceEventType.LEFT_CLICK);

  window.GEV = {
    setView: setView,
    resetGlobe: function () {
      clearPin();
      viewer.trackedEntity = undefined;
      setView({ longitude: 0, latitude: 18, height: 22000000, heading: 0, pitch: -90, roll: 0 });
    },
    renderLayer: renderLayer,
    clearLayer: clearLayer,
    layerMessage: function (layerId, payload) {
      var mod = window.GEVLayers && window.GEVLayers[layerId];
      if (mod && mod.onMessage) {
        try { mod.onMessage(payload); }
        catch (e) { noteError('layer ' + layerId + ' onMessage: ' + e.message); }
      }
    },
    setFollow: setFollow,
    flyTo: flyTo,
    showPin: showPin,
    clearPin: clearPin,
    showLocationDot: showLocationDot,
    setBasemap: setBasemap,
    setQuality: setQuality,
    setIonToken: setIonToken,
    setGoogleKey: setGoogleKey,
    reportDiagnostics: sendDiagnostics
  };

  /* Direct Google 3D Tiles (v2 plan Q9 tier 3): with the user's own
     Google Maps key (Map Tiles API), the same photorealistic tileset
     loads straight from Google instead of through ion. The loader is
     created lazily and only ever shown when the key is present and the
     basemap is selected; a load failure records googleError and leaves
     the current basemap untouched. Google attribution is added by the
     tileset's own credits through Cesium's credit display. */
  var googleKey = '';
  var googleError = '';
  var googleTileset = null;
  var googleTilesetState = 'not created';
  function ensureGoogleTileset() {
    if (googleTileset || googleTilesetState === 'loading' || !googleKey) return;
    googleTilesetState = 'loading';
    Cesium.Cesium3DTileset.fromUrl(
      'https://tile.googleapis.com/v1/3dtiles/root.json?key=' +
        encodeURIComponent(googleKey),
      { maximumScreenSpaceError: 16 }
    ).then(function (tileset) {
      googleTileset = tileset;
      googleTilesetState = 'ready';
      tileset.show = currentBasemap === 'google';
      viewer.scene.primitives.add(tileset);
    }).catch(function (e) {
      googleTilesetState = 'failed';
      googleError = 'google 3d tiles: ' + (e && e.message || e);
    });
  }
  function setGoogleKey(key) {
    googleKey = key || '';
    if (!googleKey && currentBasemap === 'google') {
      if (googleTileset) googleTileset.show = false;
      setBasemap('esri');
      bridge.onBasemapChanged('esri');
    }
    if (!googleKey && googleTileset) {
      viewer.scene.primitives.remove(googleTileset);
      googleTileset = null;
      googleTilesetState = 'not created';
    }
  }

  /* Cesium ion (issue #19, plan Q14): the user's own token unlocks the
     top of both ladders - Cesium World Terrain replaces Terrarium, and
     Google Photorealistic 3D (ion asset 2275207) becomes a basemap.
     Every swap follows the issue #12 rule: World Terrain is assigned
     only after the provider loads AND a sample height comes back; any
     failure leaves the keyless setup untouched and records ionError. */
  var ionState = 'no token';
  var ionError = '';
  /* Our own flag: Cesium's defaultAccessToken falls back to a built-in
     demo token when unset, so it cannot be used to tell whether the USER
     provided a token. */
  var ionTokenSet = false;
  var photoTileset = null;
  var photoTilesetState = 'not created';
  function ensurePhotoTileset() {
    if (photoTileset || photoTilesetState === 'loading') return;
    photoTilesetState = 'loading';
    Cesium.Cesium3DTileset.fromIonAssetId(2275207).then(function (tileset) {
      photoTileset = tileset;
      photoTilesetState = 'ready';
      tileset.show = currentBasemap === 'photorealistic';
      viewer.scene.primitives.add(tileset);
    }).catch(function (e) {
      photoTilesetState = 'failed';
      ionError = 'photorealistic: ' + (e && e.message || e);
    });
  }
  function setIonToken(token) {
    if (!token) {
      Cesium.Ion.defaultAccessToken = undefined;
      ionTokenSet = false;
      ionState = 'no token';
      if (currentBasemap === 'photorealistic') {
        setBasemap('esri');
        bridge.onBasemapChanged('esri');
      }
      if (terrainActive === 'cesium world terrain') enableTerrarium();
      return;
    }
    Cesium.Ion.defaultAccessToken = token;
    ionTokenSet = true;
    ionState = 'token set';
    Cesium.CesiumTerrainProvider.fromIonAssetId(1).then(function (provider) {
      var probe = [Cesium.Cartographic.fromDegrees(86.925, 27.9881)];
      return Cesium.sampleTerrain(provider, 2, probe).then(function (sampled) {
        if (typeof sampled[0].height !== 'number') {
          throw new Error('world terrain sample returned no height');
        }
        provider.errorEvent.addEventListener(function () {
          terrainErrorCount++;
          if (terrainErrorCount >= 6 && terrainActive === 'cesium world terrain') {
            terrainActive = 'ellipsoid (world terrain failed, reverted)';
            viewer.terrainProvider = new Cesium.EllipsoidTerrainProvider();
          }
        });
        viewer.terrainProvider = provider;
        terrainActive = 'cesium world terrain';
        addCredit('Cesium World Terrain', 'https://cesium.com/platform/cesium-ion/content/cesium-world-terrain/');
      });
    }).catch(function (e) {
      ionError = 'world terrain: ' + (e && e.message || e);
    });
  }

  /* Stage 2 (issue #24): JS layer modules. A module script (loaded
     after this file) registers itself as
       window.GEVLayers[layerId] = { onMessage, onClear, onPick }
     onMessage(payload) receives Kotlin payloads sent through
     GlobeController.sendLayerMessage; onClear() runs when the core
     clears the layer; onPick(entityId, screenPos) may claim a pick and
     answer with a ready-made card via bridge.onEntityCard (return
     true), or decline (return false) and let the generic pick route
     run. GEVInternal gives modules the viewer and bridge. */
  window.GEVLayers = window.GEVLayers || {};
  window.GEVInternal = {
    viewer: viewer,
    bridge: bridge,
    noteError: noteError
  };

  enableTerrarium();
  bridge.onGlobeReady();
  setTimeout(sendDiagnostics, 9000);
})();
