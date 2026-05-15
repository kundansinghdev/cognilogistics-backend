package com.cognilogistic.platform.api;

import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Spring MVC {@code @RestControllerAdvice} that translates exceptions thrown by controllers
 * and services into consistent {@link ApiResponse} JSON payloads.
 *
 * <p>Handler priority (most specific → least specific):
 * <ol>
 *   <li>{@link ApiException} — domain errors thrown by service classes</li>
 *   <li>{@link MethodArgumentNotValidException} — {@code @Valid} bean-validation failures on request bodies</li>
 *   <li>{@link jakarta.validation.ConstraintViolationException} — {@code @Validated} constraint violations on method params</li>
 *   <li>{@link org.springframework.security.access.AccessDeniedException} — Spring Security authorisation failures</li>
 *   <li>{@link org.springframework.security.authentication.BadCredentialsException} — Spring Security auth failures</li>
 *   <li>{@link HttpMessageNotReadableException} — malformed JSON or type mismatch on {@code @RequestBody}</li>
 *   <li>{@link MissingServletRequestParameterException} — required query / form parameter absent</li>
 *   <li>{@link MethodArgumentTypeMismatchException} — path or query param cannot be converted to the declared type</li>
 *   <li>{@link org.springframework.dao.DataIntegrityViolationException} — duplicate DB keys (race after service checks)</li>
 *   <li>{@link Exception} — catch-all for any unhandled exception (logged as ERROR)</li>
 * </ol>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * Handles domain exceptions ({@link ApiException}) thrown by service classes.
     * The HTTP status and error code are determined by the exception's {@link ErrorCode}.
     *
     * @param ex the domain exception
     * @return a structured API error response with the appropriate HTTP status
     */
    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiResponse<Void>> handleApi(ApiException ex) {
        ApiError err = ApiError.of(ex.getCode(), ex.getMessage(), ex.getDetails());
        return ResponseEntity.status(ex.getCode().status()).body(ApiResponse.fail(err));
    }

    /**
     * Handles {@code @Valid} bean-validation failures on {@code @RequestBody} parameters.
     * Collects all field-level violations into the {@code details.fields} map.
     *
     * @param ex the validation exception containing binding result details
     * @return a 400 VALIDATION_ERROR response with field-level error messages
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException ex) {
        String invalidFields = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getField)
                .distinct()
                .collect(Collectors.joining(","));
        log.warn("[EXIT] request | VALIDATION_ERROR | invalidFields=[{}]", invalidFields);

        Map<String, Object> details = new HashMap<>();
        details.put("fields", ex.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(
                        f -> f.getField(),
                        f -> f.getDefaultMessage() == null ? "invalid" : f.getDefaultMessage(),
                        (a, b) -> a)));
        ApiError err = ApiError.of(ErrorCode.VALIDATION_ERROR, "Validation failed", details);
        return ResponseEntity.status(ErrorCode.VALIDATION_ERROR.status()).body(ApiResponse.fail(err));
    }

    /**
     * Handles {@code @Validated} constraint violations on method-level parameters
     * (path variables, query params, and service-layer arguments).
     *
     * @param ex the constraint violation exception
     * @return a 400 VALIDATION_ERROR response with the violation message
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraint(ConstraintViolationException ex) {
        log.warn("[EXIT] request | VALIDATION_ERROR | {}", ex.getMessage());
        ApiError err = ApiError.of(ErrorCode.VALIDATION_ERROR, ex.getMessage());
        return ResponseEntity.status(ErrorCode.VALIDATION_ERROR.status()).body(ApiResponse.fail(err));
    }

    /**
     * Handles Spring Security {@link AccessDeniedException} thrown when an authenticated
     * user attempts an operation they are not authorised to perform (e.g. a TP_TRANSPORT_MANAGER
     * invoking a TP_ADMIN-only endpoint such as order reassignment BR-05).
     *
     * @param ex the access denied exception
     * @return a 403 FORBIDDEN response
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException ex) {
        ApiError err = ApiError.of(ErrorCode.FORBIDDEN, "Access denied");
        return ResponseEntity.status(ErrorCode.FORBIDDEN.status()).body(ApiResponse.fail(err));
    }

    /**
     * Handles {@link BadCredentialsException} thrown by Spring Security during authentication
     * (typically from {@code AuthenticationManager.authenticate} when the PIN does not match).
     *
     * @param ex the bad credentials exception
     * @return a 401 UNAUTHORIZED response with a generic "Invalid credentials" message
     *         (deliberately vague to avoid user enumeration)
     */
    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiResponse<Void>> handleBadCreds(BadCredentialsException ex) {
        ApiError err = ApiError.of(ErrorCode.UNAUTHORIZED, "Invalid credentials");
        return ResponseEntity.status(ErrorCode.UNAUTHORIZED.status()).body(ApiResponse.fail(err));
    }

    /**
     * Handles unreadable JSON bodies (syntax errors, wrong value types for the target DTO).
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnreadable(HttpMessageNotReadableException ex) {
        log.warn("[EXIT] request | VALIDATION_ERROR | malformed body | {}", ex.getMostSpecificCause().getMessage());
        ApiError err = ApiError.of(ErrorCode.VALIDATION_ERROR, "Malformed JSON request body");
        return ResponseEntity.status(ErrorCode.VALIDATION_ERROR.status()).body(ApiResponse.fail(err));
    }

    /**
     * Required request parameter (query or form) was not supplied.
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingParam(MissingServletRequestParameterException ex) {
        log.warn("[EXIT] request | VALIDATION_ERROR | missing parameter | {}", ex.getParameterName());
        String msg = "Missing required parameter: " + ex.getParameterName();
        ApiError err = ApiError.of(ErrorCode.VALIDATION_ERROR, msg);
        return ResponseEntity.status(ErrorCode.VALIDATION_ERROR.status()).body(ApiResponse.fail(err));
    }

    /**
     * Path variable or request parameter value could not be converted (e.g. non-numeric id for {@code Long}).
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        String name = ex.getName() == null ? "parameter" : ex.getName();
        log.warn("[EXIT] request | VALIDATION_ERROR | type mismatch | {} | value=[{}]", name, ex.getValue());
        String msg = "Invalid value for parameter: " + name;
        ApiError err = ApiError.of(ErrorCode.VALIDATION_ERROR, msg);
        return ResponseEntity.status(ErrorCode.VALIDATION_ERROR.status()).body(ApiResponse.fail(err));
    }

    /**
     * Maps MySQL duplicate-key races (when service-level checks were bypassed or a concurrent insert won)
     * to the same domain codes the API normally returns from {@link com.cognilogistic.order.service.CompanyService}
     * and {@link com.cognilogistic.user.service.OfficeService}.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleDataIntegrity(DataIntegrityViolationException ex) {
        String msg = ex.getMostSpecificCause() != null ? ex.getMostSpecificCause().getMessage() : ex.getMessage();
        log.warn("[EXIT] request | DATA_INTEGRITY | {}", msg == null ? ex.toString() : msg);
        String m = msg == null ? "" : msg;
        if (m.contains("uq_company_tp_gstin")) {
            ApiError err = ApiError.of(ErrorCode.COMPANY_GSTIN_EXISTS,
                    "A company with this GSTIN is already in your Company Master.");
            return ResponseEntity.status(ErrorCode.COMPANY_GSTIN_EXISTS.status()).body(ApiResponse.fail(err));
        }
        if (m.contains("uq_office_tp_code")) {
            ApiError err = ApiError.of(ErrorCode.OFFICE_CODE_EXISTS,
                    "This office code is already in use. Choose a different code.");
            return ResponseEntity.status(ErrorCode.OFFICE_CODE_EXISTS.status()).body(ApiResponse.fail(err));
        }
        ApiError err = ApiError.of(ErrorCode.VALIDATION_ERROR, "This record conflicts with existing data.");
        return ResponseEntity.status(ErrorCode.VALIDATION_ERROR.status()).body(ApiResponse.fail(err));
    }

    /**
     * Catch-all handler for any {@link Exception} not matched by a more specific handler above.
     * Logs the full stack trace at ERROR level so it appears in application logs / Sentry,
     * then returns a generic 500 response to avoid leaking internal details to clients.
     *
     * @param ex the unhandled exception
     * @return a 500 INTERNAL_ERROR response with a generic message
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleAny(Exception ex) {
        log.error("Unhandled exception", ex);
        ApiError err = ApiError.of(ErrorCode.INTERNAL_ERROR, "Internal server error");
        return ResponseEntity.status(ErrorCode.INTERNAL_ERROR.status()).body(ApiResponse.fail(err));
    }
}
