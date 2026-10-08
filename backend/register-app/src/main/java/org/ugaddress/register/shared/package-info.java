/**
 * Cross-cutting concerns: security, the current actor, jurisdiction context, idempotency and RFC 9457 problem details.
 *
 * <p>Public API: the types in this package. Everything under {@code internal} is private to the module.
 */
@ApplicationModule(displayName = "Shared")
@NullMarked
package org.ugaddress.register.shared;

import org.jspecify.annotations.NullMarked;
import org.springframework.modulith.ApplicationModule;
