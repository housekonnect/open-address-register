# Session 2 plan: fix the maps, then close the demo gaps

Status: **approved 2026-10-08** (branch `bootstrap`).

Decisions taken at approval:
- The plan is approved as written, including the deviations and interpretations below.
- Record Store stays: no switch to Garage, no version change and no client change. The task 7 spike only tests and records results in ADR 0007.

Rules from `CLAUDE.md`, `docs/plan/bootstrap.md` and the ADRs still apply. One commit per task, in the order below. Nothing outside these tasks is changed.

## 1. Maps

### 1.1 Diagnosis so far (against the running stack, before any change)

I loaded the portal address page `/a/34532962432` in headless Chrome (Playwright, from a scratch directory) and checked every item on the brief's list.

| Check | Result |
|---|---|
| Browser-reachable URLs | OK. Portal and console pass `TILES_URL=http://localhost:3002` to the browser. No `martin:3000` reaches the browser. Martin's TileJSON returns `http://localhost:3002/{source}/{z}/{x}/{y}`. |
| Martin responses and CORS | OK. `/catalog` lists the four `tiles` views. TileJSON returns 200 with `Access-Control-Allow-Origin`. Tiles that cover the fixtures return 200 with data (for example `buildings/16/38701/32704`, 1,208 bytes). Empty tiles return 204, which is normal. |
| Map container | OK. The region is 568×320 px, the canvas 566×318 px, and `maplibre-gl.css` is loaded. |
| Client-side rendering | OK. `RegisterMap` is a `"use client"` component and creates the map in `useEffect`. |
| Content Security Policy | Not a factor. Neither app sets a CSP. |
| **Web worker** | **Fails. This is the root cause on the web.** The browser console shows a 404 followed by `Error: Worker failed to load`. maplibre-gl 6.13 builds its worker URL at runtime as `new URL("./maplibre-gl-worker.mjs", import.meta.url)`. After Next.js bundles it, `import.meta.url` is the chunk URL, so the worker is requested from `/_next/static/chunks/maplibre-gl-worker.mjs`, which does not exist. Turbopack emitted the file as `/_next/static/media/maplibre-gl-worker.<hash>.mjs`. Without a worker, MapLibre loads the TileJSON but never requests a single tile. The map shows only its background colour. |
| Coordinates | Contributing cause. Default centre `32.59, 0.3515` at zoom 16 shows latitudes 0.348–0.355, but the buildings lie between 0.343 and 0.350. Only a thin strip of data falls inside the opening view, in the console and in the field app. |
| Field app runtime | It is a development build (`expo-dev-client`, native `ios/` project), not Expo Go. However: (a) `<Camera trackUserLocation="default">` follows the device position. Simulators and emulators default to California, so the camera leaves the demo area. (b) `localhost` on an Android emulator means the emulator itself, so tiles, API and Authentik are unreachable unless the ports are forwarded. |

The console uses the same `RegisterMap` component, so it has the same worker failure. I'll confirm this after login, and the console Playwright test will cover it. The field app's causes will be confirmed on the Android emulator (task 6).

### 1.2 Fixes
- **Worker:** `RegisterMap` calls `setWorkerUrl()` with a URL the bundler resolves: `new URL("maplibre-gl/dist/maplibre-gl-worker.mjs", import.meta.url)`. If Turbopack does not resolve that, the fallback is a build step that copies the worker into `public/`. The worker is self-contained and imports nothing.
- **Opening view:** open on the bounds of the demo district instead of a hard-coded centre. Portal address pages still centre on the address.
- **Field camera:** stop following the user's position by default. A "my location" button recentres the map.
- **Field base URLs:** document `adb reverse` for the emulator, which keeps `localhost` working and keeps the token issuer identical. On a phone, use the LAN IP.

### 1.3 Self-hosted basemap
- **Data:** add `make basemap`.
  - It runs `pmtiles extract` from the pinned `protomaps/go-pmtiles` image against a pinned Protomaps daily build, with a bounding box and maximum zoom.
  - It writes the file into a new Docker volume, `ugaddress_basemap`, and verifies the file's SHA-256 against a value pinned in `infra/basemap/areas.env`.
  - There are two areas: `uganda` (the local default) and `demo` (the synthetic district only, used by CI).
  - `make up` runs `make basemap`, which does nothing when a verified file is already in the volume. The file is never committed.
- **Glyphs and sprites:** `make basemap` also downloads, from a pinned commit of `protomaps/basemaps-assets` with a pinned SHA-256:
  - the three Noto Sans font stacks the style uses (as PBF glyph ranges)
  - the v4 light and dark sprites
- **Serving:**
  - Martin mounts the volume read-only and serves the PMTiles file as the source `basemap`, through a `pmtiles:` entry in `martin/config.yaml`.
  - Martin can't serve prebuilt glyph PBFs or sprite sheets: it only generates them from TTF and SVG files. So the portal mounts the same volume read-only and serves `/map/fonts/...` and `/map/sprites/...` from a route handler, with long cache headers and CORS. No new service is added.
