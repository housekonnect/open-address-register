---
status: accepted
date: 2026-10-08
---

# 0004 PostgreSQL/PostGIS as the single source of truth

## Context and Problem Statement

The register is spatial (buildings, entrances, streets, admin units), must keep full history, must enforce jurisdiction rules, and must be auditable. Where do these guarantees live, and how does the application access the data?

## Decision Drivers

- Integrity rules must hold regardless of which code path writes.
- Spatial queries must be first-class.
- SQL must be visible and reviewable, not hidden behind an ORM.

## Considered Options

- PostgreSQL + PostGIS with Flyway plain SQL and jOOQ
- PostgreSQL + PostGIS with JPA/Hibernate (Hibernate Spatial)
- A document or graph store

## Decision Outcome

Chosen option: **PostgreSQL 18 + PostGIS as the single source of truth, Flyway plain-SQL migrations, jOOQ for access, JTS for geometry in Java.**

- Extensions: postgis, pg_trgm, unaccent, pgcrypto.
- Primary keys are UUIDv7 via PostgreSQL 18's native `uuidv7()`.
- Geometry is stored in EPSG:4326; distances use `geography`.
- Each editable table has a `_history` twin filled by triggers; business validity is tracked separately in `valid_from` / `valid_to`.
- Row-level security restricts writes to the custodian's jurisdiction via `current_setting('app.jurisdictions')`.
- jOOQ code is generated from the migrations against a real PostGIS started by Testcontainers, so generated code always matches the schema.
- No ORM is used for spatial data.

### Consequences

- Good: constraints, history, RLS and the audit chain hold even for manual SQL.
- Good: type-safe SQL with full access to PostGIS functions.
- Bad: building the backend requires Docker (for code generation and tests).
- Bad: the official PostGIS image is amd64 only; Apple Silicon machines run it under emulation.
