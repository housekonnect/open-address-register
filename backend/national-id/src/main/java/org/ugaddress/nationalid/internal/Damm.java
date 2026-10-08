package org.ugaddress.nationalid.internal;

/**
 * Damm check digit algorithm over decimal digits.
 *
 * <p>Uses the weakly totally anti-symmetric quasigroup of order 10 published by H. Michael Damm (2004). The
 * algorithm detects every single-digit error and every adjacent transposition.
 */
public final class Damm {

    private static final int[][] TABLE = {
        {0, 3, 1, 7, 5, 9, 8, 6, 4, 2},
        {7, 0, 9, 2, 1, 5, 4, 8, 6, 3},
        {4, 2, 0, 6, 8, 7, 1, 3, 5, 9},
        {1, 7, 5, 0, 9, 8, 3, 4, 2, 6},
        {6, 1, 2, 3, 0, 4, 5, 9, 7, 8},
        {3, 6, 7, 4, 2, 0, 9, 5, 8, 1},
        {5, 8, 6, 9, 7, 2, 0, 1, 3, 4},
        {8, 9, 4, 5, 3, 6, 2, 0, 1, 7},
        {9, 4, 3, 8, 6, 1, 7, 2, 0, 5},
        {2, 5, 8, 1, 4, 3, 6, 7, 9, 0},
    };

    private Damm() {
    }

    /**
     * Computes the check digit for a string of ASCII digits.
     *
     * @param digits ASCII digits only
     * @return the check digit, {@code 0} to {@code 9}
     * @throws IllegalArgumentException if {@code digits} contains a non-digit character
     */
    public static int checkDigit(final CharSequence digits) {
        int interim = 0;
        for (int i = 0; i < digits.length(); i++) {
            final char c = digits.charAt(i);
            if (c < '0' || c > '9') {
                throw new IllegalArgumentException("Not a digit at position " + i);
            }
            interim = TABLE[interim][c - '0'];
        }
        return interim;
    }

    /**
     * Checks whether a string of ASCII digits ends with a correct Damm check digit.
     *
     * @param digitsWithCheckDigit ASCII digits, the last one being the check digit
     * @return {@code true} if the check digit is correct
     * @throws IllegalArgumentException if the input contains a non-digit character
     */
    public static boolean isValid(final CharSequence digitsWithCheckDigit) {
        return checkDigit(digitsWithCheckDigit) == 0;
    }
}
