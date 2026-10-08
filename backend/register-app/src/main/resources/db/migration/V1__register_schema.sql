-- National Address Register: initial schema.
--
-- Runs as register_owner. Extensions are installed by the database init script (PostGIS is not a trusted
-- extension); the statements below only assert that they are present.
--
-- Rules (see CLAUDE.md and ADRs 0004, 0009):
--   * no personal data of residents, anywhere
--   * UUIDv7 primary keys (PostgreSQL 18 uuidv7())
--   * geometry in EPSG:4326, distances via geography
--   * every editable table has a <table>_history twin written by triggers; business validity in valid_from/valid_to
--   * row-level security: register_app may write a row only if its admin unit is in app.jurisdictions
--   * audit_event is append-only and hash-chained (SHA-256 via pgcrypto)

CREATE EXTENSION IF NOT EXISTS postgis;
CREATE EXTENSION IF NOT EXISTS pg_trgm;
CREATE EXTENSION IF NOT EXISTS unaccent;
CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE SCHEMA IF NOT EXISTS register;
CREATE SCHEMA IF NOT EXISTS tiles;

SET search_path = register, public;

-- ---------------------------------------------------------------------------------------------------------------
-- Types
-- ---------------------------------------------------------------------------------------------------------------

CREATE TYPE register.lifecycle AS ENUM ('proposed', 'active', 'retired');
CREATE TYPE register.object_kind AS ENUM ('building', 'entrance', 'access_point', 'landmark', 'facility');
CREATE TYPE register.national_id_status AS ENUM ('allocated', 'demonstration');
CREATE TYPE register.admin_level AS ENUM ('region', 'district', 'county', 'subcounty', 'parish', 'village', 'city', 'division');
CREATE TYPE register.custodian_kind AS ENUM ('local_government', 'city_authority', 'ministry');
CREATE TYPE register.change_kind AS ENUM ('correction', 'new_object', 'retirement');
CREATE TYPE register.change_state AS ENUM ('submitted', 'in_review', 'approved', 'rejected', 'applied', 'withdrawn');
CREATE TYPE register.change_source AS ENUM ('console', 'field');
CREATE TYPE register.verification_outcome AS ENUM ('confirmed', 'disputed', 'not_found');

-- ---------------------------------------------------------------------------------------------------------------
-- Helper functions
-- ---------------------------------------------------------------------------------------------------------------

-- Damm check over a string of digits; 0 means the last digit is a correct check digit (ADR 0005).
CREATE FUNCTION register.damm_interim(digits text) RETURNS integer
    LANGUAGE plpgsql IMMUTABLE STRICT PARALLEL SAFE AS
$$
DECLARE
    table_ CONSTANT integer[] := ARRAY[
        [0,3,1,7,5,9,8,6,4,2],[7,0,9,2,1,5,4,8,6,3],[4,2,0,6,8,7,1,3,5,9],[1,7,5,0,9,8,3,4,2,6],
        [6,1,2,3,0,4,5,9,7,8],[3,6,7,4,2,0,9,5,8,1],[5,8,6,9,7,2,0,1,3,4],[8,9,4,5,3,6,2,0,1,7],
        [9,4,3,8,6,1,7,2,0,5],[2,5,8,1,4,3,6,7,9,0]];
    interim integer := 0;
    i integer;
BEGIN
    IF digits !~ '^[0-9]*$' THEN
        RETURN -1;
    END IF;
    FOR i IN 1 .. length(digits) LOOP
        interim := table_[interim + 1][substr(digits, i, 1)::integer + 1];
    END LOOP;
    RETURN interim;
END;
$$;

CREATE FUNCTION register.is_valid_national_id(id text) RETURNS boolean
    LANGUAGE sql IMMUTABLE STRICT PARALLEL SAFE AS
$$ SELECT id ~ '^[0-9]{11}$' AND register.damm_interim(id) = 0 $$;

-- Admin units the current transaction may write to. The backend sets app.jurisdictions (comma-separated UUIDs)
-- with set_config(..., true) at the start of every write transaction.
CREATE FUNCTION register.in_jurisdiction(admin_unit uuid) RETURNS boolean
    LANGUAGE sql STABLE PARALLEL SAFE AS
