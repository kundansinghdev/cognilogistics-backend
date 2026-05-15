package com.cognilogistic.platform.api;

import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Shared {@code [EXIT]} helper for REST controllers: logs success or domain
 * {@link ApiException} before rethrowing so {@link GlobalExceptionHandler} can
 * build the HTTP error body.
 *
 * <p>Use together with per-endpoint {@code log.info("[ENTRY] …")} lines in controllers.
 * Do not log secrets (PIN, OTP, tokens); use {@link #maskPhone(String)} for phone hints.
 *
 * <p>For raw response bodies (e.g. {@code text/html}), use {@link #withExitLogValue}.
 */
public final class ControllerRequestLogging {

    private ControllerRequestLogging() {}

    /** Last 4 digits only — never log full phone numbers in INFO/WARN lines. */
    public static String maskPhone(String phone) {
        if (phone == null || phone.length() < 4) {
            return "****";
        }
        return "****" + phone.substring(phone.length() - 4);
    }

    /**
     * Runs an action, logs {@code [EXIT] operation | ok} on success, or
     * {@code [EXIT] operation | code | message} on {@link ApiException}, then rethrows.
     */
    public static <T> ApiResponse<T> withExitLog(Class<?> controllerClass, String operation, Supplier<T> action) {
        Logger log = LoggerFactory.getLogger(controllerClass);
        try {
            T result = action.get();
            log.info("[EXIT] {} | ok", operation);
            return ApiResponse.ok(result);
        } catch (ApiException ex) {
            log.warn("[EXIT] {} | {} | {}", operation, ex.getCode(), ex.getMessage());
            throw ex;
        }
    }

    /**
     * Same as {@link #withExitLog} for endpoints that return no body (e.g. HTTP 204).
     */
    public static void withExitLogVoid(Class<?> controllerClass, String operation, Runnable action) {
        Logger log = LoggerFactory.getLogger(controllerClass);
        try {
            action.run();
            log.info("[EXIT] {} | ok", operation);
        } catch (ApiException ex) {
            log.warn("[EXIT] {} | {} | {}", operation, ex.getCode(), ex.getMessage());
            throw ex;
        }
    }

    /**
     * Same exit semantics as {@link #withExitLog} but returns the value directly — for
     * endpoints that bypass {@link ApiResponse} (e.g. {@code text/html} GR/LR documents).
     */
    public static <T> T withExitLogValue(Class<?> controllerClass, String operation, Supplier<T> action) {
        Logger log = LoggerFactory.getLogger(controllerClass);
        try {
            T result = action.get();
            log.info("[EXIT] {} | ok", operation);
            return result;
        } catch (ApiException ex) {
            log.warn("[EXIT] {} | {} | {}", operation, ex.getCode(), ex.getMessage());
            throw ex;
        }
    }
}
