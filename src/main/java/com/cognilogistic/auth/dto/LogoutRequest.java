package com.cognilogistic.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request body for the {@code POST /api/v1/auth/logout} endpoint.
 *
 * <p>Components:
 * <ul>
 *   <li>{@code refreshToken} — the raw (unhashed) refresh token to revoke;
 *       only the token's device session is ended, other devices remain active</li>
 * </ul>
 *
 * <p>Same shape as {@link RefreshRequest#refreshToken()}.
 */
public record LogoutRequest(
        @NotBlank
        @Size(min = 40, max = 64, message = "refreshToken length out of expected range")
        @Pattern(regexp = "^[A-Za-z0-9_-]+$", message = "refreshToken must be Base64url characters only")
        String refreshToken
) {}
