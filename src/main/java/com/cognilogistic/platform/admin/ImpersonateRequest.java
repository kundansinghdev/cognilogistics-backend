package com.cognilogistic.platform.admin;

import com.cognilogistic.auth.model.DeviceType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request body for {@code POST /api/v1/admin/impersonate} (BACKEND_GAPS §7).
 *
 * <p>Identifies the target account the admin wants to enter:
 * <ul>
 *   <li>{@code targetType="TP"} — {@link #targetId} is the {@code tp_accounts.id}.</li>
 *   <li>{@code targetType="PARTNER"} or {@code "CUSTOMER"} — {@link #targetId} is
 *       the {@code users.id}.</li>
 * </ul>
 *
 * <p><strong>Note: no {@code deviceId} field.</strong> The impersonation token's
 * device scope is synthesised server-side from the calling admin's JWT user id
 * plus the freshly-generated audit-log session id:
 * {@code "imp-" + adminUserId + "-" + sessionId} — see
 * {@link AdminService#impersonate(com.cognilogistic.auth.security.AuthPrincipal, ImpersonateRequest)}.
 * Keeping the device id off the wire prevents a hostile client from spoofing
 * someone else's device key, and the {@code imp-} prefix lets ops filter
 * impersonation rows out of normal-user analytics with
 * {@code WHERE device_id LIKE 'imp-%'}.
 *
 * <p>{@code deviceType} stays on the wire because mobile-admin is a plausible
 * future client; we accept the optional override so that lands without a
 * backend deploy. Default is {@code WEB} (the current admin portal is web-only).
 *
 * @param targetType  one of {@code "TP"} / {@code "PARTNER"} / {@code "CUSTOMER"}
 * @param targetId    UUID of the target account (matches {@code targetType}'s table)
 * @param reason      optional reason text recorded on the audit log row (≤500 chars)
 * @param deviceType  optional WEB / MOBILE override; defaults to WEB server-side
 */
public record ImpersonateRequest(

        @NotBlank
        String targetType,

        @NotBlank
        String targetId,

        @Size(max = 500)
        String reason,

        DeviceType deviceType
) {}
