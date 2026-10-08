package org.ugaddress.register.field;

import java.util.Arrays;
import java.util.Objects;

/**
 * An evidence photo.
 *
 * @param content the bytes
 * @param contentType {@code image/jpeg} or {@code image/png}
 */
public record PhotoDTO(byte[] content, String contentType) {

    /**
     * Creates the DTO.
     *
     * @param content bytes
     * @param contentType content type
     */
    public PhotoDTO {
        content = content.clone();
        Objects.requireNonNull(contentType, "contentType");
    }

    @Override
    public byte[] content() {
        return content.clone();
    }

    @Override
    public boolean equals(final Object other) {
        return other instanceof final PhotoDTO that && Arrays.equals(content, that.content)
            && contentType.equals(that.contentType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(Arrays.hashCode(content), contentType);
    }

    @Override
    public String toString() {
        return "PhotoDTO[contentType=" + contentType + ", bytes=" + content.length + "]";
    }
}
