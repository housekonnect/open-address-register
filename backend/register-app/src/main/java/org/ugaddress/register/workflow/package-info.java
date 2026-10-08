/**
 * Change requests and their state machine, including the four-eyes rule: a proposer never decides their own request.
 *
 * <p>Public API: the types in this package. Everything under {@code internal} is private to the module.
 */
@ApplicationModule(displayName = "Workflow")
@NullMarked
package org.ugaddress.register.workflow;

import org.jspecify.annotations.NullMarked;
import org.springframework.modulith.ApplicationModule;
