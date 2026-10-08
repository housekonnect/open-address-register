---
status: accepted
date: 2026-10-08
---

# 0001 One register, many custodians

## Context and Problem Statement

Uganda's addressing data is created by many bodies: local governments name streets and number houses, KCCA administers Kampala, and MoICT owns the postcode framework. If each body keeps its own system, consumers (emergency services, utilities, logistics, banks) must integrate with each one, and the datasets drift apart.

How should address data be held and maintained so that it stays consistent while responsibility remains with the bodies that create it?

## Decision Drivers

- A single authoritative answer to "what is this address?"
- Accountability: every change is attributable to a responsible custodian.
- Custodians may only change data inside their own jurisdiction.
- The government must be able to adopt and run the system.

## Considered Options

- One shared register with jurisdiction-scoped write access for many custodians
- A federation of custodian-owned registers synchronised into an index
- A central register maintained only by a national body

## Decision Outcome

Chosen option: **one shared register with many custodians**. All authoritative data lives in one PostgreSQL/PostGIS database. Each custodian (local government, KCCA, MoICT) writes only within its jurisdiction, which the database enforces with row-level security. Everyone else reads through one public API.

### Consequences

- Good: one source of truth, one API, one audit trail.
- Good: jurisdiction rules are enforced in the database, not only in application code.
- Bad: the operator of the register becomes critical infrastructure and must be resourced accordingly.
- Neutral: custodians who want their own systems integrate through the change-request API.
