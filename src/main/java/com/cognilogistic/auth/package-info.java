/**
 * Auth module — phone-OTP login, PIN setup, JWT issuance, refresh tokens.
 *
 * <p>Owns: users, auth_credentials, otp_log, refresh_tokens. Issues the
 * JWT access tokens that every other request flows through {@code JwtAuthFilter}
 * (in {@code com.cognilogistic.auth.security}) to authenticate.
 *
 * <p>Depends on: {@code platform.api} (response envelope, errors) and Spring Security.
 * Depended on by: every controller (via {@code @CurrentUser AuthPrincipal}) and any
 * service that needs the caller's {@code tpAccountId} for tenant isolation.
 *
 * <p>Read first: {@link com.cognilogistic.auth.service.AuthService} for the OTP →
 * verify → setup-pin → login flow, and {@link com.cognilogistic.auth.security.AuthPrincipal}
 * for the shape of the authenticated user as it travels through the request.
 */
package com.cognilogistic.auth;