$$
SELECT admin_unit = ANY (
    coalesce(string_to_array(nullif(current_setting('app.jurisdictions', true), ''), ','), ARRAY[]::text[])::uuid[])
$$;

-- Writes history rows. SECURITY DEFINER so that register_app can never write history tables directly.
CREATE FUNCTION register.record_history() RETURNS trigger
    LANGUAGE plpgsql SECURITY DEFINER SET search_path = register, public AS
$$
DECLARE
    row_ record;
BEGIN
    IF TG_OP = 'DELETE' THEN
        row_ := OLD;
    ELSE
        row_ := NEW;
    END IF;
    EXECUTE format('INSERT INTO %I.%I SELECT ($1).*, $2, clock_timestamp(), pg_current_xact_id()::text::bigint, $3',
                   TG_TABLE_SCHEMA, TG_TABLE_NAME || '_history')
        USING row_, lower(TG_OP), nullif(current_setting('app.subject', true), '');
    RETURN NULL;
END;
$$;

-- Keeps version and updated_at current on every update.
CREATE FUNCTION register.touch_row() RETURNS trigger
    LANGUAGE plpgsql AS
$$
BEGIN
    NEW.version := OLD.version + 1;
    NEW.updated_at := clock_timestamp();
    RETURN NEW;
END;
$$;

-- ---------------------------------------------------------------------------------------------------------------
-- Gazetteer: admin units, custodians, jurisdictions, thoroughfares, postcode areas
-- ---------------------------------------------------------------------------------------------------------------

CREATE TABLE register.admin_unit (
    id          uuid PRIMARY KEY DEFAULT uuidv7(),
    parent_id   uuid REFERENCES register.admin_unit (id),
    level       register.admin_level NOT NULL,
    code        text NOT NULL,
    name        text NOT NULL,
    boundary    geometry(MultiPolygon, 4326) NOT NULL,
    valid_from  timestamptz NOT NULL DEFAULT now(),
    valid_to    timestamptz,
    version     bigint NOT NULL DEFAULT 1,
    created_at  timestamptz NOT NULL DEFAULT clock_timestamp(),
    updated_at  timestamptz NOT NULL DEFAULT clock_timestamp(),
    CONSTRAINT admin_unit_validity CHECK (valid_to IS NULL OR valid_to > valid_from),
    CONSTRAINT admin_unit_code_unique EXCLUDE USING btree (code WITH =) WHERE (valid_to IS NULL)
);
CREATE INDEX admin_unit_boundary_idx ON register.admin_unit USING gist (boundary);
CREATE INDEX admin_unit_parent_idx ON register.admin_unit (parent_id);

CREATE TABLE register.custodian (
    id          uuid PRIMARY KEY DEFAULT uuidv7(),
    code        text NOT NULL UNIQUE,
    name        text NOT NULL,
    kind        register.custodian_kind NOT NULL,
    valid_from  timestamptz NOT NULL DEFAULT now(),
    valid_to    timestamptz,
    version     bigint NOT NULL DEFAULT 1,
    created_at  timestamptz NOT NULL DEFAULT clock_timestamp(),
    updated_at  timestamptz NOT NULL DEFAULT clock_timestamp(),
    CONSTRAINT custodian_code_format CHECK (code ~ '^[a-z0-9][a-z0-9-]{1,62}$')
);
COMMENT ON COLUMN register.custodian.name IS 'Name of the organisation (e.g. a district local government), never of a person.';

CREATE TABLE register.jurisdiction (
    id            uuid PRIMARY KEY DEFAULT uuidv7(),
    custodian_id  uuid NOT NULL REFERENCES register.custodian (id),
    admin_unit_id uuid NOT NULL REFERENCES register.admin_unit (id),
    valid_from    timestamptz NOT NULL DEFAULT now(),
    valid_to      timestamptz,
    version       bigint NOT NULL DEFAULT 1,
    created_at    timestamptz NOT NULL DEFAULT clock_timestamp(),
    updated_at    timestamptz NOT NULL DEFAULT clock_timestamp(),
    CONSTRAINT jurisdiction_unique EXCLUDE USING btree (custodian_id WITH =, admin_unit_id WITH =) WHERE (valid_to IS NULL)
);
COMMENT ON TABLE register.jurisdiction IS 'Admin units a custodian maintains; the jurisdiction includes all descendant units.';

