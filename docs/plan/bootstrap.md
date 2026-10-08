# Bootstrap plan: National Address Register (UGAddress)

Status: **implemented 2026-10-08** on branch `bootstrap` (see "Outcome" at the end).

Decisions taken at approval:
- Use the official `postgis/postgis:18-3.6` image (amd64 only). On Apple Silicon, Compose and Testcontainers run it under emulation with `platform: linux/amd64`.
- Push the `bootstrap` branch and open a pull request so GitHub Actions runs CI.

This session builds the skeleton only: every app starts, the apps talk to each other, one end-to-end slice works, and CI is green. Anything not named in the bootstrap brief is out of scope.

## 1. Versions

Rule: use the **latest stable release** of every dependency. Where the latest release is incompatible with a sibling dependency, use the newest compatible release and list it under "Exceptions". Every version below was checked on 2026-10-08 against Maven Central, the npm registry, Docker Hub / GHCR or the project's own release page.

### Runtime and toolchain

| Item | Version | Notes |
|---|---|---|
| Java | **25 LTS** (Temurin 25.0.4+7) | Latest LTS; Spring Boot 4.1.1 supports Java 17–26 and 26 is not LTS. The same build goes in `pom.xml` (`maven.compiler.release=25`), `.sdkmanrc` (`java=25.0.4-tem`) and Docker (`eclipse-temurin:25.0.4_7-jdk` / `-jre`) |
| Maven (wrapper) | 3.10.0 | Maven 4 is still RC |
| Node | 24 LTS (`node:24.21.0` images, `engines >=24`) | Node 26 is "current", not LTS |
| pnpm | 11.20.0 | Pinned via `packageManager` |

### Backend (Maven Central)

| Artifact | Version |
|---|---|
| Spring Boot (parent BOM) | 4.1.1 |
| Spring Modulith BOM | 2.1.1 |
| jOOQ + jooq-codegen-maven | 3.21.9 (Boot manages 3.21.7; overridden) |
| Flyway core + database-postgresql | 13.10.0 (Boot manages 12.4.0; see Exceptions) |
| Testcontainers BOM | 2.0.5 |
| PostgreSQL JDBC | 42.7.14 |
| JTS core | 1.20.0 |
| db-scheduler-spring-boot-starter | 16.12.0 |
| AWS SDK v2 BOM | 2.55.12 |
| openapi-generator-maven-plugin | 7.26.0 |
| jackson-databind-nullable | 0.2.12 (managed, not needed: `openApiNullable=false`) |
| ArchUnit (junit5) | 1.5.1 |
| JSpecify | 1.0.1 |
| JUnit BOM | 6.1.3 |
| AssertJ | 3.27.7 |
| CycloneDX Maven plugin | 2.9.3 |
| Micrometer / OpenTelemetry | Boot-managed (1.17.1 / 1.62.0) |
| Jackson 3 (`tools.jackson:jackson-bom`) | 3.2.3 (Boot manages 3.1.5; security override) |
| Tomcat embed | 11.0.26 (Boot manages 11.0.24; security override) |
| db-scheduler | `db-scheduler-spring-boot-4-starter` 16.12.0 (the Boot 4 variant) |
| Plugins pinned in `pluginManagement` | compiler 3.16.0, surefire 3.6.0, failsafe 3.6.0, jar 3.5.1, resources 3.5.0, clean 3.5.0, install 3.2.0, deploy 3.2.0, site 3.22.0, enforcer 3.6.3, wrapper 3.3.4, javadoc 3.12.0, dependency 3.11.0, build-helper 3.6.2, exec 3.6.4, spring-boot 4.1.1, openapi-generator 7.26.0, cyclonedx 2.9.3 |

### Web and field (npm)

