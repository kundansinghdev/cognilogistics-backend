package com.cognilogistic.auth.dto;

import java.time.Instant;

/**
 * Response body for {@code POST /api/v1/auth/login} (and the analogous
 * {@code /setup-pin} / {@code /reset-pin/set} flows).
 *
 * <p>Combines the {@link TokenPair} fields with onboarding context and the
 * authenticated identity payload. The front-end uses {@link #user} to populate
 * its global identity store, drive the welcome-banner / profile-pending UI
 * (BACKEND_GAPS §1.4), and route plan-aware default landing.
 *
 * <p><strong>Wire shape:</strong>
 * <pre>{@code
 * {
 *   "accessToken": "ey...",
 *   "refreshToken": "...",
 *   "accessTokenExpiresAt": "2026-05-08T11:00:00Z",
 *   "refreshTokenExpiresAt": "2026-06-07T10:00:00Z",
 *   "onboardingStep": 3,
 *   "user": { ... AuthUser ... }
 * }
 * }</pre>
 *
 * @param accessToken            short-lived JWT
 * @param refreshToken           long-lived opaque refresh token (single-use, rotated on /refresh)
 * @param accessTokenExpiresAt   absolute UTC expiry of the access token
 * @param refreshTokenExpiresAt  absolute UTC expiry of the refresh token
 * @param onboardingStep         legacy mirror of {@link AuthUser#onboardingStep()} kept on the
 *                               envelope so older FE callers don't have to dig into {@link #user};
 *                               will be removed once the FE consumes {@code user.onboardingStep}
 * @param user                   identity payload (BACKEND_GAPS §1.4)
 */
public record LoginResponse(
        String accessToken,
        String refreshToken,
        Instant accessTokenExpiresAt,
        Instant refreshTokenExpiresAt,
        Integer onboardingStep,
        AuthUser user
) {}
