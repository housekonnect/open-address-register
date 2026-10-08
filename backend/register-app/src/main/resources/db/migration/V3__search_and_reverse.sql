-- Search (/v1/search) and reverse lookup (/v1/reverse).
--
-- search_document holds one row per current addressable object (except entrances, which are found through their
-- building). It is derived data, rebuilt by triggers whenever an object, its entrances, address, aliases, street or
-- postcode area changes; it is never edited directly and therefore has no _history twin. register_app may only
-- read it: rows are written by register.refresh_search_document(), which runs with the owner's rights.

SET search_path = register, public;

-- Lower-case, accent-free form used on both sides of every comparison. unaccent() with an explicit dictionary is
-- only STABLE; pinning the dictionary makes this wrapper safe to declare IMMUTABLE (and to use in indexes).
CREATE FUNCTION register.search_normalise(value text) RETURNS text
    LANGUAGE sql IMMUTABLE STRICT PARALLEL SAFE AS
$$ SELECT lower(public.unaccent('public.unaccent'::regdictionary, value)) $$;

CREATE TABLE register.search_document (
    object_id    uuid PRIMARY KEY REFERENCES register.addressable_object (id) ON DELETE CASCADE,
    -- The object's national ID and those of its entrances.
    national_ids text[] NOT NULL,
    -- Normalised text: name, address, postcode, aliases and national IDs.
    search_text  text NOT NULL,
    document     tsvector NOT NULL,
    -- Stable order among equally ranked results: street, house number, object id.
    sort_key     text NOT NULL
);
CREATE INDEX search_document_document_idx ON register.search_document USING gin (document);
CREATE INDEX search_document_text_trgm_idx ON register.search_document USING gin (search_text gin_trgm_ops);
CREATE INDEX search_document_national_ids_idx ON register.search_document USING gin (national_ids);

CREATE FUNCTION register.refresh_search_document(p_object_id uuid) RETURNS void
    LANGUAGE plpgsql SECURITY DEFINER SET search_path = register, public AS
$$
BEGIN
    DELETE FROM register.search_document WHERE object_id = p_object_id;
    INSERT INTO register.search_document (object_id, national_ids, search_text, document, sort_key)
    SELECT o.id, ids.national_ids, doc.search_text, to_tsvector('simple', doc.search_text), doc.sort_key
      FROM register.addressable_object o
      LEFT JOIN LATERAL (
          SELECT a.house_number, a.unit, t.name AS street, p.code AS postcode
            FROM register.address a
            JOIN register.thoroughfare t ON t.id = a.thoroughfare_id
            LEFT JOIN register.postcode_area p ON p.id = a.postcode_area_id
           WHERE a.object_id = o.id AND a.lifecycle = 'active' AND a.valid_to IS NULL
           LIMIT 1) addr ON true
      CROSS JOIN LATERAL (
          SELECT array_prepend(o.national_id::text, coalesce(array_agg(e.national_id::text ORDER BY e.national_id),
                                                             ARRAY[]::text[])) AS national_ids
            FROM register.addressable_object e
           WHERE e.parent_id = o.id AND e.kind = 'entrance' AND e.lifecycle = 'active' AND e.valid_to IS NULL) ids
      CROSS JOIN LATERAL (
          SELECT string_agg(al.system || ':' || al.value || ' ' || al.value, ' ') AS aliases
            FROM register.alias al
           WHERE al.object_id = o.id AND al.valid_to IS NULL) als
      CROSS JOIN LATERAL (
          SELECT register.search_normalise(concat_ws(' ',
                     o.name,
                     concat_ws(' ', coalesce(addr.unit || '/', '') || addr.house_number, addr.street),
                     addr.postcode,
                     als.aliases,
                     array_to_string(ids.national_ids, ' '))) AS search_text,
                 register.search_normalise(coalesce(addr.street, o.name, '')) || ' '
                     || lpad(coalesce(substring(addr.house_number FROM '^[0-9]+'), '0'), 5, '0')
                     || coalesce(addr.house_number, '') || ' ' || o.id::text AS sort_key) doc
     WHERE o.id = p_object_id
       AND o.kind <> 'entrance'
       AND o.lifecycle = 'active'
       AND o.valid_to IS NULL;
