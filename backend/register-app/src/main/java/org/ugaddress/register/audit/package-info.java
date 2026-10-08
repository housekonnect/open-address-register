/**
 * Append-only, hash-chained audit events. Every write path records an event through {@link org.ugaddress.register.audit.AuditService}.
 *
 * <p>Public API: the types in this package. Everything under {@code internal} is private to the module.
 */
@ApplicationModule(displayName = "Audit")
@NullMarked
package org.ugaddress.register.audit;

import org.jspecify.annotations.NullMarked;
import org.springframework.modulith.ApplicationModule;
