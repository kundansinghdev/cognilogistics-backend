package com.cognilogistic.platform.admin;

import com.cognilogistic.auth.model.DeviceType;

/**
 * Request body for {@code POST /api/v1/admin/impersonate/{sessionId}/exit}
 * (BACKEND_GAPS §7).
 *
 * <p>Body may be empty {@code {}} — the exit flow re-derives the synthetic
 * impersonation device id from the calling admin's JWT user id plus the
 * {@code sessionId} on the path, the same way
 * {@link AdminService#impersonate(com.cognilogistic.auth.security.AuthPrincipal, ImpersonateRequest)}
 * constructed it on entry. Keeping {@code deviceId} off the wire prevents a
 * hostile admin from passing someone else's device key.
 *
 * <p>{@code deviceType} is optional for symmetry with the start request — if
 * the FE sent {@code MOBILE} on impersonate-start it should send the same
 * value on exit so the new admin token is scoped to the matching client.
 * Defaults to {@code WEB} when omitted.
 *
 * @param deviceType optional WEB / MOBILE override; defaults to WEB server-side
 */
public record ExitImpersonationRequest(
        DeviceType deviceType
) {}
