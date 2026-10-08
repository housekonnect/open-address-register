package org.ugaddress.nationalid;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Checks the Java implementation against the shared vectors in {@code contracts/test-vectors/national-id.json}.
 */
class NationalIdTest {

    private static final JsonNode VECTORS = loadVectors();

    private static JsonNode loadVectors() {
        final String path = System.getProperty("nationalId.testVectors", "../../contracts/test-vectors/national-id.json");
        return JsonMapper.builder().build().readTree(new File(path));
    }

    private static List<JsonNode> section(final String name) {
        final List<JsonNode> nodes = new ArrayList<>();
        VECTORS.get(name).forEach(nodes::add);
        return nodes;
    }

    static Stream<Arguments> checkDigitVectors() {
        return section("checkDigit").stream()
            .map(v -> Arguments.of(v.get("payload").asString(), v.get("id").asString()));
    }

    static Stream<Arguments> validVectors() {
        return section("valid").stream().map(v -> Arguments.of(
            v.get("id").asString(), v.get("display").asString(), v.get("demonstrationDisplay").asString()));
    }

    static Stream<Arguments> invalidVectors() {
        return section("invalid").stream()
            .map(v -> Arguments.of(v.get("input").asString(), v.get("reason").asString()));
    }

    static Stream<Arguments> parseVectors() {
        return section("parse").stream().map(v -> Arguments.of(v.get("input").asString(), v.get("id").asString()));
    }

    @ParameterizedTest
    @MethodSource("checkDigitVectors")
    void appendsTheDammCheckDigit(final String payload, final String expectedId) {
        // GIVEN a 10-digit payload from the shared vectors
        // WHEN the check digit is appended
        final NationalId id = NationalId.fromPayload(payload);

        // THEN the ID matches the vector
        assertThat(id.digits()).isEqualTo(expectedId);
    }

    @ParameterizedTest
    @MethodSource("validVectors")
    void formatsValidIds(final String digits, final String display, final String demonstrationDisplay) {
        // GIVEN a valid ID
        final NationalId id = NationalId.of(digits);

        // WHEN it is formatted
        // THEN the display forms match the vectors and parse back to the same ID
        assertThat(id.format()).isEqualTo(display);
        assertThat(id.formatAsDemonstration()).isEqualTo(demonstrationDisplay);
        assertThat(NationalId.parse(display)).isEqualTo(id);
        assertThat(NationalId.parse(demonstrationDisplay)).isEqualTo(id);
    }

    @ParameterizedTest
    @MethodSource("invalidVectors")
    void rejectsInvalidInput(final String input, final String reason) {
        // GIVEN an invalid input from the shared vectors
        // WHEN it is validated or parsed
        // THEN it is rejected
        assertThat(NationalId.isValid(input)).as(reason).isFalse();
        assertThat(NationalId.tryParse(input)).as(reason).isEmpty();
        assertThatThrownBy(() -> NationalId.parse(input)).as(reason).isInstanceOf(InvalidNationalIdException.class);
    }

    @ParameterizedTest
    @MethodSource("parseVectors")
    void parsesSpacesDashesAndDemonstrationMarker(final String input, final String expectedDigits) {
        // GIVEN user input with separators or a demonstration marker
        // WHEN it is parsed
        final NationalId id = NationalId.parse(input);

        // THEN separators and the marker are ignored
        assertThat(id.digits()).isEqualTo(expectedDigits);
    }

    @Test
    void generatesValidIdsReproduciblyFromAnInjectedRandomSource() {
        // GIVEN two generators with the same seed
        final NationalIdGenerator first = new NationalIdGenerator(new SplittableRandom(42L));
        final NationalIdGenerator second = new NationalIdGenerator(new SplittableRandom(42L));

        // WHEN both generate an ID
        final NationalId a = first.generate();
        final NationalId b = second.generate();

        // THEN the IDs are equal and valid
        assertThat(a).isEqualTo(b);
        assertThat(NationalId.isValid(a.digits())).isTrue();
    }

    @Test
    void errorMessagesNeverEchoTheInput() {
        // GIVEN an input that looks like a mistyped ID
        final String input = "48210937619";

        // WHEN it is parsed
        // THEN the exception message does not contain the input
        assertThatThrownBy(() -> NationalId.parse(input))
            .isInstanceOf(InvalidNationalIdException.class)
            .message().doesNotContain(input);
    }
}
