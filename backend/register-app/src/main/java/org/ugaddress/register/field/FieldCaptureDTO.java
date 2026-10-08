package org.ugaddress.register.field;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Result of receiving a field capture. In this version a capture is stored as a change request, so both ids match.
 *
 * @param id capture id
 * @param changeRequestId change request created for the capture
 * @param photoStored whether the photo is in object storage
 * @param receivedAt when the register received the capture
 */
public record FieldCaptureDTO(UUID id, UUID changeRequestId, boolean photoStored, OffsetDateTime receivedAt) {
}
