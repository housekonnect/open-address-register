---
status: accepted
date: 2026-10-08
---

# 0008 Our own field app (React Native + Expo)

## Context and Problem Statement

Field verifiers capture building points, entrances and photos, often without connectivity. Captures must reach the register as change requests, with no loss and no duplicates.

## Decision Drivers

- Reliable offline capture and resumable sync.
- Exactly-once effect on the server despite retries.
- Same identity, API client and language (TypeScript) as the web apps.
- Runs on low-cost Android devices common in Uganda, and on iOS.

## Considered Options

- Our own React Native + Expo app
- A generic data-collection tool (ODK, KoboToolbox) with a custom importer
- A progressive web app

## Decision Outcome

Chosen option: **our own React Native + Expo app** (TypeScript).

- Captures (point + photo) are written to an expo-sqlite queue first and synced when connectivity returns.
- Each capture gets its `Idempotency-Key` when it is created, so retries never create duplicates.
- Login uses OIDC with PKCE against Authentik.
- Maps use MapLibre React Native with the same Martin tiles as the web apps.
- Because MapLibre React Native and SQLite need native code, the app runs as an Expo development build rather than in Expo Go.

### Consequences

- Good: the capture → change-request flow is fully under our control and shares the generated API types.
- Bad: we maintain a mobile app, including store releases.
- Neutral: importers for ODK/Kobo data can still be added to the `ingest` module later.