CREATE TABLE register.thoroughfare (
    id            uuid PRIMARY KEY DEFAULT uuidv7(),
    admin_unit_id uuid NOT NULL REFERENCES register.admin_unit (id),
    name          text NOT NULL,
    centreline    geometry(LineString, 4326) NOT NULL,
    lifecycle     register.lifecycle NOT NULL DEFAULT 'active',
    valid_from    timestamptz NOT NULL DEFAULT now(),
    valid_to      timestamptz,
    version       bigint NOT NULL DEFAULT 1,
    created_at    timestamptz NOT NULL DEFAULT clock_timestamp(),
    updated_at    timestamptz NOT NULL DEFAULT clock_timestamp(),
    CONSTRAINT thoroughfare_validity CHECK (valid_to IS NULL OR valid_to > valid_from)
);
CREATE INDEX thoroughfare_centreline_idx ON register.thoroughfare USING gist (centreline);
CREATE INDEX thoroughfare_name_trgm_idx ON register.thoroughfare USING gin (name gin_trgm_ops);

CREATE TABLE register.postcode_area (
    id            uuid PRIMARY KEY DEFAULT uuidv7(),
    admin_unit_id uuid NOT NULL REFERENCES register.admin_unit (id),
    code          text NOT NULL,
    area          geometry(MultiPolygon, 4326) NOT NULL,
    valid_from    timestamptz NOT NULL DEFAULT now(),
    valid_to      timestamptz,
    version       bigint NOT NULL DEFAULT 1,
    created_at    timestamptz NOT NULL DEFAULT clock_timestamp(),
    updated_at    timestamptz NOT NULL DEFAULT clock_timestamp(),
    CONSTRAINT postcode_area_code_unique EXCLUDE USING btree (code WITH =) WHERE (valid_to IS NULL)
);
CREATE INDEX postcode_area_area_idx ON register.postcode_area USING gist (area);

-- ---------------------------------------------------------------------------------------------------------------
-- Register: addressable objects, addresses, aliases, assertions, verifications
-- ---------------------------------------------------------------------------------------------------------------

CREATE TABLE register.addressable_object (
    id                 uuid PRIMARY KEY DEFAULT uuidv7(),
    national_id        char(11) NOT NULL UNIQUE,
    national_id_status register.national_id_status NOT NULL DEFAULT 'allocated',
    kind               register.object_kind NOT NULL,
    parent_id          uuid REFERENCES register.addressable_object (id),
    name               text,
    residential        boolean NOT NULL DEFAULT false,
    main_entrance      boolean NOT NULL DEFAULT false,
    location           geometry(Point, 4326) NOT NULL,
    footprint          geometry(MultiPolygon, 4326),
    admin_unit_id      uuid NOT NULL REFERENCES register.admin_unit (id),
    lifecycle          register.lifecycle NOT NULL DEFAULT 'active',
    valid_from         timestamptz NOT NULL DEFAULT now(),
    valid_to           timestamptz,
    version            bigint NOT NULL DEFAULT 1,
    created_at         timestamptz NOT NULL DEFAULT clock_timestamp(),
    updated_at         timestamptz NOT NULL DEFAULT clock_timestamp(),
    CONSTRAINT addressable_object_national_id_valid CHECK (register.is_valid_national_id(national_id)),
    CONSTRAINT addressable_object_entrance_has_parent CHECK (kind <> 'entrance' OR parent_id IS NOT NULL),
    CONSTRAINT addressable_object_validity CHECK (valid_to IS NULL OR valid_to > valid_from)
);
COMMENT ON COLUMN register.addressable_object.name IS 'Name of a landmark or facility. Never the name of a person.';
COMMENT ON COLUMN register.addressable_object.residential IS 'Residential entrance coordinates are only returned to partners (ADR 0009).';
CREATE INDEX addressable_object_location_idx ON register.addressable_object USING gist (location);
CREATE INDEX addressable_object_parent_idx ON register.addressable_object (parent_id);
CREATE INDEX addressable_object_admin_unit_idx ON register.addressable_object (admin_unit_id);

