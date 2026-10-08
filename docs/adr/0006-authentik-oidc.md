---
status: accepted
date: 2026-10-08
---

# 0006 Authentik for OIDC

## Context and Problem Statement

Custodian staff, field verifiers and administrators need accounts, groups and single sign-on across the console, the field app and the API. The identity provider must be self-hostable and open source so the government can run it.

## Decision Drivers

- Open source and self-hostable; no dependency on a foreign SaaS.
- Standard OIDC so the provider can be replaced later.
- Declarative, reproducible configuration for local and test environments.

## Considered Options

- Authentik
- Keycloak
- A cloud identity provider

## Decision Outcome

Chosen option: **Authentik**, run with its official Compose setup.

- The backend is a Spring Security OAuth2 resource server that validates JWTs against Authentik's JWKS.
- The console and portal use the OIDC authorization code flow; the field app uses the code flow with PKCE (public client).
- Configuration is declarative via Authentik blueprints: OIDC applications for console, portal and field app, the API audience, the groups `custodian-editor`, `custodian-approver`, `field-verifier` and `steward-admin`, and one synthetic test user per group.
- Each user's custodian is an Authentik user attribute emitted as the `custodian` claim; the backend derives the jurisdiction from it.

### Consequences

- Good: everything the clients and backend rely on is plain OIDC/OAuth2.
- Good: blueprints make the identity setup reproducible from a clean clone.
- Bad: Authentik's issuer URL depends on the host name used to reach it; local development must keep the public URL consistent (see README).
- Deviation from the official Compose file: the worker does not mount the Docker socket, because outposts are not used and the mount would grant root-equivalent host access.
