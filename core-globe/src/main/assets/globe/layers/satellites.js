'use strict';
/* Satellites layer module (Stage 2, issue #27) — placeholder registered
   by the Stage 2 infra change (issue #24). The satellites worker
   replaces this file's internals: it receives the TLE catalog from
   Kotlin via GEV.layerMessage('satellites', payload), propagates with
   the vendored satellite.js, renders a Cesium point primitive
   collection, and answers picks with bridge.onEntityCard cards.
   Contract: window.GEVLayers['satellites'] = { onMessage, onClear,
   onPick } — see globe.js. */
(function () {
  window.GEVLayers = window.GEVLayers || {};
  window.GEVLayers['satellites'] = {
    onMessage: function () {},
    onClear: function () {},
    onPick: function () { return false; }
  };
})();
