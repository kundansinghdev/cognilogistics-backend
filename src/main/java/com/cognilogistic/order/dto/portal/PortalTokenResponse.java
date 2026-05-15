package com.cognilogistic.order.dto.portal;

/**
 * Response returned by {@code POST /api/v1/portal/auth/verify-otp} on successful authentication.
 *
 * <p>Components:
 * <ul>
 *   <li>{@code portalAccessToken} — a short-lived CUSTOMER-role JWT that must be included
 *       in the {@code Authorization: Bearer} header for all subsequent portal API calls.
 *       No refresh token is issued — customers re-authenticate via OTP when the token expires.</li>
 * </ul>
 */
public record PortalTokenResponse(String portalAccessToken) {}
