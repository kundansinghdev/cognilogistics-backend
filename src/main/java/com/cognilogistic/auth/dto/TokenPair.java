package com.cognilogistic.auth.dto;

import java.time.Instant;

/**
 * A JWT access token paired with a long-lived refresh token, as returned by the
 * setup-pin, refresh, and reset-pin/set endpoints.
 *
 * <p>Components:
 * <ul>
 *   <li>{@code accessToken} — signed JWT containing userId, phone, role, and tpAccountId claims</li>
 *   <li>{@code refreshToken} — opaque random token (stored as SHA-256 hash in DB) for silent renewal</li>
 *   <li>{@code accessTokenExpiresAt} — UTC instant when the access token becomes invalid</li>
 *   <li>{@code refreshTokenExpiresAt} — UTC instant when the refresh token expires (configurable via auth.jwt.refreshTtlDays)</li>
 * </ul>
 */
public record TokenPair(
        String accessToken,
        String refreshToken,
        Instant accessTokenExpiresAt,
        Instant refreshTokenExpiresAt
) {}
