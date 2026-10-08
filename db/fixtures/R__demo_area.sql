-- Synthetic demonstration area. Everything here is invented: places, streets, organisations and IDs.
-- There is no personal data. All national IDs have the status 'demonstration' and display with a DEMO marker.
--
-- Loaded by Flyway as a repeatable migration only in local/demo environments (location filesystem:db/fixtures).
-- National IDs are derived deterministically from a seed, so they are stable across `make db-reset`.

SET search_path = register, public;

DO
$fixtures$
DECLARE
    region_id     uuid;
    district_id   uuid;
    mirembe_id    uuid;
    amani_id      uuid;
    district_cust uuid;
    city_cust     uuid;
    ministry_cust uuid;
    street        record;
    street_id     uuid;
    postcode_id   uuid;
    building_id   uuid;
    n             integer;
    side          integer;
    along         double precision;
    bx            double precision;
    by_           double precision;
    payload       text;
    is_facility   boolean;
    buildings     integer := 0;
BEGIN
    IF EXISTS (SELECT 1 FROM register.custodian WHERE code = 'demo-city') THEN
        RAISE NOTICE 'Demo fixtures already loaded; skipping.';
        RETURN;
    END IF;

    PERFORM set_config('app.subject', 'system:fixtures', true);

    -- Admin units: an invented region and district with two parishes side by side.
    INSERT INTO admin_unit (level, code, name, boundary)
    VALUES ('region', 'DEMO-R', 'Demo Region',
            ST_Multi(ST_MakeEnvelope(32.560, 0.330, 32.620, 0.370, 4326)))
    RETURNING id INTO region_id;
    INSERT INTO admin_unit (parent_id, level, code, name, boundary)
    VALUES (region_id, 'district', 'DEMO-D1', 'Demo District',
            ST_Multi(ST_MakeEnvelope(32.570, 0.340, 32.610, 0.360, 4326)))
    RETURNING id INTO district_id;
    INSERT INTO admin_unit (parent_id, level, code, name, boundary)
    VALUES (district_id, 'parish', 'DEMO-P1', 'Mirembe Parish (demo)',
            ST_Multi(ST_MakeEnvelope(32.570, 0.340, 32.590, 0.360, 4326)))
    RETURNING id INTO mirembe_id;
    INSERT INTO admin_unit (parent_id, level, code, name, boundary)
    VALUES (district_id, 'parish', 'DEMO-P2', 'Amani Parish (demo)',
            ST_Multi(ST_MakeEnvelope(32.590, 0.340, 32.610, 0.360, 4326)))
    RETURNING id INTO amani_id;

    -- Custodians (organisations, not people) and their jurisdictions.
    INSERT INTO custodian (code, name, kind) VALUES ('demo-district', 'Demo District Local Government', 'local_government')
    RETURNING id INTO district_cust;
    INSERT INTO custodian (code, name, kind) VALUES ('demo-city', 'Demo City Authority', 'city_authority')
    RETURNING id INTO city_cust;
    INSERT INTO custodian (code, name, kind) VALUES ('demo-ministry', 'Demo Ministry of ICT', 'ministry')
    RETURNING id INTO ministry_cust;
    INSERT INTO jurisdiction (custodian_id, admin_unit_id) VALUES (district_cust, mirembe_id);
    INSERT INTO jurisdiction (custodian_id, admin_unit_id) VALUES (city_cust, amani_id);
    INSERT INTO jurisdiction (custodian_id, admin_unit_id) VALUES (ministry_cust, district_id);

    -- Postcode areas, one per parish.
    INSERT INTO postcode_area (admin_unit_id, code, area)
    SELECT id, CASE code WHEN 'DEMO-P1' THEN 'D101' ELSE 'D102' END, boundary
      FROM admin_unit WHERE id IN (mirembe_id, amani_id);

    -- Four streets; buildings alternate sides, odd numbers north/east, even numbers south/west.
    FOR street IN
        SELECT * FROM (VALUES
            ('MIR', 'Mirembe Road',    mirembe_id, 32.5730, 0.3500, true,  13),
            ('KIT', 'Kitenge Lane',    mirembe_id, 32.5850, 0.3430, false, 13),
            ('AMA', 'Amani Avenue',    amani_id,   32.5930, 0.3500, true,  12),
            ('JAC', 'Jacaranda Close', amani_id,   32.6050, 0.3430, false, 12)
        ) AS s(code, name, admin_unit_id, x0, y0, east_west, building_count)
    LOOP
        INSERT INTO thoroughfare (admin_unit_id, name, centreline)
        VALUES (street.admin_unit_id, street.name,
                CASE WHEN street.east_west
                     THEN ST_SetSRID(ST_MakeLine(ST_MakePoint(street.x0, street.y0), ST_MakePoint(street.x0 + 0.0040, street.y0)), 4326)
                     ELSE ST_SetSRID(ST_MakeLine(ST_MakePoint(street.x0, street.y0), ST_MakePoint(street.x0, street.y0 + 0.0040)), 4326)
                END)
        RETURNING id INTO street_id;

        SELECT id INTO postcode_id FROM postcode_area
         WHERE admin_unit_id = street.admin_unit_id AND valid_to IS NULL;

        FOR n IN 1 .. street.building_count LOOP
            side := CASE WHEN n % 2 = 1 THEN 1 ELSE -1 END;
            along := 0.0002 + 0.0003 * ((n - 1) / 2) + CASE WHEN n % 2 = 0 THEN 0.00015 ELSE 0 END;
            IF street.east_west THEN
                bx := street.x0 + along;
                by_ := street.y0 + side * 0.00016;
            ELSE
                bx := street.x0 + side * 0.00016;
                by_ := street.y0 + along;
            END IF;
            is_facility := n % 6 = 0;

            payload := lpad(((('x' || substr(md5('ugaddress-demo:' || street.code || ':' || n), 1, 15))::bit(60)::bigint)
                             % 10000000000)::text, 10, '0');
            INSERT INTO addressable_object (national_id, national_id_status, kind, name, residential, location,
                                            footprint, admin_unit_id)
            VALUES (payload || register.damm_interim(payload)::text, 'demonstration',
                    CASE WHEN is_facility THEN 'facility' ELSE 'building' END::object_kind,
                    CASE WHEN is_facility THEN street.name || ' ' || CASE WHEN n % 12 = 0 THEN 'Health Centre' ELSE 'Primary School' END || ' (demo)' END,
                    NOT is_facility,
                    ST_SetSRID(ST_MakePoint(bx, by_), 4326),
                    ST_Multi(ST_SetSRID(ST_MakeEnvelope(bx - 0.00005, by_ - 0.00004, bx + 0.00005, by_ + 0.00004), 4326)),
                    street.admin_unit_id)
            RETURNING id INTO building_id;

            payload := lpad(((('x' || substr(md5('ugaddress-demo:' || street.code || ':' || n || ':entrance'), 1, 15))::bit(60)::bigint)
                             % 10000000000)::text, 10, '0');
            INSERT INTO addressable_object (national_id, national_id_status, kind, parent_id, residential, main_entrance,
                                            location, admin_unit_id)
            VALUES (payload || register.damm_interim(payload)::text, 'demonstration', 'entrance', building_id,
                    NOT is_facility, true,
                    CASE WHEN street.east_west
                         THEN ST_SetSRID(ST_MakePoint(bx, street.y0 + side * 0.00007), 4326)
                         ELSE ST_SetSRID(ST_MakePoint(street.x0 + side * 0.00007, by_), 4326)
                    END,
                    street.admin_unit_id);

            INSERT INTO address (object_id, thoroughfare_id, house_number, postcode_area_id, admin_unit_id)
            VALUES (building_id, street_id, n::text, postcode_id, street.admin_unit_id);

            INSERT INTO alias (object_id, system, value, admin_unit_id)
            VALUES (building_id, 'demo-plot', street.code || '-' || lpad(n::text, 4, '0'), street.admin_unit_id);

            buildings := buildings + 1;
        END LOOP;
    END LOOP;

    PERFORM append_audit_event('system:fixtures', NULL, 'fixtures.loaded', 'fixture', NULL,
                               jsonb_build_object('name', 'demo-area', 'buildings', buildings));
END;
$fixtures$;
