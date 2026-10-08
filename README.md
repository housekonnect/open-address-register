# National Address Register (UGAddress)

An independent, open-source address register for Uganda, designed so the government can adopt and run it.

**One register, many custodians.** Local governments, KCCA and MoICT maintain streets, house numbers and postcodes inside one PostgreSQL/PostGIS register; everyone else reads it through one API.

> Status: skeleton. The repository proves the architecture end to end with synthetic data. It is not yet a production system.

## Getting started

Requirements: Docker, a JDK 25 (see `.sdkmanrc`), Node 24+ and pnpm.

```sh
make up
```

The full walkthrough is added when the end-to-end slice is complete.

## Documentation

- [CLAUDE.md](CLAUDE.md): stack, layout, conventions and commands at a glance
- [docs/adr](docs/adr): architecture decision records
- [docs/plan/bootstrap.md](docs/plan/bootstrap.md): bootstrap plan and pinned versions

## License

Apache License 2.0 (provisional, pending a final decision). See [LICENSE](LICENSE).
