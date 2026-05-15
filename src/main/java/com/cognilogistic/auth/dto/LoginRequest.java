package com.cognilogistic.auth.dto;

import com.cognilogistic.auth.model.DeviceType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request body for {@code POST /api/v1/auth/login}.
 *
 * <p>Components:
 * <ul>
 *   <li>{@code phone} — registered phone (E.164 or local 10–15 digit)</li>
 *   <li>{@code pin} — the user's 4-digit PIN</li>
 *   <li>{@code deviceId} — stable client identifier; scopes refresh-token rotation
 *       (one active refresh per device)</li>
 *   <li>{@code deviceType} — WEB or MOBILE; controls access-token TTL</li>
 *   <li>{@code roleHint} — optional disambiguator the front-end's role-picker landing
 *       page sends so an account that exists in multiple registries (e.g. a phone
 *       used by both a TP staff member and a customer record) is routed to the
 *       expected workspace. Accepted-and-ignored today; future enrichment when
 *       Partner / Customer registries are populated. (BACKEND_GAPS §1.2)</li>
 * </ul>
 *
 * <p>{@code roleHint} is a free-text string rather than an enum because the FE
 * uses values like {@code "COGNI_ADMIN"} / {@code "PARTNER"} / {@code "CUSTOMER"}
 * and we don't want to fail a deserialisation if the FE adds a new value before
 * the backend ships an updated enum.
 *
 * @param phone      phone number (10–15 digits, optional leading +)
 * @param pin        4-digit numeric PIN
 * @param deviceId   stable device identifier
 * @param deviceType WEB or MOBILE (uppercase enum name)
 * @param roleHint   optional role hint for routing — tolerated, currently ignored
 */
public record LoginRequest(
        @NotBlank @Pattern(regexp = "\\+?\\d{10,15}", message = "phone must be 10–15 digits, optional leading +") String phone,
        @NotBlank @Pattern(regexp = "\\d{4}", message = "PIN must be exactly 4 digits") String pin,
        @NotBlank @Size(max = 255, message = "deviceId must be at most 255 characters") String deviceId,
        @NotNull DeviceType deviceType,
        @Size(max = 64, message = "roleHint must be at most 64 characters") String roleHint
) {}