| Package | Version |
|---|---|
| next / eslint-config-next | 16.4.0 |
| react / react-dom (web) | 19.3.0 |
| tailwindcss / @tailwindcss/postcss | 4.3.3 |
| shadcn (CLI) | 4.21.4 |
| maplibre-gl | 6.13.0 |
| qrcode.react | 4.2.0 |
| next-intl | 4.14.9 |
| openid-client (console OIDC code flow) | 6.8.8 |
| typescript | **6.0.3** (see Exceptions) |
| eslint / typescript-eslint | 10.12.0 / 8.71.1 |
| prettier | 3.9.9 |
| vitest / jsdom / @testing-library/react | 5.0.3 / 30.1.2 / 16.3.3 |
| @openapitools/openapi-generator-cli | 2.41.0 (runs generator 7.26.0, matching Maven) |
| @redocly/cli | 2.60.0 |
| npm SBOM | `pnpm sbom --sbom-format cyclonedx` (pnpm 11.20.0); see Exceptions |
| radix-ui / class-variance-authority / clsx / tailwind-merge / tw-animate-css | 1.7.0 / 0.7.1 / 2.1.1 / 3.7.0 / 1.4.0 |
| lucide-react | 1.53.0 |
| jose / undici (console session + OIDC fetch) | 6.2.12 / 8.11.2 |
| expo-location, expo-dev-client, expo-status-bar, react-native-safe-area-context | 57.0.20, 57.0.19, 57.0.1, 5.7.0 (SDK 57) |
| eslint-config-expo | 57.0.2 |
| jest / @types/jest (field app) | 29.7.0 / 29.5.14 (see Exceptions) |
| shell-quote (transitive, pnpm override) | 1.12.0 (security) |
| expo | 57.0.27 (latest stable SDK) |
| react-native / react (field) | 0.86.3 / 19.2.3 (pinned by Expo SDK 57) |
| expo-sqlite, expo-auth-session, expo-image-picker, expo-network, expo-file-system, expo-crypto, expo-secure-store, expo-web-browser, jest-expo | SDK 57 versions (`~57.0.x`, as listed in Expo 57's `bundledNativeModules.json`) |
| @maplibre/maplibre-react-native | 11.5.0 |

### Container images

| Service | Image |
|---|---|
| postgis | `postgis/postgis:18-3.6` (PostgreSQL 18 provides native `uuidv7()`) |
| authentik server + worker | `ghcr.io/goauthentik/server:2026.8.3` |
| authentik's own database | `postgres:16-alpine`, as in Authentik's official Compose file |
| record-store | `ghcr.io/openelementslabs/record-store:0.2.1` (published multi-arch image, so no source build and no Garage fallback is needed) |
| martin | `ghcr.io/maplibre/martin:1.16.1` (2.0 is still beta) |
| backend | built from `eclipse-temurin:25.0.4_7-jdk` and run on `eclipse-temurin:25.0.4_7-jre` |
| console / portal | built and run on `node:24.21.0` |

### Exceptions to "latest"

1. **TypeScript 6.0.3, not 7.0.2.** typescript-eslint 8.71.1 supports only `typescript >=4.8.4 <6.1.0`. TS 7 is the native Go compiler and has no compatible linting API yet. Renovate will propose TS 7 once typescript-eslint supports it.
2. **React Native 0.86.3 and React 19.2.3 in the field app.** Expo SDK 57 pins these. RN 0.87.1 and React 19.3.0 exist, but only Expo SDK 58 supports them, and SDK 58 is not released.
3. **Flyway 13.10.0 overrides Boot's 12.4.0** (a major-version jump). If Boot 4.1.1's Flyway auto-configuration fails with 13.x, I'll fall back to 12.4.0 and record it.
4. **Jest 29.7.0 in the field app, not 30.5.2.** jest-expo 57 is built on Jest 29 (`babel-jest ^29`, `jest-environment-jsdom ^29`).
5. **npm SBOM via `pnpm sbom`, not `@cyclonedx/cyclonedx-npm`.** cyclonedx-npm reads npm's own dependency tree and produced an empty SBOM (0 components) for this pnpm workspace; `pnpm sbom --sbom-format cyclonedx` produces CycloneDX 1.7 SBOMs with all components.
6. **Security overrides of Spring Boot-managed versions:** Tomcat 11.0.26 and Jackson 3.2.3 (Trivy: CVE-2026-65182 and others in Tomcat 11.0.24; several CVEs in Jackson 3.1.5). The full test suite passes with them.
7. **Temurin 25.0.4+7, not the 25.0.4.1 respin.** SDKMAN has only `25.0.4-tem`, and the brief requires the identical build in Maven, SDKMAN and Docker.

## 2. Architecture decisions within the brief

- **Database roles.**
  - `register_owner` owns the schema and runs Flyway.
  - `register_app` is the non-owner role the backend uses at runtime, so row-level security applies.
  - `martin_reader` can read only the `tiles` schema views.
- **Row-level security.**
  - Writable tables allow SELECT for all; the API layer enforces read-side redaction.
  - INSERT, UPDATE and DELETE require `admin_unit_id = ANY(string_to_array(current_setting('app.jurisdictions', true), ',')::uuid[])`.
  - The backend sets `app.jurisdictions` with `set_config(..., true)` at the start of each write transaction. The value comes from the custodian linked to the authenticated user.
- **History.** Each editable table has a `<table>_history` twin filled by an `AFTER INSERT/UPDATE/DELETE` trigger. It records `sys_period`, the operation and the actor. `valid_from` / `valid_to` carry business validity separately.
- **Audit chain.**
  - Writes go through `audit.append(...)`, a `SECURITY DEFINER` function.
  - It takes a transaction-level advisory lock, reads the previous hash, and stores `digest(prev_hash || canonical_content, 'sha256')` via pgcrypto.
  - UPDATE and DELETE are revoked from everyone, and a trigger also rejects them.
- **Four-eyes rule.** It is enforced in the `workflow` service and by a database CHECK (`approved_by IS DISTINCT FROM proposed_by`). Approval endpoints are not in this slice's contract; the rule is covered by service and database tests.
- **Idempotency.** An extra table, `idempotency_key`, stores `(key, principal, request_hash)` and the stored response. A replay with the same body returns the original response; a reused key with a different body returns 422 as a problem detail. db-scheduler also needs its own `scheduled_tasks` table. Both tables are additions to the brief's list.
- **Public redaction.**
  - Response schemas mark residential entrance coordinates as returned only with the `register:partner` scope.
  - The `resolve` module strips them for anonymous and non-partner callers.
  - Martin's views publish streets, building footprints and non-residential entrances only.
- **Field captures.**
  - `POST /v1/field/captures` is `multipart/form-data`: JSON metadata plus a photo, with an `Idempotency-Key` header.
  - The backend streams the photo to Record Store through AWS SDK v2. The object key is derived from the idempotency key, so retries overwrite the same object and do not create a duplicate.
  - The backend then creates a change request and an audit event.
  - Resumable (chunked) uploads are deferred; they are noted in the contract as future work.
- **Identity.**
  - Authentik is bootstrapped with blueprints mounted into the container. Blueprints read secrets and test-user passwords from `.env` using `!Env`.
  - Each user's custodian is an Authentik user attribute, emitted as a `custodian` claim.
  - The worker container does not mount `/var/run/docker.sock`. Authentik's official file mounts it only for outpost management, which we don't use, and leaving it out avoids giving a container root access to the host.
- **Console authentication.** `openid-client` runs the server-side code flow in Next.js route handlers, with an encrypted httpOnly session cookie. The portal is public and uses no login for the slice. It gets its own OIDC application, as the brief requires.
- **Field app.** MapLibre React Native and expo-sqlite need native code, so the app runs as an Expo development build, not in Expo Go. A real device or emulator reaches the host's Authentik and API through a configurable base URL.

## 3. Steps (one commit each, on branch `bootstrap`)

1. **Plan.** This file, committed after approval.
2. **Foundations.**
   - Conventions and docs: `CLAUDE.md`, README skeleton, LICENSE (Apache-2.0, provisional), SECURITY, CONTRIBUTING, CODE_OF_CONDUCT, `.editorconfig`, `.gitignore`, `.sdkmanrc`.
   - Tooling: pnpm workspace, Maven parent and wrapper, `renovate.json`, `.env.example`.
   - `make env` generates a local `.env` with random secrets; the file is gitignored.
   - Makefile targets: `up`, `down`, `build`, `test`, `lint`, `generate`, `db-reset`, `env`.
   - ADRs 0001–0009 in MADR format.
3. **National ID libraries.**
   - Java (`module-info`, no Spring) and TypeScript ports of the same API: generate with an injectable random source, validate, format, and parse.
   - Both read the same `contracts/test-vectors/national-id.json`.
   - Parameterised tests over 1,000 random IDs reject every single-digit substitution and every adjacent transposition of distinct digits.
   - Demonstration IDs render with the `DEMO` marker.
4. **Contract and generation.**
   - `register.v1.yaml` in OpenAPI 3.1: all 9 operations, RFC 9457 problems, cursor pagination, ETags, `Idempotency-Key`, and partner-scoped fields.
   - Redocly lint.
   - Spring interfaces are generated at build time. `packages/api-client` is generated and committed.
   - `make generate` plus a git-diff drift check.
5. **Database.** Flyway V1 with extensions, the 12 required tables plus the idempotency and scheduler tables, history triggers, RLS, constraints, the audit chain, roles and tile views. jOOQ code is generated from the migrations against a PostGIS Testcontainer. Synthetic fixtures cover about 50 buildings on a few invented streets, with demonstration IDs.
6. **Backend.**
   - Modules: `register`, `gazetteer`, `workflow`, `field`, `resolve`, `audit`, `ingest` (stubs), `shared`.
   - Security: resource server, scopes, jurisdiction context, problem details, virtual threads, db-scheduler, S3 client, Micrometer/OTel.
   - Implemented endpoints: resolve, objects, history, change requests and captures. All others return 501.
   - Tests: Modulith verification, ArchUnit rules, and Testcontainers integration tests covering RLS, history, concurrent audit appends, idempotent replay and redaction.
7. **Local environment.**
   - Compose with postgis, authentik (server, worker, own database), record-store with bucket bootstrap, martin, backend, console and portal.
   - Healthchecks and `depends_on` conditions.
   - Authentik blueprints: three OIDC applications, the API audience, four groups, and four test users.
8. **Web.**
   - `packages/ui`: shadcn/ui components and semantic tokens.
   - next-intl message catalogues, English first.
   - Portal: search, address page, MapLibre map and QR code.
   - Console: Authentik login, streets map, correction form that creates a change request.
   - Vitest tests and standalone production builds for both apps.
9. **Field app.** Expo app with PKCE login, an expo-sqlite offline queue (point plus photo), connectivity-triggered sync, and idempotent retry. Jest unit tests for the queue cover offline, sync, retry and no-duplicate behaviour.
10. **CI and acceptance.**
    - Workflows: backend `./mvnw verify` plus SBOM; contracts lint and drift check; web lint, types, tests and build; field lint, types and tests; Trivy filesystem and image scans; CodeQL for Java and JS/TS; npm SBOM.
    - Then a clean-clone `make up`, a manual run of the five-step slice, and final README steps.

## 4. Verification

- `make test` passes locally, and every CI job is run locally where possible.
- CI being green is claimed only after GitHub Actions has actually run on a pushed branch.
- The Java version is checked to be identical in `pom.xml`, `.sdkmanrc` and both Dockerfiles.
- No personal data: the schema review and fixtures contain no names, phone numbers, emails or national ID numbers of residents.
- The final report lists every pinned version, every deviation, and anything not done.

## 5. Known deviations from the brief

- Two extra tables: `idempotency_key` and db-scheduler's `scheduled_tasks`.
- No Docker socket mount on the Authentik worker.
- Resumable photo uploads are deferred; the slice sends one multipart upload per capture.
- jOOQ code generation uses a small single-file program (`backend/register-app/codegen/JooqCodegen.java`) run by exec-maven-plugin, not the Testcontainers jOOQ Maven plugin (0.0.4, last released April 2024, built for Testcontainers 1.x).
- A field capture is stored as a change request; the capture id equals the change request id (no separate capture table yet).
- The API audience `ugaddress-api` exists as its own Authentik provider (client credentials, partner scope). Authentik issues `aud` = client id, so the backend accepts the console, field and API client ids as audiences and one issuer per application.
- Map tiles are the register's own Martin layers only; there is no external base map.
- The version exceptions in section 1.

## 6. Outcome

- **Backend:** `./mvnw verify` passes: 2,047 national-ID tests, Modulith verification and 14 ArchUnit rules, and 19 Testcontainers integration tests against real PostGIS and Record Store (row-level security, history, concurrent audit chain, constraints, resolve, ETags, 501s, idempotent change requests, field captures with photos, four-eyes).
- **JavaScript:** lint, `tsc --noEmit` and tests pass for every package (2,048 national-ID tests in TypeScript, API client, UI, portal, console, field queue); portal and console build as standalone images.
- **Slice, verified by hand against the running Compose stack:** portal lookup and address page; console login through Authentik and correction submission with an audit event (retry returns the same change request); field captures with a real PKCE token, photo stored in Record Store, retry without duplicate; partner token sees residential entrance coordinates, anonymous callers do not. The field app's native iOS development build compiles and starts on the simulator; the interactive on-device steps (tapping through login and capture) were not automated.
- **Findings fixed on the way:** Authentik 2026.8 needs explicit `grant_types` per provider; Record Store 0.2.1 rejects the AWS SDK's flexible checksums and aws-chunked uploads (client configured accordingly, ADR 0007); expo-auth-session discovery needs the issuer without its trailing slash, and its token requests strip the trailing slash Authentik's token endpoint needs (the field app exchanges and refreshes tokens with its own small client); Trivy findings in Tomcat, Jackson and shell-quote.
