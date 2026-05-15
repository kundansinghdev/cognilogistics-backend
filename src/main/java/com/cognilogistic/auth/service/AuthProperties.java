package com.cognilogistic.auth.service;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Strongly-typed configuration record bound to the {@code auth.*} namespace in
 * {@code application.yml} / {@code application.properties}.
 *
 * <p>Activated via {@code @EnableConfigurationProperties(AuthProperties.class)} in
 * {@link com.cognilogistic.config.SecurityConfig}.
 *
 * <p>Components:
 * <ul>
 *   <li>{@code jwt} — JWT signing secret and TTL settings for access, refresh, and temp tokens</li>
 *   <li>{@code pin} — brute-force lockout policy (max attempts and lockout duration)</li>
 *   <li>{@code otp} — OTP generation policy (digit length, validity window, resend cooldown)</li>
 * </ul>
 */
@ConfigurationProperties(prefix = "auth")
public record AuthProperties(Jwt jwt, Pin pin, Otp otp) {

    /**
     * JWT-related settings.
     *
     * <ul>
     *   <li>{@code secret} — HMAC-SHA256 signing secret; must be at least 32 bytes</li>
     *   <li>{@code webAccessTtlMinutes} — access token lifetime for WEB device type</li>
     *   <li>{@code mobileAccessTtlMinutes} — access token lifetime for MOBILE device type (typically longer)</li>
     *   <li>{@code refreshTtlDays} — refresh token lifetime in days</li>
     *   <li>{@code tempTokenTtlMinutes} — lifetime of the setup-pin temp token</li>
     *   <li>{@code resetTokenTtlMinutes} — lifetime of the PIN-reset token</li>
     * </ul>
     */
    public record Jwt(
            String secret,
            int webAccessTtlMinutes,
            int mobileAccessTtlMinutes,
            int refreshTtlDays,
            int tempTokenTtlMinutes,
            int resetTokenTtlMinutes
    ) {}

    /**
     * PIN brute-force lockout policy.
     *
     * <ul>
     *   <li>{@code maxFailedAttempts} — consecutive failures before the account is locked</li>
     *   <li>{@code lockoutMinutes} — duration of the lockout window in minutes</li>
     * </ul>
     */
    public record Pin(int maxFailedAttempts, int lockoutMinutes) {}

    /**
     * OTP generation and delivery policy.
     *
     * <ul>
     *   <li>{@code length} — number of digits in each OTP (always 6 in production)</li>
     *   <li>{@code ttlMinutes} — how long an OTP remains valid after issuance</li>
     *   <li>{@code resendCooldownSeconds} — minimum seconds between successive OTP requests
     *       for the same phone+purpose (prevents SMS flooding)</li>
     *   <li>{@code fixedCode} — when non-blank, every OTP uses this value instead of a random
     *       code (local/test only; unset in production)</li>
     * </ul>
     */
    public record Otp(
            int length,
            int ttlMinutes,
            int resendCooldownSeconds,
            int maxVerifyAttempts,
            String fixedCode
    ) {}
}
