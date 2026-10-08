---
status: accepted
date: 2026-10-08
---

# 0007 Record Store for object storage, accessed only through the S3 API

## Context and Problem Statement

Field captures include photos of buildings and entrances. They must be stored durably, with integrity guarantees, on infrastructure the government controls.

## Decision Drivers

- Open source, self-hosted.
- No lock-in: the application must work with any S3-compatible store.
- Integrity and retention controls suit evidence attached to change requests.

## Considered Options

- Record Store (github.com/OpenElementsLabs/record-store), Apache-2.0, S3-compatible
- Garage
- MinIO (excluded by project rules)
- Files on the database server

## Decision Outcome

Chosen option: **Record Store**, accessed **only through the S3 API via AWS SDK v2**.

- Endpoint, region, bucket and credentials come from environment variables (`S3_ENDPOINT`, `S3_REGION`, `S3_BUCKET`, `S3_ACCESS_KEY`, `S3_SECRET_KEY`), with path-style addressing, so Garage can stand in without code changes.
- Record Store publishes a multi-arch image (`ghcr.io/openelementslabs/record-store`, pinned to 0.2.1). A source build and the Garage fallback were therefore **not** needed.
- Record Store 0.2.1 implements a subset of S3. It answers `NotImplemented` to the AWS SDK's default flexible checksums and to `aws-chunked` streaming uploads, so the client sets checksum calculation and validation to `WHEN_REQUIRED` and disables chunked encoding. Both settings are harmless with other S3 stores. An integration test runs against the real Record Store image.
- Clients never talk to the object store directly; photos are uploaded through the register API, which stores them and records the object key on the change request.

### Consequences

- Good: storage is replaceable; only S3 semantics are assumed.
- Good: versioning and integrity features of Record Store are available for evidence.
- Bad: Record Store is young (0.x); upgrades must be tested. Garage remains the documented fallback.
