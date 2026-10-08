package org.ugaddress.register.field;

/**
 * An evidence photo.
 *
 * @param content the bytes
 * @param contentType {@code image/jpeg} or {@code image/png}
 */
public record PhotoDTO(byte[] content, String contentType) {
}
