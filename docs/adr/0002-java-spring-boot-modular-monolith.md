---
status: accepted
date: 2026-10-08
---

# 0002 Java and Spring Boot modular monolith

## Context and Problem Statement

The backend has several distinct concerns (register, gazetteer, workflow, field capture, resolution, audit, ingest) that must evolve independently, but the system will be operated by a small public-sector team.

## Decision Drivers

- Low operational burden: few deployable units.
- Clear internal boundaries that can be verified automatically.
- A mainstream, long-term supported platform with a large local and international talent pool.

## Considered Options

- Java + Spring Boot modular monolith with Spring Modulith
- Microservices
- A different language or framework (Node.js, Go, Python)

## Decision Outcome

Chosen option: **Java (latest LTS supported by Spring Boot, currently 25) with Spring Boot, Spring MVC on virtual threads and Spring Modulith**, built with the Maven wrapper.

Modules (`register`, `gazetteer`, `workflow`, `field`, `resolve`, `audit`, `ingest`, `shared`) communicate only through their public APIs or Spring application events. A Spring Modulith verification test and ArchUnit rules fail the build when a module reaches into another module's internals.

### Consequences

- Good: one deployable, one database transaction boundary, simple operations.
- Good: module boundaries are checked on every build; a module can be extracted later if ever needed.
- Bad: modules share one runtime; a resource leak in one affects all.
- Microservices are explicitly out of scope.
