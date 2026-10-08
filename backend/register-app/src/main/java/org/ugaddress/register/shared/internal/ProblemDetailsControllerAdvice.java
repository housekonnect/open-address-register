package org.ugaddress.register.shared.internal;

import java.sql.SQLException;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.ugaddress.nationalid.InvalidNationalIdException;
import org.ugaddress.register.shared.ProblemException;

/**
 * Turns exceptions into RFC 9457 problem details. Framework exceptions (validation, missing headers, upload size)
 * are handled by {@link ResponseEntityExceptionHandler}.
 */
@RestControllerAdvice
class ProblemDetailsControllerAdvice extends ResponseEntityExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(ProblemDetailsControllerAdvice.class);

    /** SQLSTATE raised by PostgreSQL when row-level security rejects a row. */
    private static final String INSUFFICIENT_PRIVILEGE = "42501";

    @ExceptionHandler(ProblemException.class)
    ProblemDetail handleProblem(final ProblemException exception) {
        final ProblemDetail problem = ProblemDetail.forStatusAndDetail(exception.status(), exception.getMessage());
        problem.setTitle(exception.title());
        return problem;
    }

    @ExceptionHandler(InvalidNationalIdException.class)
    ProblemDetail handleInvalidNationalId(final InvalidNationalIdException exception) {
        final ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
        problem.setTitle("Invalid national ID");
        return problem;
    }

    @ExceptionHandler(DataAccessException.class)
    ProblemDetail handleDataAccess(final DataAccessException exception) {
        if (INSUFFICIENT_PRIVILEGE.equals(sqlState(exception))) {
            return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN,
                "The change is outside the caller's jurisdiction.");
        }
        LOG.error("Database error", exception);
        return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred.");
    }

    private static @Nullable String sqlState(final Throwable exception) {
        Throwable cause = exception;
        while (cause != null) {
            if (cause instanceof final SQLException sql && sql.getSQLState() != null) {
                return sql.getSQLState();
            }
            cause = cause.getCause();
        }
        return null;
    }
}
