package org.ugaddress.register.audit;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * An event to append to the audit chain.
 *
 * <p>The payload must not contain personal data or residential entrance coordinates.
 *
 * @param actor opaque subject of the actor, or a {@code system:*} identifier
 * @param custodianId custodian on whose behalf the actor acted, if any
 * @param action what happened, e.g. {@code change_request.submitted}
 * @param entityType type of the affected entity, e.g. {@code change_request}
 * @param entityId id of the affected entity, if any
 * @param payload additional, non-personal facts
 */
public record AuditEntryDTO(String actor, @Nullable UUID custodianId, String action, String entityType,
                            @Nullable UUID entityId, Map<String, Object> payload) {

    /**
     * Creates an entry.
     *
     * @param actor actor
     * @param custodianId custodian
     * @param action action
     * @param entityType entity type
     * @param entityId entity id
     * @param payload payload
     */
    public AuditEntryDTO {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(entityType, "entityType");
        payload = Map.copyOf(payload);
    }
}
