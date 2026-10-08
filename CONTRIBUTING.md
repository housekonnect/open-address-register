# Contributing

Thank you for helping build the National Address Register. By participating you agree to the [Code of Conduct](CODE_OF_CONDUCT.md).

## Before you start

- Read [CLAUDE.md](CLAUDE.md): it is the short version of the project's stack, layout and conventions, for humans and coding agents alike.
- Architectural decisions live in [docs/adr](docs/adr). A change that contradicts an ADR needs a new ADR first.
- The register holds **no personal data**. See [ADR 0009](docs/adr/0009-no-personal-data.md).

## Workflow

1. Open an issue or discussion for anything larger than a small fix.
2. Branch from `main` and keep pull requests focused.
3. Change the API by editing `contracts/register.v1.yaml` first, then run `make generate` and commit the regenerated client.
4. Run `make lint test` before pushing. CI runs the same checks.
5. Use [Conventional Commits](https://www.conventionalcommits.org/) for commit messages (`feat:`, `fix:`, `docs:`, `chore:` …).

## Local setup

See the [README](README.md#getting-started).

## Licensing

Contributions are accepted under the Apache License 2.0 (the licence choice is provisional until the project's governance decides).
