# Security policy

The National Address Register is meant to become public infrastructure. We take vulnerabilities seriously.

## Reporting a vulnerability

Please do **not** open a public issue. Use GitHub's private vulnerability reporting for this repository (Security → Report a vulnerability). Include steps to reproduce and the affected component (backend, console, portal, field app, infrastructure).

We aim to acknowledge reports within five working days.

## Scope notes

- The register must never contain personal data of residents (names, phone numbers, emails, National ID numbers). A finding that personal data can enter the register is in scope.
- Coordinates of residential entrances are restricted to partners. A path that exposes them publicly is in scope.
- The audit log is append-only and hash-chained. Any way to modify or delete audit events undetected is in scope.

## Supported versions

The project is pre-release. Only the `main` branch receives security fixes.