CREATE TABLE register.address (
    id               uuid PRIMARY KEY DEFAULT uuidv7(),
    object_id        uuid NOT NULL REFERENCES register.addressable_object (id),
    thoroughfare_id  uuid NOT NULL REFERENCES register.thoroughfare (id),
    house_number     text NOT NULL,
    unit             text,
    postcode_area_id uuid REFERENCES register.postcode_area (id),
    admin_unit_id    uuid NOT NULL REFERENCES register.admin_unit (id),
    lifecycle        register.lifecycle NOT NULL DEFAULT 'active',
    valid_from       timestamptz NOT NULL DEFAULT now(),
    valid_to         timestamptz,
    version          bigint NOT NULL DEFAULT 1,
    created_at       timestamptz NOT NULL DEFAULT clock_timestamp(),
    updated_at       timestamptz NOT NULL DEFAULT clock_timestamp(),
    CONSTRAINT address_house_number_format CHECK (house_number ~ '^[0-9]{1,5}[A-Z]?$'),
    CONSTRAINT address_validity CHECK (valid_to IS NULL OR valid_to > valid_from),
    -- One active address per (thoroughfare, house number, unit).
    CONSTRAINT address_one_active EXCLUDE USING btree (
        thoroughfare_id WITH =, house_number WITH =, (coalesce(unit, '')) WITH =
    ) WHERE (lifecycle = 'active')
);
CREATE INDEX address_object_idx ON register.address (object_id);

CREATE TABLE register.alias (
    id            uuid PRIMARY KEY DEFAULT uuidv7(),
    object_id     uuid NOT NULL REFERENCES register.addressable_object (id),
    system        text NOT NULL,
    value         text NOT NULL,
    admin_unit_id uuid NOT NULL REFERENCES register.admin_unit (id),
    valid_from    timestamptz NOT NULL DEFAULT now(),
    valid_to      timestamptz,
    version       bigint NOT NULL DEFAULT 1,
    created_at    timestamptz NOT NULL DEFAULT clock_timestamp(),
    updated_at    timestamptz NOT NULL DEFAULT clock_timestamp(),
    CONSTRAINT alias_system_value_unique UNIQUE (system, value),
    CONSTRAINT alias_system_format CHECK (system ~ '^[a-z0-9][a-z0-9-]{1,62}$')
);
CREATE INDEX alias_object_idx ON register.alias (object_id);

-- Claims about an object made by a source (append-only facts).
CREATE TABLE register.assertion (
    id            uuid PRIMARY KEY DEFAULT uuidv7(),
    object_id     uuid NOT NULL REFERENCES register.addressable_object (id),
    predicate     text NOT NULL,
    value         jsonb NOT NULL,
    custodian_id  uuid REFERENCES register.custodian (id),
    admin_unit_id uuid NOT NULL REFERENCES register.admin_unit (id),
    asserted_at   timestamptz NOT NULL DEFAULT clock_timestamp()
);
CREATE INDEX assertion_object_idx ON register.assertion (object_id);

-- ---------------------------------------------------------------------------------------------------------------
-- Workflow: change requests (four-eyes rule)
-- ---------------------------------------------------------------------------------------------------------------

CREATE TABLE register.change_request (
    id               uuid PRIMARY KEY DEFAULT uuidv7(),
    kind             register.change_kind NOT NULL,
    state            register.change_state NOT NULL DEFAULT 'submitted',
    source           register.change_source NOT NULL,
    summary          text NOT NULL,
    target_object_id uuid REFERENCES register.addressable_object (id),
    thoroughfare_id  uuid REFERENCES register.thoroughfare (id),
    admin_unit_id    uuid NOT NULL REFERENCES register.admin_unit (id),
    custodian_id     uuid NOT NULL REFERENCES register.custodian (id),
    proposal         jsonb NOT NULL DEFAULT '{}'::jsonb,
    photo_object_key text,
    -- Opaque OIDC subject identifiers of staff; never names or emails (ADR 0009).
    proposed_by      text NOT NULL,
    proposed_at      timestamptz NOT NULL DEFAULT clock_timestamp(),
    decided_by       text,
    decided_at       timestamptz,
    version          bigint NOT NULL DEFAULT 1,
    created_at       timestamptz NOT NULL DEFAULT clock_timestamp(),
    updated_at       timestamptz NOT NULL DEFAULT clock_timestamp(),
    CONSTRAINT change_request_four_eyes CHECK (decided_by IS DISTINCT FROM proposed_by),
    CONSTRAINT change_request_decision CHECK (
        (state IN ('approved', 'rejected', 'applied')) = (decided_by IS NOT NULL AND decided_at IS NOT NULL)),
    CONSTRAINT change_request_target CHECK (kind = 'new_object' OR target_object_id IS NOT NULL OR thoroughfare_id IS NOT NULL),
    CONSTRAINT change_request_summary_length CHECK (length(summary) BETWEEN 3 AND 500)
);
CREATE INDEX change_request_admin_unit_state_idx ON register.change_request (admin_unit_id, state);

