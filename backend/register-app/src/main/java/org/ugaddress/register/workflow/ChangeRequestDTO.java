package org.ugaddress.register.workflow;

import java.time.OffsetDateTime;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * A stored change request.
 *
 * @param id change request id
 * @param kind {@code correction}, {@code new_object} or {@code retirement}
 * @param state current state
 * @param source {@code console} or {@code field}
 * @param summary summary
 * @param targetObjectId target object
 * @param thoroughfareId street
 * @param adminUnitId admin unit the change belongs to
 * @param custodianId custodian that proposed it
 * @param proposedBy opaque subject of the proposer
 * @param decidedBy opaque subject of the approver or rejecter
 * @param photoObjectKey object-storage key of an attached photo
 * @param createdAt creation time
 */
public record ChangeRequestDTO(UUID id, String kind, String state, String source, String summary,
                               @Nullable UUID targetObjectId, @Nullable UUID thoroughfareId, UUID adminUnitId,
                               UUID custodianId, String proposedBy, @Nullable String decidedBy,
                               @Nullable String photoObjectKey, OffsetDateTime createdAt) {
}
