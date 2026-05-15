package com.cognilogistic.auth.dto;

/**
 * Response body for {@code POST /api/v1/auth/verify-otp}.
 *
 * <p><strong>Three optional payloads — FE branches on which is non-null:</strong>
 * <ul>
 *   <li>{@link #session} — populated only on the now-deprecated OTP-only path.
 *       See {@link #session} Javadoc for the unified-contract supersession.</li>
 *   <li>{@link #shadow} — populated when the verified phone matches a
 *       {@code users.is_shadow=TRUE} row pre-created by another tenant.
 *       See {@link ShadowContext} for full semantics.</li>
 *   <li>{@link #tempToken} — the dominant case. PIN-flow signup or PIN reset.</li>
 * </ul>
 *
 * <p>Verify-OTP serves two distinct flows depending on the user's role:
 *
 * <ul>
 *   <li><strong>PIN-flow</strong> ({@code TP_ADMIN}, {@code TP_TRANSPORT_MANAGER},
 *       {@code PARTNER_TP}, plus first-login auto-create): returns a short-lived
 *       {@code tempToken} the client exchanges at {@code /auth/setup-pin}
 *       (first-time) or uses to navigate to {@code /enter-pin} (returning user).
 *       Wire shape:
 *       <pre>{@code
 *       { "tempToken": "ey...", "isNewUser": true,  "session": null }
 *       }</pre>
 *   </li>
 *   <li><strong>OTP-only</strong> ({@code COGNILOGISTIC_ADMIN}): server mints
 *       full tokens immediately and returns them via {@link #session} — no PIN
 *       step exists for this role. Front-end stores the tokens and routes
 *       directly to {@code /app/admin}. Wire shape:
 *       <pre>{@code
 *       { "tempToken": null, "isNewUser": false,
 *         "session": { "accessToken": "...", "refreshToken": "...",
 *                      "accessTokenExpiresAt": "...", "refreshTokenExpiresAt": "...",
 *                      "onboardingStep": 3, "user": { ...AuthUser... } } }
 *       }</pre>
 *   </li>
 * </ul>
 *
 * <p>Front-end branches on {@code data.session != null}: when populated, store
 * tokens + skip the PIN screen. When null, take the existing path.
 *
 * <p>Note: {@code CUSTOMER}-role authentication uses a separate endpoint
 * ({@code /api/v1/portal/auth/verify-otp}) and is not served by this DTO.
 *
 * @param tempToken single-use token to exchange at /setup-pin (PIN-flow only).
 *                  {@code null} for OTP-only roles.
 * @param isNewUser {@code true} when the phone had no prior TP_ADMIN account
 *                  (PIN-flow auto-creation path). Always {@code false} for
 *                  OTP-only roles since admin accounts are pre-seeded.
 * @param session   complete login response (tokens + user) for OTP-only roles.
 *                  {@code null} for PIN-flow.
 */
public record VerifyOtpResponse(
        String tempToken,
        boolean isNewUser,
        LoginResponse session,
        ShadowContext shadow) {

    /** Convenience for the PIN-flow path — preserves the legacy 2-arg constructor for callers. */
    public static VerifyOtpResponse pinFlow(String tempToken, boolean isNewUser) {
        return new VerifyOtpResponse(tempToken, isNewUser, null, null);
    }

    /**
     * Convenience for the PIN-flow path with shadow context attached. The phone
     * matched a {@code users.is_shadow=TRUE} row, so the FE skips role-pick and
     * pre-fills the business-name field with {@link ShadowContext#orgName()}.
     * Tokens still come via the standard {@code setup-pin} step (no session
     * minted on verify; shadow row's PIN is set during activation).
     */
    public static VerifyOtpResponse pinFlowWithShadow(String tempToken, boolean isNewUser, ShadowContext shadow) {
        return new VerifyOtpResponse(tempToken, isNewUser, null, shadow);
    }

    /**
     * Convenience for the OTP-only path — server-minted tokens, no temp token.
     *
     * @deprecated The OTP-only branch was superseded 2026-05-08 PM by the unified
     *     auth contract (BACKEND_GAPS §1.7) — every role now goes through PIN
     *     login on returning sessions and OTP+PIN on signup. The branch is
     *     functionally inert because the FE no longer drives it. Slated for
     *     removal one full release cycle after §1.7 stabilises.
     */
    @Deprecated(since = "2026-05-08", forRemoval = true)
    public static VerifyOtpResponse otpOnly(LoginResponse session) {
        return new VerifyOtpResponse(null, false, session, null);
    }
}
