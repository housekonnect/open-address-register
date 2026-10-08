package org.ugaddress.nationalid;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import org.ugaddress.nationalid.internal.Damm;

/**
 * A national address ID: 10 random digits followed by one Damm check digit.
 *
 * <p>Instances are always valid. Create them with {@link #parse(CharSequence)}, {@link #of(String)} or a
 * {@link NationalIdGenerator}.
 *
 * @param digits the 11 digits of the ID, without separators
 */
public record NationalId(String digits) {

    /** Number of random payload digits. */
    public static final int PAYLOAD_LENGTH = 10;

    /** Total number of digits, including the check digit. */
    public static final int LENGTH = PAYLOAD_LENGTH + 1;

    /** Marker that prefixes the display form of demonstration IDs. */
    public static final String DEMONSTRATION_MARKER = "DEMO";

    /**
     * Creates an ID from exactly 11 digits.
     *
     * @param digits the 11 digits of the ID, without separators
     * @throws InvalidNationalIdException if {@code digits} is not 11 ASCII digits with a correct check digit
     */
    public NationalId {
        Objects.requireNonNull(digits, "digits");
        requireValid(digits);
    }

    /**
     * Creates an ID from exactly 11 digits without separators.
     *
     * @param digits the 11 digits of the ID
     * @return the ID
     * @throws InvalidNationalIdException if {@code digits} is not a valid ID
     */
    public static NationalId of(final String digits) {
        return new NationalId(digits);
    }

    /**
     * Creates an ID from a 10-digit payload by appending its Damm check digit.
     *
     * @param payload 10 ASCII digits
     * @return the ID
     * @throws InvalidNationalIdException if {@code payload} is not 10 ASCII digits
     */
    public static NationalId fromPayload(final String payload) {
        Objects.requireNonNull(payload, "payload");
        if (payload.length() != PAYLOAD_LENGTH || !isAsciiDigits(payload)) {
            throw new InvalidNationalIdException("Payload must be exactly " + PAYLOAD_LENGTH + " digits");
        }
        return new NationalId(payload + Damm.checkDigit(payload));
    }

    /**
     * Parses user input. Spaces and dashes are ignored, as is a leading {@value #DEMONSTRATION_MARKER} marker
     * (case-insensitive), so every display form parses back to its ID.
     *
     * @param input user input such as {@code 4821 093 7618} or {@code 4821-093-7618}
     * @return the ID
     * @throws InvalidNationalIdException if the input is not a valid ID
     */
    public static NationalId parse(final CharSequence input) {
        Objects.requireNonNull(input, "input");
        return new NationalId(normalize(input));
    }

    /**
     * Parses user input like {@link #parse(CharSequence)}, returning an empty result instead of throwing.
     *
     * @param input user input
     * @return the ID, or empty if the input is not a valid ID
     */
    public static Optional<NationalId> tryParse(final CharSequence input) {
        try {
            return Optional.of(parse(input));
        } catch (final InvalidNationalIdException e) {
            return Optional.empty();
        }
    }

    /**
     * Checks whether user input is a valid ID, accepting the same forms as {@link #parse(CharSequence)}.
     *
     * @param input user input
     * @return {@code true} if the input parses to a valid ID
     */
    public static boolean isValid(final CharSequence input) {
        return tryParse(input).isPresent();
    }

    /**
     * Returns the display form, grouped 4-3-4, for example {@code 4821 093 7618}.
     *
     * @return the display form
     */
    public String format() {
        return digits.substring(0, 4) + ' ' + digits.substring(4, 7) + ' ' + digits.substring(7);
    }

    /**
     * Returns the display form of a demonstration ID, for example {@code DEMO 4821 093 7618}.
     *
     * @return the display form with the {@value #DEMONSTRATION_MARKER} marker
     */
    public String formatAsDemonstration() {
        return DEMONSTRATION_MARKER + ' ' + format();
    }

    /**
     * Returns the display form.
     *
     * @return the same as {@link #format()}
     */
    @Override
    public String toString() {
        return format();
    }

    private static String normalize(final CharSequence input) {
        String text = input.toString().strip();
        if (text.toUpperCase(Locale.ROOT).startsWith(DEMONSTRATION_MARKER)) {
            text = text.substring(DEMONSTRATION_MARKER.length());
        }
        final StringBuilder digits = new StringBuilder(LENGTH);
        for (int i = 0; i < text.length(); i++) {
            final char c = text.charAt(i);
            if (c == ' ' || c == '-') {
                continue;
            }
            if (c < '0' || c > '9') {
                throw new InvalidNationalIdException("Only digits, spaces and dashes are allowed");
            }
            digits.append(c);
        }
        return digits.toString();
    }

    private static void requireValid(final String digits) {
        if (digits.length() != LENGTH) {
            throw new InvalidNationalIdException("A national ID has exactly " + LENGTH + " digits");
        }
        if (!isAsciiDigits(digits)) {
            throw new InvalidNationalIdException("A national ID contains only digits");
        }
        if (!Damm.isValid(digits)) {
            throw new InvalidNationalIdException("Check digit does not match");
        }
    }

    private static boolean isAsciiDigits(final String value) {
        for (int i = 0; i < value.length(); i++) {
            final char c = value.charAt(i);
            if (c < '0' || c > '9') {
                return false;
            }
        }
        return true;
    }
}
