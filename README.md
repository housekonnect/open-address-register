# National Address Register (UGAddress)

An independent, open-source address register for Uganda, designed so the government can adopt and run it.

**One register, many custodians.** Local governments, KCCA and MoICT maintain streets, house numbers and postcodes inside one PostgreSQL/PostGIS register; everyone else reads it through one API.

> Status: skeleton. The repository proves the architecture end to end with **synthetic data**. It is not a production system, and the national ID format is a draft ([ADR 0005](docs/adr/0005-national-id-format.md)).

| Part | Path | Stack |
|---|---|---|
| Register backend | [backend/register-app](backend/register-app) | Java 25, Spring Boot 4.1 (modular monolith), jOOQ, Flyway, PostGIS |
| National ID library | [backend/national-id](backend/national-id), [packages/national-id](packages/national-id) | pure Java / TypeScript, shared test vectors |
| API contract | [contracts/register.v1.yaml](contracts/register.v1.yaml) | OpenAPI 3.1, generated server interfaces and TS client |
| Public portal | [apps/portal](apps/portal) | Next.js, MapLibre, shadcn/ui |
| Custodian console | [apps/console](apps/console) | Next.js, Authentik login (OIDC code flow) |
| Field app | [apps/field](apps/field) | Expo / React Native, offline SQLite queue, PKCE |
| Local environment | [infra/compose](infra/compose) | Docker Compose: PostGIS, Authentik, Record Store, Martin |
| Basemap | [infra/basemap](infra/basemap) | `make basemap`: self-hosted Protomaps extract of OpenStreetMap, glyphs and sprites |

Read [CLAUDE.md](CLAUDE.md) for conventions and [docs/adr](docs/adr) for the decisions behind them.

## Getting started

Requirements: **Docker**, **JDK 25** (`sdk env` picks it up from [.sdkmanrc](.sdkmanrc)), **Node 24+** and **pnpm** (`corepack enable` installs the pinned version).

```sh
make up
```

`make up` creates `.env` with random local secrets (once), runs `make basemap` (below), builds the backend jar (jOOQ code generation runs PostGIS in Testcontainers), builds the images and waits until every service is healthy. The first run downloads all images and takes several minutes; on Apple Silicon the PostGIS image runs under emulation.

| Service | URL |
|---|---|
| Portal (public lookup) | http://localhost:3000 |
| Console (custodians) | http://localhost:3001 |
| Register API | http://localhost:8080/v1 |
| Authentik | http://localhost:9000 |
| Martin (vector tiles) | http://localhost:3002/catalog |
| Record Store (S3 API) | http://localhost:7600 |

`make down` stops everything and keeps the data; `make db-reset` recreates the register database with fresh fixtures.

### Maps

Every map (portal, console, field app) loads one style, served by the portal at http://localhost:3000/map/style.json: a self-hosted [Protomaps](https://protomaps.com) basemap built from OpenStreetMap, with the register's streets, buildings and public entrances on top. Nothing is fetched from outside the stack at runtime.

- `make basemap` downloads a Uganda extract (about 630 MB, once) with the `pmtiles extract` CLI, plus the fonts and sprites from Protomaps' basemaps assets, verifies every file against the SHA-256 pinned in [infra/basemap/pins.env](infra/basemap/pins.env) and stores them in the Docker volume `ugaddress-basemap`. Martin serves the tiles; the portal serves fonts and sprites. The files are never committed.
- `make basemap BASEMAP_AREA=demo` (and `make up BASEMAP_AREA=demo`) uses a 4 MB extract covering only the synthetic district, as CI does.
- Maps show "© OpenStreetMap contributors" (ODbL).
- `make e2e` runs the Playwright map tests of the portal and the console against the running stack. They wait for the map to finish rendering, check that basemap and register tiles returned 200 and that no request left the stack, and save a screenshot. The first time, install the browser with `pnpm --filter @ugaddress/portal exec playwright install chromium`, or set `PLAYWRIGHT_CHANNEL=chrome` to use an installed Chrome.

> `.env` holds the secrets the data volumes were initialised with. Do not regenerate it while volumes exist; to start over completely, run `docker compose -f infra/compose/docker-compose.yml --env-file .env down -v` first, then delete `.env`.

