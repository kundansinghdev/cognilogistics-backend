package com.cognilogistic.platform.api;

import lombok.Getter;

import java.util.Map;

/**
 * Domain exception used throughout the CogniLogistic service layer to signal
 * business-rule violations and client errors that should be surfaced as structured
 * HTTP responses rather than 500 Internal Server Errors.
 *
 * <p>Thrown by service classes (e.g., {@link com.cognilogistic.order.service.OrderService},
 * {@link com.cognilogistic.auth.service.AuthService}) and caught by
 * {@link GlobalExceptionHandler}, which maps it to the appropriate HTTP status code
 * defined on the {@link ErrorCode} enum.
 *
 * <p>Extends {@link RuntimeException} so callers do not need to declare checked exceptions,
 * keeping service method signatures clean.
 */
@Getter
public class ApiException extends RuntimeException {

    /** The machine-readable error category, which determines the HTTP response status. */
    private final ErrorCode code;

    /**
     * Optional structured context for this error (e.g., failing field names).
     * {@code null} when no additional detail is available.
     */
    private final Map<String, Object> details;

    /**
     * Creates an exception without additional detail context.
     *
     * @param code    the error category
     * @param message the human-readable error description (included in the JSON response)
     */
    public ApiException(ErrorCode code, String message) {
        super(message);
        this.code = code;
        this.details = null;
    }

    /**
     * Creates an exception with additional structured detail context.
     *
     * @param code    the error category
     * @param message the human-readable error description
     * @param details key-value pairs providing extra context (e.g., {@code {"field": "vehicleRegistration"}})
     */
    public ApiException(ErrorCode code, String message, Map<String, Object> details) {
        super(message);
        this.code = code;
        this.details = details;
    }
}