- **Style:**
  - `packages/ui` gets `buildBasemapStyle()`. It builds the layers with `@protomaps/basemaps` (light and dark flavours) and adds the register's layers on top: streets with name labels, buildings, and public entrances.
  - The portal serves the style at `/map/style.json` (`?theme=dark` for dark). All URLs in it are absolute and come from config (`TILES_PUBLIC_URL`, `PORTAL_PUBLIC_URL`), never from Docker hostnames.
  - The portal, the console and the field app all load this one style URL.
  - Highlighting (portal) and the custodian's own streets (console) are applied after load with `setFilter` / `setPaintProperty`, so the shared style stays the same.
- **Attribution:**
  - The basemap source carries `© OpenStreetMap contributors`.
  - The web maps show MapLibre's attribution control, not collapsed.
  - The field app enables its attribution control.
- **New config:**
  - `TILES_PUBLIC_URL` and `MAP_STYLE_URL` in Compose, with defaults, so the existing root `.env` keeps working unchanged.
  - `EXPO_PUBLIC_MAP_STYLE_URL` replaces `EXPO_PUBLIC_TILES_URL` in the field app.
- **Proof (Playwright):**
  - `apps/portal/e2e` and `apps/console/e2e` each:
    - open a map page
    - wait for MapLibre's `idle` event
    - assert that basemap and register tile requests returned 200
    - fail on any request to a host outside the stack
    - save a screenshot
  - The console test logs in through Authentik, including the MFA step from task 5.
  - A new CI job, `e2e`, runs `make env`, `make basemap BASEMAP_AREA=demo` and `make up`, runs both suites, and uploads the screenshots and traces as artefacts.

## 2. Search and reverse lookup
- **Migration V3:**
  - an immutable `unaccent` wrapper
  - a `register.search_document` table, one row per addressable object, holding the national ID, street name plus house number, building name and aliases
  - the table is kept current by triggers on the source tables
  - a GIN `tsvector` index (`simple` configuration over unaccented text) and a GIN `pg_trgm` index
- **`GET /v1/search?q=`:**
  - A query that parses as a national ID (4-3-4 with spaces or dashes, with or without `DEMO`) ranks the exact match first.
  - Then full-text rank (prefix tsquery), then trigram similarity, so typos still match.
  - Keyset cursor on `(score, id)`.
  - Residential entrance coordinates are redacted with the existing resolve rules.
- **`GET /v1/reverse?lat=&lon=&radius=`:**
  - KNN (`<->`) on a GiST index, limited by `ST_DWithin` on `geography`. `radius` defaults to 50 m, maximum 500 m.
  - **My interpretation of "street level only", please confirm:**
    - Anonymous and non-partner callers get the nearest street: the thoroughfare, postcode area, admin units and a distance rounded to 10 m. They get no house number, national ID or object.
    - Partner callers get the nearest addressable objects with full address and entrance coordinates.
    - This needs a new response schema, `ReversePage`. Nothing consumes the operation yet: it returns 501 today.
- **Portal:** the search box calls `/v1/search` and lists the results, each linking to `/a/{id}`.
- **Tests (Testcontainers):** ranking order, a typo still finding the street, the 4-3-4 format and `DEMO` prefix, cursor paging, and the public versus partner tier for both operations.