### Test users

Authentik is configured automatically from [infra/compose/authentik/blueprints/ugaddress.yaml](infra/compose/authentik/blueprints/ugaddress.yaml). All four synthetic users share one password, generated into `.env`:

```sh
grep TEST_USER_PASSWORD .env
```

| Username | Group | Custodian | Can do |
|---|---|---|---|
| `editor` | custodian-editor | demo-city (Amani Parish) | submit corrections in the console |
| `approver` | custodian-approver, custodian-editor | demo-city | decide change requests in the console inbox; also proposes, to show the four-eyes rule |
| `verifier` | field-verifier | demo-city | log in to the field app and upload captures |
| `steward` | steward-admin | demo-ministry (whole demo district) | — (reserved for administration) |

The Authentik administrator is `akadmin`, password `AUTHENTIK_BOOTSTRAP_PASSWORD` in `.env`.

## The end-to-end slice

The fixtures ([db/fixtures/R__demo_area.sql](db/fixtures/R__demo_area.sql)) describe an invented district with two parishes, four streets and 50 buildings, each with an entrance and a `demo-plot` alias. Every ID is a **demonstration** ID and shows a `DEMO` marker. There is no personal data.

Sample IDs:

| National ID | Address | Alias |
|---|---|---|
| `9526 184 5754` | 1 Amani Avenue | `demo-plot:AMA-0001` |
| `5179 768 8621` | 6 Amani Avenue (facility) | `demo-plot:AMA-0006` |
| `1198 907 5024` | 1 Jacaranda Close | `demo-plot:JAC-0001` |
| `3453 296 2432` | 1 Mirembe Road | `demo-plot:MIR-0001` |

A shortcut for database checks used below:

```sh
alias regsql='docker compose -f infra/compose/docker-compose.yml --env-file .env exec -T postgis psql -U postgres -d register -c'
```

### 1. Start everything

```sh
make up
docker compose -f infra/compose/docker-compose.yml --env-file .env ps   # all services healthy
```

### 2. Check the fixtures

```sh
regsql "select kind, national_id_status, count(*) from register.addressable_object group by 1, 2"
regsql "select register.verify_audit_chain() is null as audit_chain_intact"
```

Expected: 42 buildings, 8 facilities and 50 entrances, all `demonstration`; the audit chain is intact.

### 3. Portal: search and resolve an ID

1. Open http://localhost:3000 and type `9526 184 5754` (spaces, dashes or the `DEMO` prefix are all accepted). Change one digit to see the check digit catch the typo before any request is made.
2. Press **Search**. A valid ID or reference opens its address page directly. It shows the address lines, the `DEMO` ID, a map of the address on the self-hosted OpenStreetMap basemap with the building highlighted, and a QR code that links back to the page.
3. Residential entrance coordinates are not shown publicly; the API returns them only to callers with the `register:partner` scope.

4. Search for free text instead, e.g. `amani avnue` (typo included) or `Health Centre`: the results list streets, addresses and places, best match first.

The same through the API:

```sh
curl "http://localhost:8080/v1/resolve?ref=demo-plot:AMA-0001"
curl "http://localhost:8080/v1/search?q=jacarnda%20close"        # full-text + trigram, typo-tolerant, cursor-paginated
curl "http://localhost:8080/v1/reverse?lat=0.3502&lon=32.5935"   # public: street level only
```

Reverse lookups return the nearest street (with postcode and admin units, distance rounded to 10 m) to the public, and the nearest addressed objects with their entrance coordinates to partners (`register:partner`).

### 4. Console: submit a correction

1. Open http://localhost:3001 and **Sign in** as `editor`.
2. The map highlights the streets of demo-city (Amani Avenue, Jacaranda Close).
3. Click a building on Amani Avenue, describe the correction (e.g. "The plate shows 5A") and optionally a new house number, then **Submit correction**. The confirmation shows the change request reference.
4. Check the change request and its audit event:

   ```sh
   regsql "select id, kind, state, source, summary from register.change_request order by created_at desc limit 3"
   regsql "select seq, action, entity_id from register.audit_event order by seq desc limit 3"
   ```

