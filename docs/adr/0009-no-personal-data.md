---
status: accepted
date: 2026-10-08
---

# 0009 No personal data in the register

## Context and Problem Statement

An address register describes places. It is tempting to attach occupants, owners or contacts, but doing so would turn public infrastructure into a population register with surveillance, security and data-protection risks (Uganda Data Protection and Privacy Act, 2019).

## Decision Drivers

- The register is publicly readable.
- Minimise harm if data leaks.
- Keep the register's legal basis simple.

## Considered Options

- No personal data, ever
- Personal data in restricted columns
- Personal data in a separate linked store

## Decision Outcome

Chosen option: **the register contains no personal data. This rule has no exceptions.**

- The schema has no columns for residents' names, phone numbers, emails or National ID numbers, and fixtures contain only invented places.
- Custodian staff and verifiers are referenced only by the opaque subject identifier from Authentik in change requests and audit events; their profiles stay in the identity provider.
- Coordinates of **residential entrances** are not personal data but can locate a home precisely; public API responses and public map tiles omit them, and only the `register:partner` scope receives them.
- Logs never contain personal data or residential entrance coordinates.

### Consequences

- Good: the register can be public without exposing people.
- Bad: services that need to reach a person must hold that link themselves, under their own legal basis.
