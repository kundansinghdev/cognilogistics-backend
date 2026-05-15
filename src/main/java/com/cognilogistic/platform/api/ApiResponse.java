package com.cognilogistic.platform.api;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Standardised JSON envelope for all API responses in the CogniLogistic backend.
 *
 * <p>Every controller method returns an {@code ApiResponse<T>} so the client always
 * receives a consistent shape regardless of whether the call succeeded or failed:
 * <pre>
 * {
 *   "success": true,
 *   "data": { ... }
 * }
 * </pre>
 * or on error:
 * <pre>
 * {
 *   "success": false,
 *   "error": { "code": "INVALID_TRANSITION", "message": "..." }
 * }
 * </pre>
 *
 * <p>Null fields ({@code data} on errors, {@code error} on successes) are omitted from
 * JSON serialisation via {@code @JsonInclude(NON_NULL)}.
 *
 * <p>Components:
 * <ul>
 *   <li>{@code success} — {@code true} for 2xx responses, {@code false} for errors</li>
 *   <li>{@code data} — the response payload on success; {@code null} on failure</li>
 *   <li>{@code error} — structured error details on failure; {@code null} on success</li>
 * </ul>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(boolean success, T data, ApiError error) {

    /**
     * Creates a successful response wrapping a non-null data payload.
     *
     * @param <T>  the type of the data payload
     * @param data the response body
     * @return a success wrapper
     */
    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, data, null);
    }

    /**
     * Creates a successful response with no data payload (for void operations like logout).
     *
     * @return a success wrapper with {@code null} data
     */
    public static ApiResponse<Void> ok() {
        return new ApiResponse<>(true, null, null);
    }

    /**
     * Creates a failure response wrapping a structured error.
     *
     * @param <T>   the expected data type (always null in failure responses)
     * @param error the error details to include
     * @return a failure wrapper
     */
    public static <T> ApiResponse<T> fail(ApiError error) {
        return new ApiResponse<>(false, null, error);
    }
}