Submitting a building on Mirembe Road (another custodian's area) is refused: row-level security only lets a custodian write inside its jurisdiction.

### 4b. Console: approve or return (four-eyes rule)

1. Sign out, then **Sign in** as `approver` and open **Inbox**. It lists the submitted change requests of demo-city, oldest first.
2. Open the correction from step 4: a map preview with the building highlighted, the diff (current and proposed house number) and the evidence (photo and capture point for field captures).
3. **Approve** it, or write a reason and **Return to proposer**. Every decision writes an audit event:

   ```sh
   regsql "select seq, action, payload from register.audit_event where action like 'change_request.%' order by seq desc limit 3"
   ```

4. `approver` is also an editor: submit a correction as `approver` on the map, then open it in the inbox. **Approve** and **Return** are disabled with the four-eyes explanation, and the API answers `403` if called directly.

### 5. Field app: capture offline, sync once

The field app needs a development build (MapLibre and SQLite are native modules), so you need Xcode with an iOS simulator or Android Studio with an emulator.

```sh
cp apps/field/.env.example apps/field/.env   # localhost works for the iOS simulator
pnpm --filter @ugaddress/field ios           # or: android (see note below)
```

1. **Sign in** as `verifier`. The app uses the OIDC code flow with PKCE (no secret on the device); tokens are kept in the keystore.
2. Go offline: stop the API with `docker compose -f infra/compose/docker-compose.yml --env-file .env stop backend` (or switch the device to airplane mode).
3. Choose **Building**, add a note, **Take photo** and **Save capture**. The capture appears as *Waiting to upload* (it is stored in SQLite with its own idempotency key).
4. Go online again: `docker compose -f infra/compose/docker-compose.yml --env-file .env start backend`. The queue syncs automatically when connectivity returns, or press **Sync now**. The capture changes to *Uploaded*.
5. Check that the photo is in Record Store and the capture became one change request:

   ```sh
   regsql "select id, source, photo_object_key from register.change_request where source = 'field' order by created_at desc limit 3"
   ```

6. Press **Sync now** again or retry the same upload: the idempotency key makes the server return the existing change request, and no duplicate is created. The same guarantee is covered by automated tests (`RegisterApiIT.fieldCaptureStoresThePhotoAndRetriesDoNotDuplicate` and the field app's queue tests).

On the Android emulator, keep `localhost` and forward the ports: `adb reverse tcp:3000 tcp:3000`, and the same for 3002, 8080 and 9000. On a physical phone, `localhost` is not the computer: set `OIDC_PUBLIC_URL`, `PORTAL_PUBLIC_URL` and `TILES_PUBLIC_URL` in the root `.env` and the URLs in `apps/field/.env` to your computer's LAN IP, then `make down && make up`, so the token issuer matches what the backend accepts and the map style points the phone at reachable hosts.

## Everyday commands

| Command | What it does |
|---|---|
| `make up` / `make down` | start / stop the local environment |
| `make basemap` | download and verify the self-hosted basemap (`BASEMAP_AREA=uganda` or `demo`) |
| `make e2e` | Playwright map tests of portal and console against the running stack |
| `make test` | backend `./mvnw verify` (unit, Testcontainers, Modulith, ArchUnit) and all JavaScript tests |
| `make lint` | OpenAPI lint, ESLint and `tsc --noEmit` for every package |
| `make generate` | regenerate the TypeScript API client after changing the contract |
| `make check-generated` | fail if the committed client is out of date |
| `make db-reset` | recreate the register database with fixtures |

CI ([.github/workflows](.github/workflows)) runs the same checks on every pull request, plus CycloneDX SBOMs, Trivy (filesystem and images) and CodeQL.

## Documentation

- [CLAUDE.md](CLAUDE.md): stack, layout, conventions and commands at a glance
- [docs/adr](docs/adr): architecture decision records
- [docs/plan/bootstrap.md](docs/plan/bootstrap.md): bootstrap plan, pinned versions and deviations
- [docs/plan/session-2.md](docs/plan/session-2.md): session 2 plan (maps, search, approvals, photo integrity, MFA, Android)
- [CONTRIBUTING.md](CONTRIBUTING.md), [SECURITY.md](SECURITY.md), [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md)

## License

Apache License 2.0 (provisional, pending a final decision). See [LICENSE](LICENSE).
