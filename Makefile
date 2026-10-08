# Single entry point for local development. Run `make help` for the list of targets.
#
# Requirements: Docker, a JDK matching .sdkmanrc, Node (>= 24) and pnpm.

SHELL   := /bin/sh
COMPOSE := docker compose --env-file .env -f infra/compose/docker-compose.yml
MVNW    := cd backend && ./mvnw -B

.DEFAULT_GOAL := help
.PHONY: help env install generate check-generated build test lint e2e basemap up down logs db-reset

help: ## Show this help
	@awk 'BEGIN {FS = ":.*## "} /^[a-z-]+:.*## / {printf "  %-18s %s\n", $$1, $$2}' $(MAKEFILE_LIST)

env: ## Create .env with random local secrets (never overwrites)
	@sh infra/scripts/gen-env.sh

install: ## Install JavaScript dependencies
	pnpm install --frozen-lockfile

generate: install ## Regenerate the TypeScript API client from contracts/
	pnpm generate

check-generated: generate ## Fail if generated code differs from what is committed
	@git diff --exit-code -- packages/api-client || (echo "Generated API client is out of date: run 'make generate' and commit." && exit 1)

build: install ## Build the backend jar and all web packages
	$(MVNW) package -DskipTests
	pnpm build

test: install ## Run all tests (backend with Testcontainers, web, field)
	$(MVNW) verify
	pnpm test

e2e: ## Run the Playwright end-to-end tests of portal and console against the running stack (make up)
	@set -a && . ./.env && set +a && pnpm --filter @ugaddress/portal --filter @ugaddress/console run e2e

lint: install ## Lint contracts, TypeScript and type-check everything
	pnpm contracts:lint
	pnpm lint
	pnpm typecheck

BASEMAP_AREA ?= uganda

basemap: ## Download and verify the self-hosted basemap (BASEMAP_AREA=uganda|demo) into a Docker volume
	@BASEMAP_AREA="$(BASEMAP_AREA)" sh infra/basemap/basemap.sh

up: env basemap ## Build and start the full local environment
	$(MVNW) package -DskipTests -q
	$(COMPOSE) up -d --build --wait
	@echo "Portal  http://localhost:3000"
	@echo "Console http://localhost:3001"
	@echo "API     http://localhost:8080/v1"
	@echo "Authentik http://localhost:9000"

down: ## Stop the local environment (keeps data)
	$(COMPOSE) down

logs: ## Follow logs of all services
	$(COMPOSE) logs -f

db-reset: env ## Drop the register database volume and start again with fresh fixtures
	$(COMPOSE) rm --stop --force postgis backend martin
	-docker volume rm ugaddress_postgis-data
	$(COMPOSE) up -d --build --wait
