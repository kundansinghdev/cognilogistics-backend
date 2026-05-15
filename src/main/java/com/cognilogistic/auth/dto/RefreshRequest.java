package com.cognilogistic.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request body for the {@code POST /api/v1/auth/refresh} endpoint.
 *
 * <p>Components:
 * <ul>
 *   <li>{@code refreshToken} — the raw (unhashed) refresh token obtained from login or a prior refresh;
 *       tokens are single-use — each call to /refresh rotates the refresh token</li>
 * </ul>
 *
 * <p>Shape: opaque Base64url string from {@link com.cognilogistic.auth.service.TokenService}
 * (32 random bytes, no padding).
 */
public record RefreshRequest(
        @NotBlank
        @Size(min = 40, max = 64, message = "refreshToken length out of expected range")
        @Pattern(regexp = "^[A-Za-z0-9_-]+$", message = "refreshToken must be Base64url characters only")
        String refreshToken
) {}
