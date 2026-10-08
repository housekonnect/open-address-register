package org.ugaddress.register.workflow.internal;

import static org.ugaddress.db.generated.Tables.CHANGE_REQUEST;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.JSONB;
import org.jspecify.annotations.Nullable;
import org.locationtech.jts.geom.Point;
import org.springframework.stereotype.Repository;
import org.ugaddress.db.generated.enums.ChangeKind;
import org.ugaddress.db.generated.enums.ChangeSource;
import org.ugaddress.db.generated.enums.ChangeState;
import org.ugaddress.db.generated.tables.records.ChangeRequestRecord;
import org.ugaddress.register.workflow.ChangeRequestDTO;
import org.ugaddress.register.shared.GeoJson;
import org.ugaddress.register.workflow.NewChangeRequestDTO;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * jOOQ access to change requests. Inserts and updates are subject to row-level security.
 */
@Repository
public class ChangeRequestRepository {

    private final DSLContext dsl;
    private final JsonMapper jsonMapper;

    ChangeRequestRepository(final DSLContext dsl, final JsonMapper jsonMapper) {
        this.dsl = dsl;
        this.jsonMapper = jsonMapper;
    }

    /**
     * Inserts a change request in state {@code submitted}.
     *
     * @param draft the change
     * @param adminUnitId admin unit it belongs to
     * @param custodianId proposing custodian
     * @param proposedBy opaque subject of the proposer
     * @return the stored change request
     */
    public ChangeRequestDTO insert(final NewChangeRequestDTO draft, final UUID adminUnitId, final UUID custodianId,
                                   final String proposedBy) {
        final Map<String, Object> proposal = new LinkedHashMap<>();
        if (draft.proposedLocation() != null) {
            proposal.put("location", Map.of("type", "Point",
                "coordinates", List.of(draft.proposedLocation().getX(), draft.proposedLocation().getY())));
        }
        if (draft.proposedHouseNumber() != null) {
            proposal.put("houseNumber", draft.proposedHouseNumber());
        }
        final ChangeRequestRecord record = dsl.insertInto(CHANGE_REQUEST)
            .set(CHANGE_REQUEST.KIND, ChangeKind.lookupLiteral(draft.kind()))
            .set(CHANGE_REQUEST.SOURCE, ChangeSource.lookupLiteral(draft.source()))
            .set(CHANGE_REQUEST.SUMMARY, draft.summary())
            .set(CHANGE_REQUEST.TARGET_OBJECT_ID, draft.targetObjectId())
            .set(CHANGE_REQUEST.THOROUGHFARE_ID, draft.thoroughfareId())
            .set(CHANGE_REQUEST.ADMIN_UNIT_ID, adminUnitId)
            .set(CHANGE_REQUEST.CUSTODIAN_ID, custodianId)
            .set(CHANGE_REQUEST.PROPOSAL, JSONB.valueOf(jsonMapper.writeValueAsString(proposal)))
            .set(CHANGE_REQUEST.PHOTO_OBJECT_KEY, draft.photoObjectKey())
            .set(CHANGE_REQUEST.PHOTO_SHA256, draft.photoSha256())
            .set(CHANGE_REQUEST.LOCATION_MOCKED, draft.locationMocked())
            .set(CHANGE_REQUEST.PROPOSED_BY, proposedBy)
            .returning()
            .fetchSingle();
        return toDto(record);
    }

    /**
     * Lists change requests of some admin units, oldest first.
     *
     * @param adminUnitIds the admin units (a jurisdiction)
     * @param state only this state, or {@code null} for all
     * @param after position of the last item of the previous page, or {@code null}
     * @param limit maximum rows
     * @return the change requests
     */
    public List<ChangeRequestDTO> list(final Set<UUID> adminUnitIds, final @Nullable ChangeState state,
                                       final @Nullable InboxPosition after, final int limit) {
        Condition condition = CHANGE_REQUEST.ADMIN_UNIT_ID.in(adminUnitIds);
        if (state != null) {
            condition = condition.and(CHANGE_REQUEST.STATE.eq(state));
        }
        if (after != null) {
            condition = condition.and(CHANGE_REQUEST.CREATED_AT.gt(after.createdAt())
                .or(CHANGE_REQUEST.CREATED_AT.eq(after.createdAt()).and(CHANGE_REQUEST.ID.gt(after.id()))));
        }
        return dsl.selectFrom(CHANGE_REQUEST)
            .where(condition)
            .orderBy(CHANGE_REQUEST.CREATED_AT, CHANGE_REQUEST.ID)
            .limit(limit)
            .fetch(this::toDto);
    }

    /**
     * Finds a change request.
     *
     * @param id id
     * @return the change request
     */
    public Optional<ChangeRequestDTO> find(final UUID id) {
        return dsl.selectFrom(CHANGE_REQUEST).where(CHANGE_REQUEST.ID.eq(id)).fetchOptional(this::toDto);
    }

    /**
     * Records a decision.
     *
     * @param id change request id
     * @param state new state
     * @param decidedBy opaque subject of the decider
     * @param reason written reason (required for a return), or {@code null}
     * @return the updated change request
     */
    public ChangeRequestDTO decide(final UUID id, final ChangeState state, final String decidedBy,
                                   final @Nullable String reason) {
        return toDto(dsl.update(CHANGE_REQUEST)
            .set(CHANGE_REQUEST.STATE, state)
            .set(CHANGE_REQUEST.DECIDED_BY, decidedBy)
            .set(CHANGE_REQUEST.DECIDED_AT, OffsetDateTime.now())
            .set(CHANGE_REQUEST.DECISION_REASON, reason)
            .where(CHANGE_REQUEST.ID.eq(id))
            .returning()
            .fetchSingle());
    }

    private ChangeRequestDTO toDto(final ChangeRequestRecord r) {
        final JsonNode proposal = jsonMapper.readTree(r.getProposal().data());
        final JsonNode houseNumber = proposal.path("houseNumber");
        final JsonNode coordinates = proposal.path("location").path("coordinates");
        final Point location = coordinates.size() == 2
            ? GeoJson.point(coordinates.get(0).asDouble(), coordinates.get(1).asDouble())
            : null;
        return new ChangeRequestDTO(r.getId(), r.getKind().getLiteral(), r.getState().getLiteral(),
            r.getSource().getLiteral(), r.getSummary(), r.getTargetObjectId(), r.getThoroughfareId(),
            r.getAdminUnitId(), r.getCustodianId(), r.getProposedBy(), r.getDecidedBy(), r.getPhotoObjectKey(),
            r.getCreatedAt(), houseNumber.isString() ? houseNumber.asString() : null, location, r.getDecidedAt(),
            r.getDecisionReason(), r.getPhotoSha256(), r.getLocationMocked());
    }

    /**
     * Position in the inbox: creation time, then id.
     *
     * @param createdAt creation time of the last item
     * @param id id of the last item
     */
    public record InboxPosition(OffsetDateTime createdAt, UUID id) {
    }
}
