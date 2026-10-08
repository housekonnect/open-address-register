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

## Compatibility spike (2026-10-08)

Input for the readiness gate. Nothing in the implementation changed: the stack stays on Record Store 0.2.1 with the client settings above.

**Which release.** 0.2.1 (2026-09-25) is still the newest Record Store release, and GHCR has no newer image: `latest` is 0.2.1. The 27 commits on `main` since then are dependency and documentation updates. The spike therefore tested 0.2.1.

**Method.** A throwaway `ghcr.io/openelementslabs/record-store:0.2.1` container on spare ports (never the stack's volume), driven by AWS SDK for Java v2 2.55.12 with the `url-connection-client`, exactly as the backend uses it. Every operation ran under four client configurations.

| Operation | Project settings (checksums `WHEN_REQUIRED`, chunked off) | SDK defaults (`WHEN_SUPPORTED`, chunked on) | Default checksums only (`WHEN_SUPPORTED`, chunked off) | Chunked only (`WHEN_REQUIRED`, chunked on) |
|---|---|---|---|---|
| PutObject, 1 KiB | OK | **501** "AWS streaming payloads (aws-chunked) … are not implemented" | OK | **501** (same) |
| GetObject, bytes compared | OK | n/a (nothing stored) | OK | n/a |
| PutObject with `checksumAlgorithm(SHA256)` | OK | **501** | OK | **501** |
| Multipart upload: 5 MiB + 1 KiB parts, completed and read back | OK, bytes identical | **501** | OK, bytes identical | **501** |

With the project settings, client-supplied checksums are verified:

| Request | Result |
|---|---|
| PutObject with a wrong `x-amz-checksum-sha256` | **400** "The Content-MD5 or checksum did not match the received data"; nothing stored |
| PutObject with a wrong `Content-MD5` | **400** (same); nothing stored |
| PutObject with a correct `x-amz-checksum-sha256` | stored; the response does not echo the checksum header |

**Findings.**

1. **Multipart uploads work** in 0.2.1. Resumable photo uploads can use them, as long as chunked encoding stays off.
2. **The SDK's default checksums work.** With chunked encoding off, `WHEN_SUPPORTED` request and response checksums succeed. This corrects the statement above: only `aws-chunked` streaming payloads (including trailing checksums) are unsupported. Of the two client settings, only `chunkedEncodingEnabled(false)` is needed for Record Store; `WHEN_REQUIRED` is harmless and stays.
3. **aws-chunked is not implemented** (501, with a clear message). Every client that writes to the store must send plain, non-chunked payloads. The backend does; any other S3 client added later must be configured the same way.
4. **Record Store verifies supplied checksums** (SHA-256 and MD5) and rejects mismatches without storing anything. A later option, not implemented: send the device-side SHA-256 as `x-amz-checksum-sha256`, so the store itself rejects a corrupted upload before the backend re-hashes it.

**Readiness gate.** For the register's use (single PUT, GET, multipart, integrity) Record Store 0.2.1 passes, on one condition: chunked encoding off. Re-run this spike before adopting any later release.