-- Outcome of checking an object, e.g. during a field visit.
CREATE TABLE register.verification (
    id                uuid PRIMARY KEY DEFAULT uuidv7(),
    object_id         uuid NOT NULL REFERENCES register.addressable_object (id),
    change_request_id uuid REFERENCES register.change_request (id),
    method            text NOT NULL,
    outcome           register.verification_outcome NOT NULL,
    verified_by       text NOT NULL,
    admin_unit_id     uuid NOT NULL REFERENCES register.admin_unit (id),
    verified_at       timestamptz NOT NULL DEFAULT clock_timestamp()
);
CREATE INDEX verification_object_idx ON register.verification (object_id);

-- ---------------------------------------------------------------------------------------------------------------
-- History twins
-- ---------------------------------------------------------------------------------------------------------------

DO
$$
DECLARE
    t text;
BEGIN
    FOREACH t IN ARRAY ARRAY['admin_unit', 'custodian', 'jurisdiction', 'thoroughfare', 'postcode_area',
                             'addressable_object', 'address', 'alias', 'change_request']
    LOOP
        EXECUTE format('CREATE TABLE register.%I (LIKE register.%I)', t || '_history', t);
        EXECUTE format('ALTER TABLE register.%I
                            ADD COLUMN history_operation text NOT NULL,
                            ADD COLUMN history_recorded_at timestamptz NOT NULL,
                            ADD COLUMN history_txid bigint NOT NULL,
                            ADD COLUMN history_actor text,
                            ADD COLUMN history_seq bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY',
                       t || '_history');
        EXECUTE format('CREATE INDEX %I ON register.%I (id, history_seq DESC)', t || '_history_id_idx', t || '_history');
        EXECUTE format('CREATE TRIGGER %I AFTER INSERT OR UPDATE OR DELETE ON register.%I
                            FOR EACH ROW EXECUTE FUNCTION register.record_history()', t || '_history_trg', t);
        EXECUTE format('CREATE TRIGGER %I BEFORE UPDATE ON register.%I
                            FOR EACH ROW EXECUTE FUNCTION register.touch_row()', t || '_touch_trg', t);
    END LOOP;
END;
$$;

-- ---------------------------------------------------------------------------------------------------------------
-- Audit: append-only, hash-chained
-- ---------------------------------------------------------------------------------------------------------------

CREATE TABLE register.audit_event (
    seq          bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    id           uuid NOT NULL UNIQUE DEFAULT uuidv7(),
    occurred_at  timestamptz NOT NULL,
    actor        text NOT NULL,
    custodian_id uuid,
    action       text NOT NULL,
    entity_type  text NOT NULL,
    entity_id    uuid,
    payload      jsonb NOT NULL DEFAULT '{}'::jsonb,
    prev_hash    bytea,
    hash         bytea NOT NULL UNIQUE
);
COMMENT ON TABLE register.audit_event IS
    'Append-only. hash = sha256(prev_hash || canonical content); write only through register.append_audit_event().';

CREATE FUNCTION register.audit_canonical(e register.audit_event) RETURNS bytea
    LANGUAGE sql IMMUTABLE AS
$$
SELECT convert_to(concat_ws('|', e.id::text, to_char(e.occurred_at AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS.US"Z"'),
                            e.actor, coalesce(e.custodian_id::text, ''), e.action, e.entity_type,
                            coalesce(e.entity_id::text, ''), e.payload::text), 'UTF8')
$$;

-- Appends one event. A transaction-scoped advisory lock serialises appends so that the chain follows commit order.
CREATE FUNCTION register.append_audit_event(
    p_actor text, p_custodian_id uuid, p_action text, p_entity_type text, p_entity_id uuid, p_payload jsonb)
    RETURNS register.audit_event
    LANGUAGE plpgsql SECURITY DEFINER SET search_path = register, public AS
$$
DECLARE
    event_ register.audit_event;
BEGIN
    PERFORM pg_advisory_xact_lock(hashtext('register.audit_event'));
    event_.id := uuidv7();
    event_.occurred_at := date_trunc('microseconds', clock_timestamp());
    event_.actor := p_actor;
    event_.custodian_id := p_custodian_id;
    event_.action := p_action;
    event_.entity_type := p_entity_type;
    event_.entity_id := p_entity_id;
    event_.payload := coalesce(p_payload, '{}'::jsonb);
    SELECT a.hash INTO event_.prev_hash FROM register.audit_event a ORDER BY a.seq DESC LIMIT 1;
    event_.hash := digest(coalesce(event_.prev_hash, ''::bytea) || register.audit_canonical(event_), 'sha256');

    INSERT INTO register.audit_event (id, occurred_at, actor, custodian_id, action, entity_type, entity_id, payload,
                                      prev_hash, hash)
    VALUES (event_.id, event_.occurred_at, event_.actor, event_.custodian_id, event_.action, event_.entity_type,
            event_.entity_id, event_.payload, event_.prev_hash, event_.hash)
    RETURNING * INTO event_;
    RETURN event_;
END;
$$;

-- Returns the seq of the first event whose hash does not match, or NULL if the chain is intact.
CREATE FUNCTION register.verify_audit_chain() RETURNS bigint
    LANGUAGE plpgsql STABLE SECURITY DEFINER SET search_path = register, public AS
$$
DECLARE
    e register.audit_event;
    prev bytea := NULL;
BEGIN
    FOR e IN SELECT * FROM register.audit_event ORDER BY seq LOOP
        IF e.prev_hash IS DISTINCT FROM prev
           OR e.hash <> digest(coalesce(prev, ''::bytea) || register.audit_canonical(e), 'sha256') THEN
            RETURN e.seq;
        END IF;
        prev := e.hash;
    END LOOP;
    RETURN NULL;
END;
$$;

CREATE FUNCTION register.reject_audit_mutation() RETURNS trigger
    LANGUAGE plpgsql AS
$$
BEGIN
    RAISE EXCEPTION 'audit_event is append-only' USING ERRCODE = 'insufficient_privilege';
END;
$$;

CREATE TRIGGER audit_event_no_update BEFORE UPDATE OR DELETE ON register.audit_event
    FOR EACH ROW EXECUTE FUNCTION register.reject_audit_mutation();
CREATE TRIGGER audit_event_no_truncate BEFORE TRUNCATE ON register.audit_event
    FOR EACH STATEMENT EXECUTE FUNCTION register.reject_audit_mutation();

-- ---------------------------------------------------------------------------------------------------------------
-- Idempotency of POST requests
-- ---------------------------------------------------------------------------------------------------------------

CREATE TABLE register.idempotency_key (
    principal       text NOT NULL,
    idempotency_key text NOT NULL,
    operation       text NOT NULL,
    request_hash    bytea NOT NULL,
    response_status integer,
    resource_id     uuid,
    created_at      timestamptz NOT NULL DEFAULT clock_timestamp(),
    PRIMARY KEY (principal, idempotency_key)
);
CREATE INDEX idempotency_key_created_idx ON register.idempotency_key (created_at);

-- ---------------------------------------------------------------------------------------------------------------
-- Row-level security
-- ---------------------------------------------------------------------------------------------------------------

DO
$$
DECLARE
    t text;
BEGIN
    FOREACH t IN ARRAY ARRAY['thoroughfare', 'postcode_area', 'addressable_object', 'address', 'alias',
                             'assertion', 'verification', 'change_request']
    LOOP
        EXECUTE format('ALTER TABLE register.%I ENABLE ROW LEVEL SECURITY', t);
        EXECUTE format('CREATE POLICY %I ON register.%I FOR SELECT TO register_app USING (true)', t || '_read', t);
        EXECUTE format('CREATE POLICY %I ON register.%I FOR INSERT TO register_app
                            WITH CHECK (register.in_jurisdiction(admin_unit_id))', t || '_insert', t);
        EXECUTE format('CREATE POLICY %I ON register.%I FOR UPDATE TO register_app
                            USING (register.in_jurisdiction(admin_unit_id))
                            WITH CHECK (register.in_jurisdiction(admin_unit_id))', t || '_update', t);
        EXECUTE format('CREATE POLICY %I ON register.%I FOR DELETE TO register_app
                            USING (register.in_jurisdiction(admin_unit_id))', t || '_delete', t);
    END LOOP;
END;
$$;

-- ---------------------------------------------------------------------------------------------------------------
-- Grants
-- ---------------------------------------------------------------------------------------------------------------

GRANT USAGE ON SCHEMA register TO register_app;
GRANT SELECT ON ALL TABLES IN SCHEMA register TO register_app;
GRANT INSERT, UPDATE ON register.thoroughfare, register.postcode_area, register.addressable_object,
    register.address, register.alias, register.change_request TO register_app;
GRANT INSERT ON register.assertion, register.verification TO register_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON register.idempotency_key TO register_app;
REVOKE INSERT, UPDATE, DELETE, TRUNCATE ON register.audit_event FROM PUBLIC, register_app;
REVOKE EXECUTE ON FUNCTION register.append_audit_event(text, uuid, text, text, uuid, jsonb) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION register.append_audit_event(text, uuid, text, text, uuid, jsonb) TO register_app;
REVOKE EXECUTE ON FUNCTION register.record_history() FROM PUBLIC;

-- ---------------------------------------------------------------------------------------------------------------
-- Public map tiles (Martin). Views run with the owner's rights; martin_reader sees only this schema.
-- Residential entrances are never published (ADR 0009).
-- ---------------------------------------------------------------------------------------------------------------

CREATE VIEW tiles.thoroughfares AS
SELECT t.id::text AS id,
       t.name,
       t.admin_unit_id::text AS admin_unit_id,
       (SELECT string_agg(DISTINCT c.code, ',')
          FROM register.jurisdiction j
          JOIN register.custodian c ON c.id = j.custodian_id
         WHERE j.valid_to IS NULL
           AND j.admin_unit_id IN (WITH RECURSIVE up AS (
                                       SELECT a.id, a.parent_id FROM register.admin_unit a WHERE a.id = t.admin_unit_id
                                       UNION ALL
                                       SELECT a.id, a.parent_id FROM register.admin_unit a JOIN up ON a.id = up.parent_id)
                                   SELECT up.id FROM up)) AS custodians,
       t.centreline AS geom
  FROM register.thoroughfare t
 WHERE t.lifecycle = 'active' AND t.valid_to IS NULL;

CREATE VIEW tiles.buildings AS
SELECT o.id::text AS id,
       o.national_id,
       o.national_id_status::text AS national_id_status,
       o.kind::text AS kind,
       o.name,
       coalesce(o.footprint, ST_Multi(ST_Buffer(o.location::geography, 4)::geometry))::geometry(MultiPolygon, 4326) AS geom
  FROM register.addressable_object o
 WHERE o.kind IN ('building', 'landmark', 'facility') AND o.lifecycle = 'active' AND o.valid_to IS NULL;

CREATE VIEW tiles.public_entrances AS
SELECT o.id::text AS id,
       o.national_id,
       o.location AS geom
  FROM register.addressable_object o
 WHERE o.kind = 'entrance' AND NOT o.residential AND o.lifecycle = 'active' AND o.valid_to IS NULL;

CREATE VIEW tiles.admin_units AS
SELECT a.id::text AS id, a.level::text AS level, a.name, a.boundary AS geom
  FROM register.admin_unit a
 WHERE a.valid_to IS NULL;

GRANT USAGE ON SCHEMA tiles TO martin_reader;
GRANT SELECT ON ALL TABLES IN SCHEMA tiles TO martin_reader;
