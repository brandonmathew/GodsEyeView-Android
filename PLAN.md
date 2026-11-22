# God's Eye View Android v2 — Plan

Started 2026-11-22. A full Android port of bilawalsidhu/gods-eye-view (MIT),
parity target pinned to upstream v0.3.0 (commit d9d79eb). Separate project
from God's Eye View Android v1 (globe-only core) — both apps install side
by side. Planned one question at a time — 21 locked decisions (Q1–Q21),
then Brandon directed: write the full plan and start Stage 1.

- App name: **God's Eye View Android v2**
- Repo: **brandonmathew/GodsEyeView-Android** (public)
- Package ID: **com.godseyeview**
- License: **MIT** for our code. Data sources keep their own terms, listed
  in the in-app Credits section (incl. two non-commercial datasets, Q19).
- First stable release: **v1.0.0 = full parity with upstream v0.3.0**,
  proven by a feature-by-feature parity audit (Stage 9).

## The one-sentence version

The entire God's Eye View — all 34 layers, cockpit, sensor styles, HUD,
Director, voice/chat AI, street level, world clock — running on Android:
its own JS globe code adapted into a WebView, native Compose for all UI,
native Kotlin for all fetching, the user's own keys in Android Keystore,
and no backend anywhere.

## Locked decisions

