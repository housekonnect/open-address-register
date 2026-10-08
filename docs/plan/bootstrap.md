# National Address Register bootstrap plan

Status: planning; implementation has not started.

## Scope

Build the requested modular monolith and three clients, with the five-step synthetic end-to-end slice. Implement only resolve, object detail, history, change requests and field captures; other contracted operations return RFC 9457 status 501. No resident personal data, spatial ORM, Lombok, Kubernetes, microservices, MinIO, blockchain or routing engine.

## Ordered implementation and commits

1. **Plan and version evidence.** Inspect the empty repository and local tools. Verify stable releases against Maven Central, npm, Docker Hub and official compatibility documentation. Record every selected version and the Spring Boot-supported Java LTS. Commit this plan before implementation.
2. **Repository foundations.** Add the root conventions, Apache-2.0 provisional license, contribution/security documents, nine MADR decisions, pnpm workspace, Maven parent/wrapper, environment example and Makefile. Commit foundations.
3. **Shared national ID libraries.** Implement the Damm API in pure Java and strict TypeScript, shared vectors, injectable randomness, demonstration display, and exhaustive substitutions/transpositions for 1,000 generated IDs per language. Commit after tests pass.
4. **Contract and reproducible generation.** Define all nine OpenAPI 3.1 operations, scope-sensitive response schemas, cursors, ETags, idempotency and problem details. Generate Spring interfaces and TypeScript client; establish lint and regeneration drift checks. Commit generated artifacts and tooling.
5. **Database and backend.** Add Flyway SQL, histories, jurisdiction RLS, active-address uniqueness, aliases, serialized hash-chain audit writes, synthetic fixtures and jOOQ generation through PostGIS Testcontainers. Implement public module APIs, security, transactional jurisdiction context, implemented endpoints, idempotency, S3 capture uploads and PostgreSQL job scheduling. Verify Modulith, ArchUnit and integration tests, then commit.
6. **Local services and identity.** Verify Record Store's published image or source build; use Garage only if source build fails, documenting evidence in ADR 0007. Add official Authentik Compose dependencies and bootstrap clients/groups/test accounts, Martin tiles and containerized application builds. Verify service readiness and commit.
7. **Web clients.** Add shared shadcn/ui primitives and semantic tokens, translation-ready strings, portal resolution/address/map/QR, and authenticated console street map/correction submission. Run lint, types, tests and both production builds; commit.
8. **Field client.** Add Expo OIDC PKCE, SQLite durable capture/photo queue, retry-safe API sync and MapLibre native map. Test offline transitions, retry and duplicate prevention; commit.
9. **CI and acceptance.** Add PR checks, Maven/npm CycloneDX, Trivy filesystem/image scans, CodeQL and Renovate. Run generation drift, all local quality gates and clean Compose startup. Exercise and document the complete five-step slice; commit acceptance documentation.

## Verification and reporting

- Use actual PostGIS for integration tests, including RLS with a non-owner application role, history capture, concurrent audit append and idempotent writes.
- Validate the Java version is identical in Maven, SDKMAN and Docker stages.
- Verify public responses omit residential entrance coordinates and partner scope permits access.
- Keep application secrets out of tracked files and container layers; generate local development credentials into ignored files and document test account access.
- Report every pinned version, deviation and incomplete check. Do not describe CI as green without executing its checks; remote GitHub execution requires a configured remote.

## Initial environment

The repository has no files or commits. Docker is available and running on ARM64. Node and pnpm are installed. The installed JDK is Java 21; the selected supported LTS may require an additional JDK. No application implementation has been written.
