# Data sources and libraries

Every provider, dataset and library God's Eye View Android uses, with its
terms. Our own code is MIT (see [LICENSE](LICENSE)); the entries below keep
their own terms. The same list appears in the app under Settings → Credits.

## Imagery and terrain

| Source | What the app uses it for | Terms |
| --- | --- | --- |
| Esri World Imagery | Default keyless satellite basemap | Esri terms of use; attribution "Esri, Maxar, Earthstar Geographics" shown by the basemap. https://www.esri.com/en-us/legal/terms/full-master-agreement |
| OpenStreetMap | Fallback basemap | © OpenStreetMap contributors, Open Database License (ODbL). https://www.openstreetmap.org/copyright |
| Terrarium (AWS Terrain Tiles) | Default keyless terrain (heightmap tiles from the public terrain bucket originally produced by Mapzen) | Terrain data is a compilation of public sources (USGS, NASA and others) with varying terms; tile service provided as-is. https://registry.opendata.aws/terrain-tiles/ |
| Cesium ion | Optional, only with the user's own ion token: access to World Terrain and Google Photorealistic 3D Tiles | Cesium ion terms of service. https://cesium.com/legal/terms-of-service/ |
| Cesium World Terrain | Terrain when an ion token is set (ion asset 1) | Cesium ion terms; data © Cesium and data providers. |
| Google Photorealistic 3D Tiles | Optional 3D basemap when an ion token is set (ion asset 2275207) | Google Maps Platform terms; attribution shown on the globe as required. https://cloud.google.com/maps-platform/terms |

## Search

| Source | What the app uses it for | Terms |
| --- | --- | --- |
| Bundled places | Small offline gazetteer (about 37 well-known places) searched first, no network | Compiled for this app from public geographic knowledge; no third-party dataset is redistributed. |
| Photon (Komoot) | Keyless place search fallback | Search data is OpenStreetMap data © OpenStreetMap contributors, ODbL. https://photon.komoot.io |
| Nominatim | Keyless place search, second fallback | OpenStreetMap data © OpenStreetMap contributors, ODbL; Nominatim usage policy applies. https://operations.osmfoundation.org/policies/nominatim/ |

## Libraries

| Library | What the app uses it for | License |
| --- | --- | --- |
| CesiumJS 1.147.0 (pinned, bundled in the APK) | Renders the globe in the WebView | Apache License 2.0. https://github.com/CesiumGS/cesium |
| Jetpack Compose / AndroidX | Native UI, WebView, FileProvider | Apache License 2.0. |
| Kotlin | App language | Apache License 2.0. |

## Updates

Releases are published on this repository's GitHub Releases page; the
in-app updater downloads them from there and verifies each APK against
the SHA-256 checksum published with the release.
