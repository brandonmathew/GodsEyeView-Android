'use strict';
/* Satellites layer module (Stage 2, issue #27).
   Receives the TLE catalog from Kotlin via
   GEV.layerMessage('satellites', payload), propagates with the
   vendored satellite.js (global `satellite`), renders a Cesium
   PointPrimitiveCollection, and answers picks with
   bridge.onEntityCard cards.
   Contract: window.GEVLayers['satellites'] = { onMessage, onClear,
   onPick } — see globe.js.
   Classification is a simplified port of upstream classes.js
   heuristics (name patterns + CelesTrak group). SATCAT-based typing
   (object type / operational status) and the UCS purpose database
   are NOT ported — that SATCAT-based typing is a known gap, so some
   dead payloads may show under their working class here. */
(function () {
  window.GEVLayers = window.GEVLayers || {};

  var viewer = window.GEVInternal && window.GEVInternal.viewer;
  var bridge = (window.GEVInternal && window.GEVInternal.bridge) || {
    onEntityCard: function () {}
  };

  var CLASS_COLORS = {
    stations: '#fff6e5',
    imaging: '#4fd8ff',
    telecom: '#c89bff',
    starlink: '#54697f',
    probes: '#9fb3c4',
    debris: '#7d7468'
  };
  var CLASS_PIXEL_SIZES = {
    stations: 7,
    imaging: 5,
    telecom: 5,
    starlink: 3,
    probes: 5,
    debris: 3
  };

  var sats = new Map();
  var visibility = {
    stations: true,
    imaging: true,
    telecom: true,
    starlink: false,
    probes: true,
    debris: true
  };
  var collection = null;
  var timer = null;
  var totalCount = 0;

  function klassFor(name, group, norad) {
    var upper = String(name || '').toUpperCase();
    if (norad === 25544) return 'stations';
    if (/ISS|TIANHE|CSS \(|ZARYA|UNITY/.test(upper)) return 'stations';
    if (upper.indexOf('STARLINK') !== -1) return 'starlink';
    if (/DEB|R\/B|DEBRIS|FENGYUN.*DEB/.test(upper)) return 'debris';
    if (group === 'weather' || group === 'resource' || group === 'science' ||
        group === 'planet' || group === 'sar') return 'imaging';
    if (group === 'gps-ops' || group === 'glo-ops' || group === 'galileo' ||
        group === 'geo') return 'telecom';
    if (group === 'stations') return 'stations';
    if (group === 'starlink') return 'starlink';
    if (group === 'visual') return 'probes';
    return 'probes';
  }

  function tick() {
    if (!collection) return;
    var now = new Date();
    var gmst;
    try {
      gmst = satellite.gstime(now);
    } catch (e) {
      return;
    }
    sats.forEach(function (sat) {
      try {
        var pv = satellite.propagate(sat.satrec, now);
        if (!pv || !pv.position ||
            typeof pv.position.x === 'boolean' ||
            typeof pv.position.x !== 'number' ||
            isNaN(pv.position.x)) {
          if (sat.point) sat.point.show = false;
          sat.lastGeo = null;
          return;
        }
        var geo = satellite.eciToGeodetic(pv.position, gmst);
        var lat = satellite.degreesLat(geo.latitude);
        // Vendored satellite.js exposes degreesLong (degreesLng in
        // some builds); guard both names.
        var degreesLngFn = satellite.degreesLng || satellite.degreesLong;
        var lon = degreesLngFn(geo.longitude);
        var altM = geo.height * 1000;
        sat.point.position = Cesium.Cartesian3.fromDegrees(lon, lat, altM);
        var velKmS = null;
        if (pv.velocity &&
            typeof pv.velocity.x === 'number' &&
            typeof pv.velocity.y === 'number' &&
            typeof pv.velocity.z === 'number') {
          velKmS = Math.hypot(pv.velocity.x, pv.velocity.y, pv.velocity.z);
        }
        sat.lastGeo = {
          lat: lat,
          lon: lon,
          altKm: geo.height,
          velKmS: velKmS
        };
        sat.point.show = visibility[sat.klass] !== false;
      } catch (e) {
        if (sat.point) sat.point.show = false;
        sat.lastGeo = null;
      }
    });
  }

  function startTimer() {
    if (timer !== null) return;
    tick();
    timer = setInterval(tick, 1000);
  }

  function stopTimer() {
    if (timer !== null) {
      clearInterval(timer);
      timer = null;
    }
  }

  function clearRendering() {
    stopTimer();
    if (collection && viewer) {
      try { viewer.scene.primitives.remove(collection); } catch (e) {}
    }
    collection = null;
    sats.clear();
    totalCount = 0;
  }

  function handleCatalog(payload) {
    clearRendering();
    if (!viewer) return;
    collection = new Cesium.PointPrimitiveCollection();
    viewer.scene.primitives.add(collection);
    var groups = payload.groups || {};
    Object.keys(groups).forEach(function (group) {
      var entries = groups[group] || [];
      entries.forEach(function (entry) {
        var norad = entry.norad;
        if (norad == null || sats.has(norad)) return;
        var satrec;
        try {
          satrec = satellite.twoline2satrec(entry.l1, entry.l2);
        } catch (e) {
          return;
        }
        if (!satrec) return;
        var klass = klassFor(entry.name, group, norad);
        var point = collection.add({
          id: 'satellites:' + norad,
          position: Cesium.Cartesian3.fromDegrees(0, 0, 0),
          color: Cesium.Color.fromCssColorString(
            CLASS_COLORS[klass] || CLASS_COLORS.probes),
          pixelSize: CLASS_PIXEL_SIZES[klass] || 5
        });
        point.show = visibility[klass] !== false;
        sats.set(norad, {
          name: entry.name || '',
          group: group,
          klass: klass,
          satrec: satrec,
          point: point,
          epochMs: entry.epochMs || null,
          lastGeo: null,
          lastPos: null
        });
      });
    });
    totalCount = sats.size;
    startTimer();
  }

  function handleClasses(payload) {
    var incoming = payload.visibility || {};
    Object.keys(visibility).forEach(function (key) {
      if (typeof incoming[key] === 'boolean') visibility[key] = incoming[key];
    });
    sats.forEach(function (sat) {
      if (sat.point) sat.point.show = visibility[sat.klass] !== false;
    });
  }

  function onMessage(payload) {
    var data = payload;
    if (typeof data === 'string') {
      try { data = JSON.parse(data); } catch (e) { return; }
    }
    if (!data || !data.type) return;
    if (data.type === 'catalog') handleCatalog(data);
    else if (data.type === 'classes') handleClasses(data);
  }

  function onPick(entityId) {
    var norad = Number(entityId);
    var sat = sats.get(norad);
    if (!sat || !sat.lastGeo) return false;
    var altKm = sat.lastGeo.altKm;
    var fields = [
      { label: 'Class', value: sat.klass.toUpperCase() },
      { label: 'NORAD ID', value: String(norad) },
      { label: 'Group', value: sat.group },
      { label: 'Altitude', value: altKm.toFixed(0) + ' km' }
    ];
    if (sat.lastGeo.velKmS != null && isFinite(sat.lastGeo.velKmS)) {
      fields.push({
        label: 'Velocity',
        value: sat.lastGeo.velKmS.toFixed(2) + ' km/s'
      });
    } else {
      fields.push({ label: 'Velocity', value: '—' });
    }
    // satellite.js v6: satrec.no is mean motion in rad/min
    // (no_kozai in newer builds); guard both.
    var noRadPerMin = null;
    if (sat.satrec) {
      if (typeof sat.satrec.no === 'number' && sat.satrec.no > 0) {
        noRadPerMin = sat.satrec.no;
      } else if (typeof sat.satrec.no_kozai === 'number' &&
                 sat.satrec.no_kozai > 0) {
        noRadPerMin = sat.satrec.no_kozai;
      }
    }
    if (noRadPerMin != null) {
      var periodMin = (2 * Math.PI) / noRadPerMin;
      fields.push({
        label: 'Period',
        value: periodMin.toFixed(1) + ' min'
      });
    }
    if (sat.epochMs) {
      fields.push({
        label: 'TLE epoch',
        value: new Date(sat.epochMs).toISOString().slice(0, 10)
      });
    }
    var card = {
      id: String(norad),
      title: sat.name || String(norad),
      latitude: sat.lastGeo.lat,
      longitude: sat.lastGeo.lon,
      altitude: altKm * 1000,
      fields: fields
    };
    bridge.onEntityCard('satellites', JSON.stringify(card));
    return true;
  }

  function onClear() {
    clearRendering();
  }

  window.GEVLayers['satellites'] = {
    onMessage: onMessage,
    onClear: onClear,
    onPick: onPick,
    count: function () { return totalCount; }
  };
})();
