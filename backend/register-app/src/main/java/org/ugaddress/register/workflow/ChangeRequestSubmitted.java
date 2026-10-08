package org.ugaddress.register.workflow;

import java.util.UUID;

/**
 * Published after a change request has been stored. Other modules may react to it without depending on the
 * workflow internals.
 *
 * @param changeRequestId the new change request
 * @param adminUnitId admin unit it belongs to
 * @param source {@code console} or {@code field}
 */
public record ChangeRequestSubmitted(UUID changeRequestId, UUID adminUnitId, String source) {
}
