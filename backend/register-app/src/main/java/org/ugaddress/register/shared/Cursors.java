package org.ugaddress.register.shared;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;
import java.util.OptionalLong;
import org.jspecify.annotations.Nullable;

/**
 * Opaque pagination cursors. A cursor wraps a position (a sequence number or a sort key) so clients cannot depend
 * on it.
 */
public final class Cursors {

    private static final String PREFIX = "v1:";
    private static final String KEY_PREFIX = "k1:";

    private Cursors() {
    }

    /**
     * Encodes a position.
     *
     * @param position the position after which the next page starts
     * @return the opaque cursor
     */
    public static String encode(final long position) {
        return Base64.getUrlEncoder().withoutPadding()
            .encodeToString((PREFIX + position).getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Decodes a cursor.
     *
     * @param cursor the cursor from a previous page, or {@code null} for the first page
     * @return the position, or empty for the first page
     * @throws ProblemException with status 400 if the cursor is malformed
     */
    public static OptionalLong decode(final @Nullable String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return OptionalLong.empty();
        }
        try {
            final String text = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            if (!text.startsWith(PREFIX)) {
                throw ProblemException.badRequest("Invalid cursor.");
            }
            return OptionalLong.of(Long.parseLong(text.substring(PREFIX.length())));
        } catch (final IllegalArgumentException e) {
            throw ProblemException.badRequest("Invalid cursor.");
        }
    }

    /**
     * Encodes a textual sort key, for lists ordered by something other than a sequence number.
     *
     * @param key the key after which the next page starts
     * @return the opaque cursor
     */
    public static String encodeKey(final String key) {
        return Base64.getUrlEncoder().withoutPadding()
            .encodeToString((KEY_PREFIX + key).getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Decodes a cursor made by {@link #encodeKey(String)}.
     *
     * @param cursor the cursor from a previous page, or {@code null} for the first page
     * @return the key, or empty for the first page
     * @throws ProblemException with status 400 if the cursor is malformed
     */
    public static Optional<String> decodeKey(final @Nullable String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return Optional.empty();
        }
        try {
            final String text = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            if (!text.startsWith(KEY_PREFIX)) {
                throw ProblemException.badRequest("Invalid cursor.");
            }
            return Optional.of(text.substring(KEY_PREFIX.length()));
        } catch (final IllegalArgumentException e) {
            throw ProblemException.badRequest("Invalid cursor.");
        }
    }
}
