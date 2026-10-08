package org.ugaddress.nationalid;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.SplittableRandom;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Proves the error-detection guarantees of ADR 0005 over 1,000 random IDs.
 */
class NationalIdErrorDetectionTest {

    private static final int SAMPLE_SIZE = 1_000;
    private static final long SEED = 20_261_008L;

    static Stream<NationalId> randomIds() {
        final NationalIdGenerator generator = new NationalIdGenerator(new SplittableRandom(SEED));
        return Stream.generate(generator::generate).limit(SAMPLE_SIZE);
    }

    @ParameterizedTest
    @MethodSource("randomIds")
    void rejectsEverySingleDigitSubstitution(final NationalId id) {
        // GIVEN a valid generated ID
        final char[] original = id.digits().toCharArray();
        assertThat(NationalId.isValid(id.digits())).isTrue();

        for (int position = 0; position < original.length; position++) {
            for (char replacement = '0'; replacement <= '9'; replacement++) {
                if (replacement == original[position]) {
                    continue;
                }
                // WHEN one digit is replaced by a different digit
                final char[] mutated = original.clone();
                mutated[position] = replacement;

                // THEN the result is rejected
                assertThat(NationalId.isValid(new String(mutated)))
                    .as("substitution at position %d with %c", position, replacement)
                    .isFalse();
            }
        }
    }

    @ParameterizedTest
    @MethodSource("randomIds")
    void rejectsEveryAdjacentTransposition(final NationalId id) {
        // GIVEN a valid generated ID
        final char[] original = id.digits().toCharArray();

        for (int position = 0; position < original.length - 1; position++) {
            if (original[position] == original[position + 1]) {
                // Swapping identical digits changes nothing, so there is no error to detect.
                continue;
            }
            // WHEN two adjacent, different digits are swapped
            final char[] mutated = original.clone();
            mutated[position] = original[position + 1];
            mutated[position + 1] = original[position];

            // THEN the result is rejected
            assertThat(NationalId.isValid(new String(mutated)))
                .as("transposition at positions %d and %d", position, position + 1)
                .isFalse();
        }
    }
}
