package org.ugaddress.register.gazetteer.internal;

import static org.ugaddress.db.generated.Tables.CUSTODIAN;
import static org.ugaddress.db.generated.Tables.THOROUGHFARE;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;
import org.ugaddress.register.gazetteer.AdminUnitDTO;
import org.ugaddress.register.gazetteer.CustodianDTO;
import org.ugaddress.register.gazetteer.ThoroughfareDTO;

/**
 * jOOQ access to gazetteer tables. Spatial predicates use PostGIS directly.
 */
@Repository
public class GazetteerRepository {

    private final DSLContext dsl;

    GazetteerRepository(final DSLContext dsl) {
        this.dsl = dsl;
    }

    /**
     * Returns a unit and its ancestors, smallest first.
     *
     * @param adminUnitId the unit
     * @return the chain
     */
    public List<AdminUnitDTO> adminUnitChain(final UUID adminUnitId) {
        return dsl.fetch("""
                with recursive chain as (
                    select a.id, a.parent_id, a.level::text as level, a.code, a.name, 0 as depth
                      from register.admin_unit a where a.id = ?
                    union all
                    select p.id, p.parent_id, p.level::text, p.code, p.name, c.depth + 1
                      from register.admin_unit p join chain c on p.id = c.parent_id
                )
                select id, level, code, name from chain order by depth
                """, adminUnitId)
            .map(r -> new AdminUnitDTO(r.get("id", UUID.class), r.get("level", String.class),
                r.get("code", String.class), r.get("name", String.class)));
    }

    /**
     * Finds the smallest current unit containing a coordinate.
     *
     * @param longitude longitude
     * @param latitude latitude
     * @return the unit id
     */
    public Optional<UUID> smallestAdminUnitContaining(final double longitude, final double latitude) {
        return dsl.fetchOptional("""
                select id from register.admin_unit
                 where valid_to is null and ST_Covers(boundary, ST_SetSRID(ST_MakePoint(?, ?), 4326))
                 order by ST_Area(boundary::geography) asc
                 limit 1
                """, longitude, latitude)
            .map(r -> r.get(0, UUID.class));
    }

    /**
     * Finds a thoroughfare.
     *
     * @param id id
     * @return the thoroughfare
     */
    public Optional<ThoroughfareDTO> thoroughfare(final UUID id) {
        return dsl.select(THOROUGHFARE.ID, THOROUGHFARE.NAME, THOROUGHFARE.ADMIN_UNIT_ID)
            .from(THOROUGHFARE)
            .where(THOROUGHFARE.ID.eq(id))
            .fetchOptional(r -> new ThoroughfareDTO(r.value1(), r.value2(), r.value3()));
    }

    /**
     * Finds a current custodian by code.
     *
     * @param code code
     * @return the custodian
     */
    public Optional<CustodianDTO> custodianByCode(final String code) {
        return dsl.select(CUSTODIAN.ID, CUSTODIAN.CODE, CUSTODIAN.NAME)
            .from(CUSTODIAN)
            .where(CUSTODIAN.CODE.eq(code))
            .and(CUSTODIAN.VALID_TO.isNull())
            .fetchOptional(r -> new CustodianDTO(r.value1(), r.value2(), r.value3()));
    }

    /**
     * Returns the custodian's jurisdiction units and their descendants.
     *
     * @param custodianId custodian
     * @return unit ids
     */
    public Set<UUID> jurisdictionUnits(final UUID custodianId) {
        return new HashSet<>(dsl.fetch("""
                with recursive units as (
                    select j.admin_unit_id as id from register.jurisdiction j
                     where j.custodian_id = ? and j.valid_to is null
                    union
                    select a.id from register.admin_unit a join units u on a.parent_id = u.id
                     where a.valid_to is null
                )
                select id from units
                """, custodianId)
            .map(r -> r.get(0, UUID.class)));
    }
}
