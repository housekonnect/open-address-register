# CLAUDE.md: National Address Register (UGAddress)

An independent, open-source address register for Uganda, designed so the government can adopt and run it.
**One register, many custodians:** local governments, KCCA and MoICT maintain streets, house numbers and postcodes in one PostgreSQL/PostGIS register; everyone else reads it through one API.

Follow only the rules in this file, the ADRs in `docs/adr` and the plan in `docs/plan`. Do not import any organisation's internal templates, tooling or conventions.

## Never add

Kubernetes manifests · microservices · MinIO · Lombok · an ORM for spatial data (no JPA/Hibernate) · blockchain · a routing engine.

**No personal data in the register.** No names, phone numbers, emails or National ID numbers of residents, anywhere in the schema, fixtures or logs. No exceptions.

## Stack

| Area | Choice |
|---|---|
| Backend | Java 25 (LTS) · Spring Boot 4.1 · Spring MVC on virtual threads · Spring Modulith · Maven wrapper |
| Data | PostgreSQL 18 + PostGIS 3.6 · Flyway plain-SQL migrations · jOOQ generated from the migrations via Testcontainers · JTS |
| Jobs | db-scheduler (jobs stored in PostgreSQL); Spring Batch later for bulk imports |
| API | Contract-first OpenAPI 3.1 in `contracts/`; OpenAPI Generator makes the Spring interfaces and the TypeScript client; errors are RFC 9457 problem details |
| Identity | Authentik (OIDC). Backend = OAuth2 resource server; web = code flow; field = PKCE |
| Objects | Record Store (S3-compatible) via AWS SDK v2 only; all config from env, so Garage can stand in |
| Web | Next.js (App Router, standalone) · React · TypeScript strict · Tailwind · shadcn/ui · MapLibre GL JS · pnpm workspaces |
| Field | React Native + Expo (dev build) · expo-sqlite offline queue · MapLibre React Native |
| Tiles | Martin, serving vector tiles from PostGIS views in the `tiles` schema and the self-hosted Protomaps basemap (PMTiles from OpenStreetMap) |
| Observability | Micrometer + OpenTelemetry |
| Local | Docker Compose (`infra/compose`) |
| CI | GitHub Actions · Renovate · Trivy · CodeQL · CycloneDX SBOM (Maven + npm) |

Pinned versions and the reasons for every exception are in `docs/plan/bootstrap.md`. Check Maven Central, npm and Docker Hub before changing a version. Never guess one.
The Java version must be identical in `backend/pom.xml`, `.sdkmanrc` and the `eclipse-temurin` image tag.

## Layout

```
backend/            Maven multi-module (parent pom: dependencyManagement, pluginManagement, CycloneDX)
  national-id/      pure Java library, no Spring, module-info exports only the API package
  register-app/     Spring Boot app built from Spring Modulith modules
apps/console/       Next.js custodian console (Authentik login)
apps/portal/        Next.js public lookup + developer docs
apps/field/         Expo field capture app
packages/api-client generated TypeScript client: NEVER edit by hand, run `make generate`
packages/national-id TypeScript port of the ID library, same test vectors
packages/ui         shared shadcn/ui components and semantic tokens
contracts/          register.v1.yaml (source of truth) + test-vectors/
db/fixtures/        synthetic seed data only
infra/compose/      docker-compose.yml + service configs (Authentik blueprints, Martin, PostGIS init)
docs/adr, docs/plan
```

## Backend modules (`org.ugaddress.register.*`)

`register` (objects, addresses, aliases, lifecycle) · `gazetteer` (streets, versioned admin units, postcode areas) · `workflow` (change requests, state machine, four-eyes: proposer never approves) · `field` (assignments, captures, photo uploads) · `resolve` (resolve/search/reverse, read side) · `audit` (append-only hash-chained events) · `ingest` (seed imports; stubs) · `shared` (security, problem details, jurisdiction context).

Modules talk only through their public API (top-level package) or Spring application events. `internal` sub-packages are private. The Modulith verification test and ArchUnit rules enforce this.

## Database rules

