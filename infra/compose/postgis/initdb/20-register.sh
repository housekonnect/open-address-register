#!/bin/sh
# Creates the register database, its roles and extensions. Runs once, on an empty data directory,
# as the PostgreSQL superuser. Used by docker-compose, by the backend's Testcontainers tests and by
# jOOQ code generation, so all three start from an identical database.
#
# Roles:
#   register_owner  owns the schema; used only by Flyway migrations
#   register_app    runtime role of the backend; not an owner, so row-level security applies
#   martin_reader   used by Martin; can read only the views in the `tiles` schema
set -eu

: "${REGISTER_OWNER_PASSWORD:?REGISTER_OWNER_PASSWORD is required}"
: "${REGISTER_APP_PASSWORD:?REGISTER_APP_PASSWORD is required}"
: "${MARTIN_READER_PASSWORD:?MARTIN_READER_PASSWORD is required}"

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname postgres \
  -v owner_pw="$REGISTER_OWNER_PASSWORD" \
  -v app_pw="$REGISTER_APP_PASSWORD" \
  -v martin_pw="$MARTIN_READER_PASSWORD" <<'SQL'
CREATE ROLE register_owner LOGIN PASSWORD :'owner_pw';
CREATE ROLE register_app LOGIN PASSWORD :'app_pw';
CREATE ROLE martin_reader LOGIN PASSWORD :'martin_pw';
CREATE DATABASE register OWNER register_owner;
SQL

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname register <<'SQL'
-- PostGIS is not a trusted extension, so the superuser installs all extensions here.
CREATE EXTENSION IF NOT EXISTS postgis;
CREATE EXTENSION IF NOT EXISTS pg_trgm;
CREATE EXTENSION IF NOT EXISTS unaccent;
CREATE EXTENSION IF NOT EXISTS pgcrypto;

REVOKE ALL ON DATABASE register FROM PUBLIC;
GRANT CONNECT ON DATABASE register TO register_app, martin_reader;
REVOKE CREATE ON SCHEMA public FROM PUBLIC;

ALTER ROLE register_owner IN DATABASE register SET search_path = register, public;
ALTER ROLE register_app IN DATABASE register SET search_path = register, public;
ALTER ROLE martin_reader IN DATABASE register SET search_path = tiles, public;
SQL
