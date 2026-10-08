package org.ugaddress.register;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Photos for capture tests: tiny JPEG-shaped byte arrays and their SHA-256, as the field app computes it.
 */
final class TestPhotos {

    private TestPhotos() {
    }

    /**
     * Returns a small JPEG-shaped photo whose bytes differ by {@code variant}.
     *
     * @param variant distinguishes photos
     * @return the bytes
     */
    static byte[] jpeg(final int variant) {
        return new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, (byte) variant, 2, 3, (byte) 0xFF,
            (byte) 0xD9};
    }

    /**
     * Returns the lower-case hex SHA-256 of some bytes.
     *
     * @param bytes the bytes
     * @return the hash
     */
    static String sha256(final byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (final NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