END;
$$;
REVOKE EXECUTE ON FUNCTION register.refresh_search_document(uuid) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION register.refresh_search_document(uuid) TO register_app;

-- Objects (and, for entrances, their buildings) whose search document depends on the changed row.
CREATE FUNCTION register.search_document_on_object() RETURNS trigger
    LANGUAGE plpgsql AS
$$
DECLARE
    id_ uuid;
BEGIN
    FOR id_ IN
        SELECT DISTINCT x FROM unnest(CASE TG_OP
            WHEN 'INSERT' THEN ARRAY[NEW.id, NEW.parent_id]
            WHEN 'UPDATE' THEN ARRAY[NEW.id, NEW.parent_id, OLD.parent_id]
            ELSE ARRAY[OLD.id, OLD.parent_id] END) AS x
         WHERE x IS NOT NULL
    LOOP
        PERFORM register.refresh_search_document(id_);
    END LOOP;
    RETURN NULL;
END;
$$;

-- Address and alias rows point at their object through object_id.
CREATE FUNCTION register.search_document_on_object_part() RETURNS trigger
    LANGUAGE plpgsql AS
$$
BEGIN
    IF TG_OP IN ('UPDATE', 'DELETE') THEN
        PERFORM register.refresh_search_document(OLD.object_id);
    END IF;
    IF TG_OP IN ('INSERT', 'UPDATE') AND (TG_OP = 'INSERT' OR NEW.object_id IS DISTINCT FROM OLD.object_id) THEN
        PERFORM register.refresh_search_document(NEW.object_id);
    END IF;
    RETURN NULL;
END;
$$;

-- A renamed street or recoded postcode area changes every address that uses it.
CREATE FUNCTION register.search_document_on_address_reference() RETURNS trigger
    LANGUAGE plpgsql AS
$$
DECLARE
    id_ uuid;
BEGIN
    FOR id_ IN
        SELECT DISTINCT a.object_id FROM register.address a
         WHERE (TG_TABLE_NAME = 'thoroughfare' AND a.thoroughfare_id = NEW.id)
            OR (TG_TABLE_NAME = 'postcode_area' AND a.postcode_area_id = NEW.id)
    LOOP
        PERFORM register.refresh_search_document(id_);
    END LOOP;
    RETURN NULL;
END;
$$;

CREATE TRIGGER search_document_object_trg AFTER INSERT OR UPDATE OR DELETE ON register.addressable_object
    FOR EACH ROW EXECUTE FUNCTION register.search_document_on_object();
CREATE TRIGGER search_document_address_trg AFTER INSERT OR UPDATE OR DELETE ON register.address
    FOR EACH ROW EXECUTE FUNCTION register.search_document_on_object_part();
CREATE TRIGGER search_document_alias_trg AFTER INSERT OR UPDATE OR DELETE ON register.alias
    FOR EACH ROW EXECUTE FUNCTION register.search_document_on_object_part();
CREATE TRIGGER search_document_thoroughfare_trg AFTER UPDATE OF name ON register.thoroughfare
    FOR EACH ROW EXECUTE FUNCTION register.search_document_on_address_reference();
CREATE TRIGGER search_document_postcode_trg AFTER UPDATE OF code ON register.postcode_area
    FOR EACH ROW EXECUTE FUNCTION register.search_document_on_address_reference();

-- Documents for objects that already exist.
SELECT register.refresh_search_document(o.id) FROM register.addressable_object o WHERE o.kind <> 'entrance';

GRANT SELECT ON register.search_document TO register_app;

-- Reverse lookup measures metres on the spheroid (geography); these indexes serve ST_DWithin on it.
CREATE INDEX addressable_object_location_geog_idx ON register.addressable_object USING gist ((location::geography));
CREATE INDEX thoroughfare_centreline_geog_idx ON register.thoroughfare USING gist ((centreline::geography));
