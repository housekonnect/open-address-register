package org.ugaddress.register.audit.internal;

import java.util.OptionalLong;
import java.util.UUID;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;
import org.ugaddress.register.audit.AuditEntryDTO;
import tools.jackson.databind.json.JsonMapper;

/**
 * Stores audit events through the database function {@code register.append_audit_event}, which serialises appends
 * and computes the SHA-256 chain. The application role cannot insert, update or delete audit rows directly.
 */
@Repository
public class AuditRepository {

    private final DSLContext dsl;
    private final JsonMapper jsonMapper;

    AuditRepository(final DSLContext dsl, final JsonMapper jsonMapper) {
        this.dsl = dsl;
        this.jsonMapper = jsonMapper;
    }

    /**
     * Appends an event.
     *
     * @param entry the event
     * @return the event id
     */
    public UUID append(final AuditEntryDTO entry) {
        final String payload = jsonMapper.writeValueAsString(entry.payload());
        return dsl.fetchSingle(
                "select id from register.append_audit_event(?::text, ?::uuid, ?::text, ?::text, ?::uuid, ?::jsonb)",
                entry.actor(), entry.custodianId(), entry.action(), entry.entityType(), entry.entityId(), payload)
            .get(0, UUID.class);
    }

    /**
     * Verifies the chain.
     *
     * @return the first broken sequence number, or empty if intact
     */
    public OptionalLong firstBrokenEvent() {
        final Long seq = dsl.fetchSingle("select register.verify_audit_chain()").get(0, Long.class);
        return seq == null ? OptionalLong.empty() : OptionalLong.of(seq);
    }
}
