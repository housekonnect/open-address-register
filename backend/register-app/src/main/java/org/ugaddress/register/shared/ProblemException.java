package org.ugaddress.register.shared;

import java.io.Serial;
import java.util.Objects;
import org.springframework.http.HttpStatus;

/**
 * An error that is returned to the caller as an RFC 9457 problem detail.
 *
 * <p>Messages are shown to API callers: never include personal data or residential entrance coordinates.
 */
public final class ProblemException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final HttpStatus status;
    private final String title;

    private ProblemException(final HttpStatus status, final String title, final String detail) {
        super(detail);
        this.status = Objects.requireNonNull(status, "status");
        this.title = Objects.requireNonNull(title, "title");
    }

    /**
     * Creates a 400 problem.
     *
     * @param detail explanation for the caller
     * @return the exception
     */
    public static ProblemException badRequest(final String detail) {
        return new ProblemException(HttpStatus.BAD_REQUEST, "Bad request", detail);
    }

    /**
     * Creates a 403 problem.
     *
     * @param detail explanation for the caller
     * @return the exception
     */
    public static ProblemException forbidden(final String detail) {
        return new ProblemException(HttpStatus.FORBIDDEN, "Forbidden", detail);
    }

    /**
     * Creates a 404 problem.
     *
     * @param detail explanation for the caller
     * @return the exception
     */
    public static ProblemException notFound(final String detail) {
        return new ProblemException(HttpStatus.NOT_FOUND, "Not found", detail);
    }

    /**
     * Creates a 409 problem.
     *
     * @param detail explanation for the caller
     * @return the exception
     */
    public static ProblemException conflict(final String detail) {
        return new ProblemException(HttpStatus.CONFLICT, "Conflict", detail);
    }

    /**
     * Creates the 422 problem for an {@code Idempotency-Key} reused with a different request.
     *
     * @return the exception
     */
    public static ProblemException idempotencyConflict() {
        return new ProblemException(HttpStatus.UNPROCESSABLE_CONTENT, "Idempotency key reused",
            "The Idempotency-Key was already used with a different request.");
    }

    /**
     * Creates a 501 problem for a contracted operation that is not implemented yet.
     *
     * @param operation the operation id from the contract
     * @return the exception
     */
    public static ProblemException notImplemented(final String operation) {
        return new ProblemException(HttpStatus.NOT_IMPLEMENTED, "Not implemented",
            "The operation '" + operation + "' is part of the contract but not implemented yet.");
    }

    /**
     * Returns the HTTP status.
     *
     * @return the status
     */
    public HttpStatus status() {
        return status;
    }

    /**
     * Returns the problem title.
     *
     * @return the title
     */
    public String title() {
        return title;
    }
}
