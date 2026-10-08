package org.ugaddress.register.audit;

import java.util.Objects;
import java.util.OptionalLong;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.ugaddress.register.audit.internal.AuditRepository;

/**
 * Appends events to the hash-chained audit log. Every write path of the register calls this service in the same
 * transaction as the write, so the change and its audit event commit or roll back together.
 */
@Service
public class AuditService {

    private final AuditRepository repository;

    /**
     * Creates the service.
     *
     * @param repository audit storage
     */
    public AuditService(final AuditRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    /**
     * Appends an event. Must run inside the transaction of the write it records.
     *
     * @param entry the event
     * @return id of the stored event
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public UUID record(final AuditEntryDTO entry) {
        return repository.append(entry);
    }

    /**
     * Verifies the hash chain.
     *
     * @return the sequence number of the first broken event, or empty if the chain is intact
     */
    @Transactional(readOnly = true)
    public OptionalLong verifyChain() {
        return repository.firstBrokenEvent();
    }
}
