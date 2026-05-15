package com.cognilogistic.platform.api;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Map;

/**
 * Structured error payload included in every non-successful API response.
 *
 * <p>Serialised to JSON as part of {@link ApiResponse} when {@code success = false}.
 * Null fields are omitted from the JSON output via {@code @JsonInclude(NON_NULL)}.
 *
 * <p>Components:
 * <ul>
 *   <li>{@code code} — machine-readable error code string (name of the {@link ErrorCode} enum constant);
 *       clients should switch on this value rather than on the HTTP status or message</li>
 *   <li>{@code message} — human-readable description of the error (English; suitable for logging)</li>
 *   <li>{@code details} — optional structured context (e.g., field-level validation errors);
 *       {@code null} when not applicable</li>
 * </ul>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiError(String code, String message, Map<String, Object> details) {

    /**
     * Creates an {@link ApiError} without additional detail context.
     *
     * @param code    the error code enum constant
     * @param message the human-readable error description
     * @return new error record
     */
    public static ApiError of(ErrorCode code, String message) {
        return new ApiError(code.name(), message, null);
    }

    /**
     * Creates an {@link ApiError} with additional structured detail context.
     *
     * @param code    the error code enum constant
     * @param message the human-readable error description
     * @param details key-value pairs providing extra context (e.g., failing field names)
     * @return new error record
     */
    public static ApiError of(ErrorCode code, String message, Map<String, Object> details) {
        return new ApiError(code.name(), message, details);
    }
}