## 3. Approval screen (four-eyes)
- **Contract additions:**
  - `GET /v1/change-requests?state=` (the inbox: change requests in the caller's jurisdiction, cursor-paginated)
  - `GET /v1/change-requests/{id}` (with ETag; returns the diff, the evidence and `permissions.approve`)
  - `GET /v1/change-requests/{id}/photo` (streams the evidence photo through the API, never directly from storage)
  - `POST .../{id}/approve` and `POST .../{id}/return` (`return` requires a reason of 3–500 characters). Both require `Idempotency-Key`.
- **States:** V3 adds the state `returned` and a `decision_reason` column, and extends the decision CHECK to cover `returned`. "Return" means "back to the proposer", which differs from `rejected`.
  - Approve moves the request to `approved`. Applying approved changes to the register data stays out of scope, as in the bootstrap slice.
- **Four-eyes:**
  - The service answers 403 as a problem detail when the caller proposed the request. The existing database CHECK stays as the last line of defence.
  - `permissions.approve` is computed on the server, so the UI never guesses.
- **Console:** an `/inbox` page lists requests. Each detail view has:
  - a map preview: the shared style, with the target highlighted and the capture point marked
  - a diff: current value against proposed value
  - the evidence: photo, SHA-256, capture time and the mocked-location flag from task 6
  - Approve and Return buttons. Return opens a reason textarea. Both buttons are disabled with an explanation when `permissions.approve` is false.
- **Test user change:** the `approver` test user also joins `custodian-editor`. That way one person can propose and then see the four-eyes rule block them. Without this, no test user can show it.
- **Audit:** every decision writes an audit event with the decision and the reason.
- **Tests:**
  - a backend IT: the proposer gets 403, another approver succeeds, a return without a reason gets 400, and the audit event exists
  - a Vitest component test: the button is disabled for the proposer

## 4. Photo integrity
- **Field app:** at capture time it reads the photo bytes with `expo-file-system` and computes SHA-256 with `expo-crypto`. It stores the hash in the queue row and sends it as `photoSha256` in the capture metadata. The contract makes this field required.
- **Backend:** after the upload it reads the stored object back from Record Store and re-hashes it with a streaming `MessageDigest`.
  - On a mismatch, it deletes the object, creates nothing, and answers 422 as a problem detail with type `.../photo-integrity`.
  - On a match, it stores `photo_sha256` on the change request and includes it in the audit event.
- **Tests:**
  - an IT where the declared hash is wrong: 422, no object in the bucket, no change request, no audit event
  - an IT where the hash matches: the hash is stored and appears in the audit event
  - a field unit test that the queue carries the hash

## 5. MFA for custodians and administrators
- **Blueprint:**
  - an `authenticator_validate` stage with `not_configured_action: configure`, which offers TOTP and WebAuthn enrolment stages
  - the stage is bound into the default authentication flow, after the password stage
  - the binding is limited by group bindings to `custodian-editor`, `custodian-approver` and `steward-admin` (any match)
  - `field-verifier` is not affected. There is no switch to turn MFA off.
- **Docs:** a README section on how the test users enrol at first login, with an authenticator app (TOTP) or a passkey or security key (WebAuthn, works on `localhost`), and how to reset a device in the Authentik admin.
- **E2E:** the console Playwright test resets the test user's TOTP device through Authentik's API, using the bootstrap token from `.env`. It enrols a new device by reading the `otpauth://` secret from the flow executor's response and computes codes with `node:crypto`.
- **Report:** I'll check Authentik's documentation to confirm that authenticator stages, policy and group bindings, and events (audit) are in the open-source edition, and cite it.

## 6. Android
- **Build:** run `expo prebuild -p android` (the `android/` folder stays generated and git-ignored, like `ios/`), then `./gradlew assembleDebug`. This gives an installable development APK. The build is local, not EAS.
  - The existing `Pixel_9` AVD (Android 37 image) runs the app, with `adb reverse` for ports 3000, 3002, 8080 and 9000.
- **Step 5 of the README slice:** sign in, capture offline, sync, retry. I'll drive the emulator through `adb` (input, `uiautomator` dumps, screenshots) and report exactly which steps were verified automatically and which by inspection.
- **Mocked location:**
  - expo-location's `mocked` flag (Android) goes into the capture metadata as `locationMocked`.
  - The backend stores it on the change request, and the inbox shows it as a warning badge.
  - Such captures are never blocked.
  - Tests: an IT for persistence and a field unit test.
- **`docs/testing/android-device.md`:** how to install the APK on a low-end phone, how to set the LAN IP, and step 5 with offline and retry.

## 7. Small items
- **Healthchecks:** each Next app gets `GET /api/health` (200, no dependencies). The Compose healthcheck calls it with `node -e "fetch(...)"`, since there is no curl in the image.
- **Record Store spike:** 0.2.1 (2026-09-25) is still the newest release. GHCR has no newer tag; `latest` is 0.2.1.
  - I'll test multipart upload, the SDK's default checksums and `aws-chunked` encoding against 0.2.1. I'll also test against the newest image built from `main` if one is published.
  - Each test runs in a throwaway container on another port, never touching the stack's volume.
  - Results go into ADR 0007. No implementation change.

## Versions to add (checked 2026-10-08; checked again at implementation)

| Item | Version |
|---|---|
| `@protomaps/basemaps` | 5.7.2 |
| `@playwright/test` | 1.64.0 |
| `protomaps/go-pmtiles` image | v1.31.2 |
| `protomaps/basemaps-assets` | commit `028c18f` (2025-10-31) |
| Protomaps daily build | newest available at implementation, pinned by date and SHA-256 |

No existing version changes are planned.

## Deviations and interpretations for you to confirm
1. Glyphs and sprites are served by the portal from the shared volume, because Martin can't serve prebuilt PBF glyphs or sprite sheets. The style URL also lives on the portal.
2. "Street level only" for public reverse lookups, as defined in task 2.
3. The new `returned` state, and approval without applying the change.
4. The `approver` test user joins `custodian-editor` as well.
5. Playwright suites live in each app (`apps/*/e2e`), not in a new top-level package.

## Risks
- Protomaps deletes old daily builds. If the pinned build disappears, `make basemap` fails with instructions to re-pin. CI would then need a new pin.
- Whether the Android Gradle build runs on JDK 25. If it doesn't, the Android build alone uses JDK 21 (the backend stays on 25), and I'll record it.
- Driving camera capture on the emulator through `adb` may not be fully automatable. The report will say exactly what was verified.

## Verification
`make lint`, `make test`, `make check-generated`, both Playwright suites against the running stack, the Android emulator run, then push and wait for GitHub Actions. "CI is green" is claimed only after the run finishes green.
