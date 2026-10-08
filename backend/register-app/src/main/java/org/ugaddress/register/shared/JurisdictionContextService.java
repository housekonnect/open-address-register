package org.ugaddress.register.shared;

import java.util.Collection;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import org.jooq.DSLContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Sets the database session context that row-level security relies on.
 *
 * <p>Writes to register tables succeed only for rows whose admin unit is listed in {@code app.jurisdictions}.
 * The settings are transaction-local ({@code set_config(..., true)}), so they never leak to the next user of a pooled
 * connection.
 */
@Service
public class JurisdictionContextService {

    private final DSLContext dsl;

    /**
     * Creates the service.
     *
     * @param dsl jOOQ context bound to the application's transactional data source
     */
    public JurisdictionContextService(final DSLContext dsl) {
        this.dsl = Objects.requireNonNull(dsl, "dsl");
    }

    /**
     * Applies the jurisdiction and actor to the current transaction. Call before the first write.
     *
     * @param subject opaque subject of the actor, recorded in history rows
     * @param adminUnits admin units the actor may write to
     * @throws IllegalStateException if no transaction is active
     */
    public void enter(final String subject, final Collection<UUID> adminUnits) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("The jurisdiction context requires an active transaction");
        }
        final String units = adminUnits.stream().map(UUID::toString).sorted().collect(Collectors.joining(","));
        dsl.fetch("select set_config('app.jurisdictions', ?, true), set_config('app.subject', ?, true)", units, subject);
    }
}
