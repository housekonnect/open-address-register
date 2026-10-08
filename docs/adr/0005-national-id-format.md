---
status: draft
date: 2026-10-08
---

# 0005 National ID format (draft)

The final identifier scheme is a government decision. This ADR records a working format so the system can be built and tested; it will be revised when the scheme is decided.

## Context and Problem Statement

Every addressable object (building, entrance, access point, landmark, facility) needs a stable, short, unambiguous identifier that people can read aloud, type on a phone, print on a plate and encode in a QR code. (This is the identifier of a *place*, not of a person.)

## Decision Drivers

- Easy to read, say and type; digits only works on every keypad and in every language.
- Detects the most common human errors: single-digit typos and adjacent transpositions.
- Carries no meaning (no location or sequence) so it never needs to change when boundaries change.

## Considered Options

- 10 random digits + 1 Damm check digit
- Luhn check digit
- Verhoeff check digit
- Grid-based codes (e.g. Plus Codes) as the primary ID

## Decision Outcome

Chosen option (draft): **10 random digits followed by 1 Damm check digit (11 digits)**, displayed in groups of 4-3-4, e.g. `4821 093 7618` (payload `4821093761`, Damm check digit `8`).

- Damm detects all single-digit errors and all adjacent transpositions with a simple table lookup; Luhn misses some transpositions (09↔90) and Verhoeff is more complex.
- Parsing accepts spaces and dashes.
- Demonstration IDs (synthetic data) are marked with `DEMO` in their display form and have the status `demonstration` in the database, so they can never be mistaken for real IDs.
- A pure Java library (`backend/national-id`) and a TypeScript port (`packages/national-id`) implement generate (with an injectable random source), validate, format and parse, and both are tested against the same vectors in `contracts/test-vectors/national-id.json`.

### Consequences

- Good: errors are caught before a lookup is made.
- Good: random IDs leak nothing about location or allocation order.
- Bad: IDs are not memorable or guessable from location; the register's resolve API is needed to interpret them.
- Open: allocation governance, reserved ranges and whether a location hint is wanted are for the government to decide.