### Scope and identity
- Q3: Public open-source release, like the original.
- Q4: Repo brandonmathew/GodsEyeView-Android, package com.godseyeview
  (Brandon's choices; distinct from v1 so both install side by side).
- Q10: Parity pinned to upstream v0.3.0 (d9d79eb). Later upstream
  releases are taken in as deliberate batch updates after parity.
- Q11: Staged betas; v1.0.0 only at full parity.
- Responsible-use line (from the original, ships in README + About):
  public data about events, assets, infrastructure, systems. No
  named-person search, face recognition, or tracking individuals. Data
  can be delayed, incomplete, modelled, or wrong; not for navigation,
  emergency response, or safety-critical use.

### Tech
- Q1: Hybrid engine — CesiumJS globe in a WebView + native Compose
  UI/HUD/chat + JNI helpers only where they help (SGP4, GRIB, H3/MGRS,
  fast math). Fully-native Cesium Native globe rejected for v2.
- Q5: Adapt the original's JS directly — its data + Cesium rendering
  modules run in the WebView; Vue UI discarded (Compose replaces it);
  server provider calls replaced by native Kotlin fetching. Clean
  rewrite rejected. Upstream updates are re-ported deliberately.
- Q6: CesiumJS 1.138.0, pinned exactly, bundled in the APK (the version
  the original's lockfile actually installs). Upgrades are their own
  issue with phone testing.
- Q7: Minimum Android 12 (API 31).
- Q8: Whole v1 bundle carried over — Kotlin + Jetpack Compose; Gradle +
  GitHub Actions; GitHub workflow (issue per feature/bug, branch per
  issue, PRs into beta, Brandon tests on phone, confirmed beta into
  main, tags on main = stable releases, done = code + CI green +
  phone-confirmed, "Refs #N" until confirmed, awaiting-confirmation
  label); in-app updater (own downloader, SHA-256 verify, Stable/Beta
  channels); Pool Helper versioning (stable X.0.0, hotfix X.0.N, betas
  X.0.0-beta.N prereleases, CI monotonic versionCode); one stable
  signing key, NEW for v2, only in GitHub secrets + local keys folder
  outside the repo; crash handler with NO test-crash button; MIT; no
  accounts/analytics/tracking; dark theme; portrait-first + landscape;
  tablets same UI.
- Rule carried from v1: never build until CI passes.

### Keys and data (no backend)
- Q2: NO backend. Bring-your-own-key: every user key (Cesium ion,
  Google, Mapillary, AISStream, FIRMS, TomTom, OpenAI, Gemini, etc.)
  is entered by the user and stored AES-GCM encrypted with Android
  Keystore, never plaintext, never backed up. All fetching is direct
  from the phone in native Kotlin (no CORS constraint). Voice session
  tokens are minted in-app with the user's own key.
- Q9: Basemap ladder, all three tiers like the original — (1) keyless
  Esri satellite + Terrarium terrain, OSM fallback; (2) user ion token
  unlocks Google Photorealistic 3D + Cesium World Terrain; (3) user
  Google Maps key unlocks direct Google 3D Tiles + Google Places
  search + Google Street View (terms-gated to the Google basemap).
- Q16: Optional own Overpass server URL in Settings (like the
  original's OVERPASS_UPSTREAMS). Set: traffic, installations, ALPR,
  OSM Places run fully. Blank: reduced public-Overpass behaviour with
  the limits shown honestly in the UI.
- Q19: Bundled non-commercial data ships like the original —
  TeleGeography submarine cables (CC BY-NC-SA) and Bhote Koshi imagery
  (CC BY-NC) with attribution and terms in Credits; datacenters/dams
  ODbL. MIT covers our code only.

### Features
- Q20: First launch = the original's mission picker (Live Contacts /
  Space Missions / Environmental / Explore Manually) + Power Up key
  panel (skippable; keyless app is complete). Later launches restore
  the last view.
- Q13: Voice + typed chat = OpenAI or Gemini, one Settings choice for
  both, like the original. API-key voice keeps the $2 warning / $5
  per-session hard cap. Codex/ChatGPT OAuth voice dropped (no Android
  equivalent).
- Q14: MCP = the original's tool catalog ported to Kotlin (same names,
  schemas, error codes; powers voice + chat) PLUS an optional in-app
  MCP server — off by default, foreground-only, local network, token/QR
  pairing. Stdio transport dropped (platform-impossible).
- Q15: All original v0.3.0 languages by v1.0.0, incl. RTL (ar/he),
  converted from upstream catalogs. English during Stages 1–8.
- Q17: CCTV = stills first (full ~7,350-camera catalogue in 3D), then
  native HLS live video source-by-source where it streams direct.
  Stills are never labelled live video.
- Q18: Local receivers = network receivers only. USB RTL-SDR dongle
  support dropped.
- Q21: Full performance + cache settings in 1.0.0 — device auto-detect
  quality with High / Balanced / Battery Saver override, tile cache
  size limit + clear, all driven by ONE shared internal quality
  profile (never per-layer hardcoded limits). No offline area
  downloads.

### Drop / redesign ledger (open, never silent)
- Dropped: MCP stdio transport (redesigned per Q14); Codex OAuth voice;
  USB RTL-SDR dongle; Snow sensor style (retired upstream).
- Redesigned: voice token minting in-app (Q2/Q13); CCTV stills-first
  (Q17); Director authoring for phone (record mode + anchor editor);
  street-level viewer uses embedded web islands for Mapillary and
  Google providers (their viewers are web-only; Google's is also
  terms-gated to the Google basemap); AIS is a per-phone WebSocket
  (no shared fan-out without a backend).

## Architecture sketch

```
:app          Compose shell, navigation, mission picker, Power Up,
              settings, updater, crash handler, MCP server (Stage 8)
:core-globe   WebView + bundled CesiumJS 1.138 + adapted upstream JS
              (data + render modules), Kotlin<->JS bridge, camera,
              basemap ladder, world clock, quality profile
:core-ui      Shared Compose components: layer panel, detail cards,
              HUD overlay, search, credits
:layer-api    Layer contract, registry, entity model, fetch manager
              (core-managed polling/cache/backoff/stale, as v1)
:native       JNI helpers (SGP4, GRIB decode, H3/MGRS) behind Kotlin
:layer-<name> One module per upstream layer, adapted from its JS
```

Bridge rule (from v1): Kotlin owns state, networking, storage, keys,
and lifecycle. The WebView owns rendering and the adapted upstream
globe logic. Layers never talk to the WebView directly except through
the core's layer API.

## Build stages to v1.0.0 (Q12)

- **Stage 1 — Foundation:** repo, CI, signing, Gradle skeleton, Compose
  shell, CesiumJS 1.138 globe + 3-tier basemap ladder, camera/search/
  My Location/share, layer system + world clock, Settings + Keystore
  key store (all provider keys), quality profile + performance/cache
  settings, Credits, crash handler, in-app updater, first-run mission
  picker + Power Up panel. Beta APK on Brandon's phone is the gate.
- **Stage 2 — Movement:** flights, military flights, satellites
  (SGP4 via JNI), vessels (AIS WebSocket), GNSS integrity.
- **Stage 3 — Events & environment:** earthquakes, FIRMS fires, fire
  perimeters, weather + wind (GRIB), observed weather, cyclones,
  air quality.
- **Stage 4 — Infrastructure & ground:** installations, drone
  airspace, submarine cables, datacenters/dams, shipwrecks, traffic,
  directions, OSM Places, ghost maps, recent imagery, news.
- **Stage 5 — Surface & audio:** transit, rail, bikeshare, radio,
  wildlife sightings, local network receivers.
- **Stage 6 — Cameras:** CCTV (stills, then HLS), ALPR, street level.
- **Stage 7 — Interaction:** cockpit + contacts, 122 procedural 3D
  fleet models, sensor styles (CRT/NVG/FLIR/Noir), military HUD +
  detection overlay, Director/scenes/annotations, bit-compatible
  share links.
- **Stage 8 — AI:** voice (OpenAI + Gemini), typed chat, analyst Q&A,
  AI HUD summary, MCP catalog + optional in-app MCP server.
- **Stage 9 — Parity audit + i18n:** feature-by-feature audit against
  upstream v0.3.0, all original languages incl. RTL, hardening,
  then v1.0.0.

Each stage follows the Q8 workflow: issues first, branch per issue,
PR to beta, CI green before any build, Brandon confirms on his phone
before the stage is called done.
