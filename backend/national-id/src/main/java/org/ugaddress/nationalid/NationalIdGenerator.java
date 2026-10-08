package org.ugaddress.nationalid;

import java.security.SecureRandom;
import java.util.Objects;
import java.util.random.RandomGenerator;

/**
 * Generates national IDs from a random source.
 *
 * <p>Production code uses {@link #secure()}. Tests inject a seeded {@link RandomGenerator} for reproducible output.
 * Uniqueness is not checked here; the register enforces it with a database constraint.
 */
public final class NationalIdGenerator {

    private final RandomGenerator random;

    /**
     * Creates a generator using the given random source.
     *
     * @param random source of randomness for the 10 payload digits
     */
    public NationalIdGenerator(final RandomGenerator random) {
        this.random = Objects.requireNonNull(random, "random");
    }

    /**
     * Creates a generator backed by {@link SecureRandom}.
     *
     * @return a generator suitable for allocating real IDs
     */
    public static NationalIdGenerator secure() {
        return new NationalIdGenerator(new SecureRandom());
    }

    /**
     * Generates a new ID: 10 uniformly random digits plus the Damm check digit.
     *
     * @return a new, valid ID
     */
    public NationalId generate() {
        final StringBuilder payload = new StringBuilder(NationalId.PAYLOAD_LENGTH);
        for (int i = 0; i < NationalId.PAYLOAD_LENGTH; i++) {
            payload.append((char) ('0' + random.nextInt(10)));
        }
        return NationalId.fromPayload(payload.toString());
    }
}
