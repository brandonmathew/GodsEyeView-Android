'use strict';
/* GNSS integrity layer module (Stage 2, issue #29) — placeholder
   registered by the Stage 2 infra change (issue #24). The GNSS worker
   replaces this file's internals: it receives normalised aircraft
   integrity rows from Kotlin via GEV.layerMessage('gnss-integrity',
   payload), accumulates the rolling 30-minute window, bins into H3
   resolution-4 cells with the vendored h3-js (upstream's records.js
   logic), renders cell polygons, and answers picks with
   bridge.onEntityCard cards.
   Contract: window.GEVLayers['gnss-integrity'] = { onMessage,
   onClear, onPick } — see globe.js. */
(function () {
  window.GEVLayers = window.GEVLayers || {};
  window.GEVLayers['gnss-integrity'] = {
    onMessage: function () {},
    onClear: function () {},
    onPick: function () { return false; }
  };
})();
