package org.ugaddress.register.workflow.internal;

import static org.ugaddress.db.generated.Tables.CHANGE_REQUEST;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.JSONB;
import org.springframework.stereotype.Repository;
import org.ugaddress.db.generated.enums.ChangeKind;
import org.ugaddress.db.generated.enums.ChangeSource;
import org.ugaddress.db.generated.enums.ChangeState;
import org.ugaddress.db.generated.tables.records.ChangeRequestRecord;
import org.ugaddress.register.workflow.ChangeRequestDTO;
import org.ugaddress.register.workflow.NewChangeRequestDTO;
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
            .set(CHANGE_REQUEST.PROPOSED_BY, proposedBy)
            .returning()
            .fetchSingle();
        return toDto(record);
    }

    /**
     * Finds a change request.
     *
     * @param id id
     * @return the change request
     */
    public Optional<ChangeRequestDTO> find(final UUID id) {
        return dsl.selectFrom(CHANGE_REQUEST).where(CHANGE_REQUEST.ID.eq(id)).fetchOptional(ChangeRequestRepository::toDto);
    }

    /**
     * Records a decision.
     *
     * @param id change request id
     * @param state new state
     * @param decidedBy opaque subject of the decider
     * @return the updated change request
     */
    public ChangeRequestDTO decide(final UUID id, final ChangeState state, final String decidedBy) {
        return toDto(dsl.update(CHANGE_REQUEST)
            .set(CHANGE_REQUEST.STATE, state)
            .set(CHANGE_REQUEST.DECIDED_BY, decidedBy)
            .set(CHANGE_REQUEST.DECIDED_AT, OffsetDateTime.now())
            .where(CHANGE_REQUEST.ID.eq(id))
            .returning()
            .fetchSingle());
    }

    private static ChangeRequestDTO toDto(final ChangeRequestRecord r) {
        return new ChangeRequestDTO(r.getId(), r.getKind().getLiteral(), r.getState().getLiteral(),
            r.getSource().getLiteral(), r.getSummary(), r.getTargetObjectId(), r.getThoroughfareId(),
            r.getAdminUnitId(), r.getCustodianId(), r.getProposedBy(), r.getDecidedBy(), r.getPhotoObjectKey(),
            r.getCreatedAt());
    }
}