- UUIDv7 keys via PostgreSQL 18 `uuidv7()`. Geometry in EPSG:4326; distances via `geography`.
- Every editable table has a `_history` twin written by triggers; business validity is in `valid_from` / `valid_to`.
- `audit_event` is append-only (UPDATE/DELETE revoked) and hash-chained with pgcrypto SHA-256. Every write path goes through the audit module.
- Row-level security: writes are allowed only where the row's admin unit is in `current_setting('app.jurisdictions')`, which the backend sets per transaction.
- Roles: `register_owner` (Flyway), `register_app` (runtime, subject to RLS), `martin_reader` (tile views only).

## API rules

Cursor pagination, ETags, an `Idempotency-Key` header on every POST, RFC 9457 errors. Public responses never contain residential entrance coordinates; they need the `register:partner` scope. Unimplemented operations return 501 as a problem detail.

## Conventions

**Java:** no Lombok, no `var`, no wildcard imports. Records for DTOs; `final` wherever possible; JSpecify `@NullMarked` packages. SLF4J parameterised logging. Javadoc on all public API. Tests use JUnit 5 + AssertJ with `// GIVEN` / `// WHEN` / `// THEN` comments. Class suffixes: `Service`, `Repository`, `Controller`, `DTO`, `Config`.

**Maven:** pin every plugin in the parent's `pluginManagement`; manage every version in the parent's `dependencyManagement`. Modules declare no versions.

**TypeScript:** strict mode. Never hand-edit `packages/api-client`. All UI strings go through the message catalogues (English first, Luganda later). Use shadcn/ui components instead of bare HTML equivalents; semantic colour tokens only (`bg-background`, `text-muted-foreground` …), never raw palette colours.

**Security:** no secrets in code or images (`make env` generates `.env`). Never log personal data or residential entrance coordinates.

## Commands

| Command | What it does |
|---|---|
| `make env` | create `.env` with random local secrets |
| `make up` / `make down` | build and start / stop the whole stack |
| `make build` | backend jar + web builds |
| `make test` | `./mvnw verify` (Testcontainers) + all JS tests |
| `make lint` | OpenAPI lint, ESLint, `tsc --noEmit` |
| `make generate` | regenerate `packages/api-client` from the contract |
| `make check-generated` | fail if generated code is stale |
| `make db-reset` | recreate the register database with fixtures |
| `make basemap` | download and verify the basemap into the `ugaddress-basemap` volume (`BASEMAP_AREA=uganda` or `demo`) |
| `make e2e` | Playwright map tests of portal and console against the running stack |

The backend needs JDK 25 (`sdk env` picks it up from `.sdkmanrc`) and a running Docker for jOOQ code generation and tests.

## Gotchas

- **Never overwrite the root `.env`.** Its secrets initialised the Docker volumes. Write per-app env files (e.g. `apps/field/.env`) with absolute paths.
- Generated code lives outside the module packages so Spring Modulith ignores it: OpenAPI interfaces in `org.ugaddress.api.v1` (Maven build), jOOQ in `org.ugaddress.db.generated` (`codegen/JooqCodegen.java`, skipped when migrations are unchanged).
- TypeScript stays on 6.0.x until typescript-eslint supports 7; the field app follows Expo SDK 57's pins (React 19.2, React Native 0.86, Jest 29). Check with `pnpm exec expo install --check` in `apps/field`.
- pnpm 11 blocks dependency install scripts; decide each one in `pnpm-workspace.yaml` → `allowBuilds` (currently all denied; their prebuilt binaries suffice).
- Record Store 0.2 needs AWS SDK checksums `WHEN_REQUIRED` and chunked encoding off (ADR 0007).
- Authentik issuers are per application and per host name. The backend accepts a list (`OIDC_ISSUERS`); the console reaches Authentik internally while keeping the public host name (`OIDC_INTERNAL_HOST`).
- Martin publishes only views in the `tiles` schema; add a view there (never a residential entrance) to publish a layer.
- Every map loads one style, `/map/style.json` on the portal (built by `packages/ui/src/lib/map-style.ts`). Fonts and sprites come from the `ugaddress-basemap` volume through the portal; no map request may leave the stack, and every map shows "© OpenStreetMap contributors".
- maplibre-gl 6 must get its worker URL from the bundler (`setWorkerUrl` in `register-map.tsx`); otherwise the worker 404s after bundling and no tile loads.
