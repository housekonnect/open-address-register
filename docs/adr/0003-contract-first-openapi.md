---
status: accepted
date: 2026-10-08
---

# 0003 Contract-first OpenAPI

## Context and Problem Statement

The register's API is its product: ministries, utilities and developers integrate against it, and three of our own clients consume it. How do we keep server and clients consistent?

## Decision Drivers

- The API must be reviewable before it is implemented.
- Server and clients must not drift apart.
- Errors must be machine-readable and consistent.

## Considered Options

- Contract-first: hand-written OpenAPI 3.1 file, code generated from it
- Code-first: generate OpenAPI from annotated controllers
- No formal contract

## Decision Outcome

Chosen option: **contract-first**. `contracts/register.v1.yaml` (OpenAPI 3.1) is the source of truth. OpenAPI Generator produces the Spring server interfaces at build time and the TypeScript client in `packages/api-client`, which is committed and never edited by hand.

Rules for every operation: RFC 9457 problem details for errors, cursor pagination for lists, ETags on resources, and an `Idempotency-Key` header on every POST. Operations that are contracted but not yet implemented return 501 as a problem detail.

### Consequences

- Good: API changes are reviewed as contract diffs; generated code guarantees type compatibility.
- Good: CI lints the contract (Redocly) and fails if the committed client is stale.
- Bad: generator quirks must be worked around in configuration, not by editing output.
