'use strict';
/* GNSS integrity layer module (Stage 2, issue #29).
   Receives normalised aircraft integrity rows from Kotlin via
   GEV.layerMessage('gnss-integrity', payload), accumulates the rolling
   30-minute window (upstream src/layers/gnss/records.js:
   accumulateGnssObservations / pruneGnssObservations), bins into H3
   resolution-4 cells with the vendored h3-js (binGnssCells /
   gnssIntegrityLevel, gpsjam.org's formula and 2% / 10% bands), renders
   cell polygons, and answers picks with bridge.onEntityCard cards.
   Contract: window.GEVLayers['gnss-integrity'] = { onMessage, onClear,
   onPick } — see globe.js. */
(function () {
  var WINDOW_MS = 30 * 60 * 1000;
  var RES = 4;
  var LEVEL_COLORS = {
    low: '#3fb950',
    medium: '#e3b341',
    high: '#f85149'
  };

  /* `${hex}|${cell}` -> { cell, seenAt, degradedAt } */
  var store = new Map();
  /* cell -> { total, degraded, level } for the last render, for picks. */
  var tallies = new Map();
  /* Cesium entity ids added by the last render. */
  var entityIds = [];
  var timer = null;

  function viewer() { return window.GEVInternal.viewer; }
  function bridge() { return window.GEVInternal.bridge; }

  function prune(now) {
    store.forEach(function (entry, key) {
      if (now - entry.seenAt > WINDOW_MS) {
        store.delete(key);
      } else if (entry.degradedAt != null && now - entry.degradedAt > WINDOW_MS) {
        entry.degradedAt = null;
      }
    });
  }

  function removeEntities() {
    var v = viewer();
    entityIds.forEach(function (id) {
      var ent = v.entities.getById(id);
      if (ent) v.entities.remove(ent);
    });
    entityIds = [];
  }

  function render() {
    tallies = new Map();
    store.forEach(function (entry) {
      var tally = tallies.get(entry.cell) || { total: 0, degraded: 0, level: 'low' };
      tally.total += 1;
      if (entry.degradedAt != null) tally.degraded += 1;
      tallies.set(entry.cell, tally);
    });
    tallies.forEach(function (tally) {
      var percent = tally.total > 0
        ? 100 * Math.max(0, tally.degraded - 1) / tally.total
        : 0;
      tally.level = percent > 10 ? 'high' : percent >= 2 ? 'medium' : 'low';
    });

    removeEntities();
    var v = viewer();
    tallies.forEach(function (tally, cell) {
      var color = Cesium.Color.fromCssColorString(LEVEL_COLORS[tally.level]);
      var positions = h3.cellToBoundary(cell).map(function (pair) {
        return Cesium.Cartesian3.fromDegrees(pair[1], pair[0]);
      });
      var ent = v.entities.add({
        id: 'gnss-integrity:' + cell,
        polygon: {
          hierarchy: new Cesium.PolygonHierarchy(positions),
          material: color.withAlpha(0.35),
          outline: true,
          outlineColor: color
        }
      });
      entityIds.push(ent.id);
    });
  }

  function startTimer() {
    if (timer !== null) return;
    timer = setInterval(function () {
      prune(Date.now());
      render();
    }, 60 * 1000);
  }

  window.GEVLayers = window.GEVLayers || {};
  window.GEVLayers['gnss-integrity'] = {
    onMessage: function (payload) {
      if (!payload || payload.type !== 'rows') return;
      startTimer();
      var now = Date.now();
      var observedAt = Math.min(payload.observedAtMs || now, now);
      if (now - observedAt <= WINDOW_MS) {
        var rows = payload.rows || [];
        for (var i = 0; i < rows.length; i++) {
          var row = rows[i];
          if (!row || !Number.isFinite(row.lat) || !Number.isFinite(row.lon)) continue;
          var cell = h3.latLngToCell(row.lat, row.lon, RES);
          var key = row.hex + '|' + cell;
          var previous = store.get(key);
          var previousDegradedAt = previous ? previous.degradedAt : null;
          store.set(key, {
            cell: cell,
            seenAt: Math.max(previous ? previous.seenAt : observedAt, observedAt),
            degradedAt: row.degraded
              ? Math.max(previousDegradedAt != null ? previousDegradedAt : observedAt, observedAt)
              : previousDegradedAt
          });
        }
      }
      prune(now);
      render();
    },
    onClear: function () {
      if (timer !== null) {
        clearInterval(timer);
        timer = null;
      }
      removeEntities();
      store.clear();
      tallies.clear();
    },
    onPick: function (entityId) {
      var cell = entityId;
      var tally = tallies.get(cell);
      if (!tally) return false;
      var center = h3.cellToLatLng(cell);
      var lat = center[0];
      var lon = center[1];
      var level = tally.level;
      var percent = tally.total > 0
        ? 100 * Math.max(0, tally.degraded - 1) / tally.total
        : 0;
      var fields = [
        { label: 'Aircraft in cell', value: String(tally.total) },
        { label: 'Low-accuracy reports', value: String(tally.degraded) },
        { label: 'Degraded share', value: percent.toFixed(1) + '%' },
        { label: 'Level', value: level },
        { label: 'Reading', value: 'Low navigation accuracy reported by ADS-B aircraft' }
      ];
      if (level !== 'low') {
        fields.push({
          label: 'Interpretation',
          value: 'Suspected GNSS interference is one possible cause — unvalidated; avionics faults also report low values'
        });
      }
      fields.push(
        { label: 'Window', value: 'Rolling 30 minutes, visited views' },
        { label: 'Classifier', value: 'gev-nic-nacp-v2 (NIC/NACp, 14 CFR 91.227 basis)' }
      );
      var card = {
        id: cell,
        title: 'GNSS integrity — ' + level.toUpperCase(),
        latitude: lat,
        longitude: lon,
        altitude: 0,
        fields: fields
      };
      bridge().onEntityCard('gnss-integrity', JSON.stringify(card));
      return true;
    }
  };
})();
