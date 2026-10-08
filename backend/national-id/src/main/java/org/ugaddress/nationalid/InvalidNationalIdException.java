package org.ugaddress.nationalid;

import java.io.Serial;

/**
 * Thrown when a value is not a well-formed national ID with a correct check digit.
 *
 * <p>The message never contains the rejected input, so it is safe to log and to return to API callers.
 */
public final class InvalidNationalIdException extends IllegalArgumentException {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * Creates the exception.
     *
     * @param reason why the input was rejected, without echoing the input
     */
    public InvalidNationalIdException(final String reason) {
        super(reason);
    }
}
