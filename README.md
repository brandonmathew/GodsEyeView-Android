# God's Eye View Android

A mobile-first, photorealistic 3D globe for Android, built from scratch in
Kotlin. Inspired by, but not a port of,
[bilawalsidhu/gods-eye-view](https://github.com/bilawalsidhu/gods-eye-view) (MIT).

**Status: v1.0.0 — the core is complete.** The core ships with zero data
layers on purpose: it is the globe plus the modular layer system every
future layer (flights, satellites, ships, ...) plugs into.
See [PLAN.md](PLAN.md) for the locked decisions and build stages.

## What the core does

- CesiumJS globe (bundled in the APK, pinned version) in a WebView that
  renders only the globe; all UI around it is native Jetpack Compose.
- Keyless basemap ladder: Esri World Imagery with Terrarium terrain, and
  an OpenStreetMap fallback. A Cesium ion token you paste in Settings
  (stored encrypted on the device) unlocks Cesium World Terrain and
  Google Photorealistic 3D.
- Touch camera matched to the original's feel, full Earth from orbit on
  first launch, and the app reopens exactly where you left it.
- Keyless search (bundled places, then Photon/Nominatim), tap-only
  My Location, and share-current-view.
- A layer system: every layer is its own Gradle module depending only on
  `:layer-api` (points, tracks, polygons, labels; tap-to-select with a
  generic detail card; follow/track camera; world clock; core-managed
  fetching with polling, backoff and stale marking).
- Settings for basemap, quality (auto-detected from the device, with
  manual override), tile-cache size, and the in-app updater: the app
  checks GitHub Releases itself, downloads updates in-app, verifies the
  SHA-256 before installing, and offers Stable and Beta channels.
- No accounts, no analytics, no tracking.

## Responsible use

This app visualises public data about events, assets, infrastructure, and
systems. It does not do named-person search, face recognition, or tracking
individuals. Data can be delayed, incomplete, modelled, or wrong; it is not
for navigation, emergency response, or other safety-critical use.

## Build

Requires JDK 17 and the Android SDK. CI (GitHub Actions) builds every beta
and release; see `.github/workflows/`.

## License

MIT for our code - see [LICENSE](LICENSE). Data sources and libraries keep
their own terms; they are listed in the in-app Credits section and in
[DATA_SOURCES.md](DATA_SOURCES.md).
