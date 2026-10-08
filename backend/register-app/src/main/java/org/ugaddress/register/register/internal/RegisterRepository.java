package org.ugaddress.register.register.internal;

import static org.ugaddress.db.generated.Tables.ADDRESS;
import static org.ugaddress.db.generated.Tables.ADDRESSABLE_OBJECT;
import static org.ugaddress.db.generated.Tables.ADDRESSABLE_OBJECT_HISTORY;
import static org.ugaddress.db.generated.Tables.ALIAS;
import static org.ugaddress.db.generated.Tables.POSTCODE_AREA;
import static org.ugaddress.db.generated.Tables.THOROUGHFARE;

import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.function.Function;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.jspecify.annotations.Nullable;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.Point;
import org.springframework.stereotype.Repository;
import org.ugaddress.db.generated.enums.Lifecycle;
import org.ugaddress.db.generated.enums.NationalIdStatus;
import org.ugaddress.db.generated.enums.ObjectKind;
import org.ugaddress.db.generated.tables.records.AddressableObjectRecord;
import org.ugaddress.register.gazetteer.AdminUnitDTO;
import org.ugaddress.register.register.AddressableObjectDTO;
import org.ugaddress.register.register.HistoryPageDTO;

/**
 * jOOQ access to addressable objects, addresses and aliases.
 */
@Repository
public class RegisterRepository {

    private final DSLContext dsl;

    RegisterRepository(final DSLContext dsl) {
        this.dsl = dsl;
    }

    /**
     * Finds a current object by national ID.
     *
     * @param digits the 11 digits
     * @return the object id
     */
    public Optional<UUID> findIdByNationalId(final String digits) {
        return dsl.select(ADDRESSABLE_OBJECT.ID).from(ADDRESSABLE_OBJECT)
            .where(ADDRESSABLE_OBJECT.NATIONAL_ID.eq(digits))
            .and(ADDRESSABLE_OBJECT.VALID_TO.isNull())
            .fetchOptional(ADDRESSABLE_OBJECT.ID);
    }

    /**
     * Finds the object of a current alias.
     *
     * @param system issuing system
     * @param value identifier
     * @return the object id
     */
    public Optional<UUID> findIdByAlias(final String system, final String value) {
        return dsl.select(ALIAS.OBJECT_ID).from(ALIAS)
            .where(ALIAS.SYSTEM.eq(system))
            .and(ALIAS.VALUE.eq(value))
            .and(ALIAS.VALID_TO.isNull())
            .fetchOptional(ALIAS.OBJECT_ID);
    }

    /**
     * Returns the admin unit of an object.
     *
     * @param objectId object id
     * @return the admin unit id
     */
    public Optional<UUID> adminUnitOf(final UUID objectId) {
        return dsl.select(ADDRESSABLE_OBJECT.ADMIN_UNIT_ID).from(ADDRESSABLE_OBJECT)
            .where(ADDRESSABLE_OBJECT.ID.eq(objectId))
            .fetchOptional(ADDRESSABLE_OBJECT.ADMIN_UNIT_ID);
    }

