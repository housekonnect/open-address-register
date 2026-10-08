/**
 * The read side: resolve national IDs and aliases, search and reverse lookup.
 *
 * <p>Public API: the types in this package. Everything under {@code internal} is private to the module.
 */
@ApplicationModule(displayName = "Resolve")
@NullMarked
package org.ugaddress.register.resolve;

import org.jspecify.annotations.NullMarked;
import org.springframework.modulith.ApplicationModule;