    /**
     * Loads an object with its address, entrances and aliases.
     *
     * @param objectId object id
     * @param partner whether residential entrance coordinates may be returned
     * @param adminUnitChain resolves an admin unit to its chain of names
     * @return the object
     */
    public Optional<AddressableObjectDTO> find(final UUID objectId, final boolean partner,
                                               final Function<UUID, List<AdminUnitDTO>> adminUnitChain) {
        final AddressableObjectRecord object = dsl.selectFrom(ADDRESSABLE_OBJECT)
            .where(ADDRESSABLE_OBJECT.ID.eq(objectId))
            .fetchOne();
        if (object == null) {
            return Optional.empty();
        }
        final boolean isEntrance = object.getKind() == ObjectKind.entrance;
        final UUID addressedObject = isEntrance && object.getParentId() != null ? object.getParentId() : object.getId();

        final AddressableObjectDTO.Address address = dsl
            .select(ADDRESS.ID, ADDRESS.HOUSE_NUMBER, ADDRESS.UNIT, ADDRESS.ADMIN_UNIT_ID, THOROUGHFARE.ID,
                THOROUGHFARE.NAME, POSTCODE_AREA.CODE)
            .from(ADDRESS)
            .join(THOROUGHFARE).on(THOROUGHFARE.ID.eq(ADDRESS.THOROUGHFARE_ID))
            .leftJoin(POSTCODE_AREA).on(POSTCODE_AREA.ID.eq(ADDRESS.POSTCODE_AREA_ID))
            .where(ADDRESS.OBJECT_ID.eq(addressedObject))
            .and(ADDRESS.LIFECYCLE.eq(Lifecycle.active))
            .and(ADDRESS.VALID_TO.isNull())
            .limit(1)
            .fetchOne(r -> new AddressableObjectDTO.Address(r.value1(), r.value2(), r.value3(), r.value5(),
                r.value6(), r.value7(), adminUnitChain.apply(r.value4())));

        final List<AddressableObjectDTO.Entrance> entrances = dsl.selectFrom(ADDRESSABLE_OBJECT)
            .where(ADDRESSABLE_OBJECT.PARENT_ID.eq(object.getId()))
            .and(ADDRESSABLE_OBJECT.KIND.eq(ObjectKind.entrance))
            .and(ADDRESSABLE_OBJECT.VALID_TO.isNull())
            .orderBy(ADDRESSABLE_OBJECT.MAIN_ENTRANCE.desc(), ADDRESSABLE_OBJECT.NATIONAL_ID)
            .fetch(e -> new AddressableObjectDTO.Entrance(e.getId(), e.getNationalId(),
                e.getNationalIdStatus() == NationalIdStatus.demonstration, e.getResidential(), e.getMainEntrance(),
                visibleLocation(e.getLocation(), e.getResidential(), partner)));

        final List<AddressableObjectDTO.Alias> aliases = dsl.select(ALIAS.SYSTEM, ALIAS.VALUE).from(ALIAS)
            .where(ALIAS.OBJECT_ID.eq(object.getId()))
            .and(ALIAS.VALID_TO.isNull())
            .orderBy(ALIAS.SYSTEM, ALIAS.VALUE)
            .fetch(r -> new AddressableObjectDTO.Alias(r.value1(), r.value2()));

        final Point location = isEntrance
            ? visibleLocation(object.getLocation(), object.getResidential(), partner)
            : (Point) object.getLocation();

        return Optional.of(new AddressableObjectDTO(object.getId(), object.getNationalId(),
            object.getNationalIdStatus() == NationalIdStatus.demonstration, object.getKind().getLiteral(),
            object.getLifecycle().getLiteral(), object.getName(), location, address, entrances, aliases,
            object.getVersion(), object.getValidFrom(), object.getAdminUnitId()));
    }

    /**
     * Loads history rows of an object, newest first.
     *
     * @param objectId object id
     * @param afterSeq only rows older than this sequence number
     * @param limit maximum rows
     * @return the rows
     */
    public List<HistoryRow> history(final UUID objectId, final OptionalLong afterSeq, final int limit) {
        Condition condition = ADDRESSABLE_OBJECT_HISTORY.ID.eq(objectId);
        if (afterSeq.isPresent()) {
            condition = condition.and(ADDRESSABLE_OBJECT_HISTORY.HISTORY_SEQ.lt(afterSeq.getAsLong()));
        }
        return dsl.select(ADDRESSABLE_OBJECT_HISTORY.HISTORY_SEQ, ADDRESSABLE_OBJECT_HISTORY.HISTORY_RECORDED_AT,
                ADDRESSABLE_OBJECT_HISTORY.HISTORY_OPERATION, ADDRESSABLE_OBJECT_HISTORY.VERSION,
                ADDRESSABLE_OBJECT_HISTORY.LIFECYCLE, ADDRESSABLE_OBJECT_HISTORY.VALID_FROM,
                ADDRESSABLE_OBJECT_HISTORY.VALID_TO)
            .from(ADDRESSABLE_OBJECT_HISTORY)
            .where(condition)
            .orderBy(ADDRESSABLE_OBJECT_HISTORY.HISTORY_SEQ.desc())
            .limit(DSL.inline(limit))
            .fetch(r -> new HistoryRow(r.value1(), new HistoryPageDTO.Entry(r.value2(), r.value3(), r.value4(),
                r.value5().getLiteral(), r.value6(), r.value7())));
    }

    private static @Nullable Point visibleLocation(final @Nullable Geometry location, final boolean residential,
                                                   final boolean partner) {
        if (residential && !partner) {
            return null;
        }
        return (Point) location;
    }

    /**
     * A history row with its sequence number, used for cursors.
     *
     * @param seq history sequence number
     * @param entry the entry
     */
    public record HistoryRow(long seq, HistoryPageDTO.Entry entry) {
    }
}
