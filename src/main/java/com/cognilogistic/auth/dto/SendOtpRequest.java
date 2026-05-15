package com.cognilogistic.auth.dto;

import com.cognilogistic.auth.model.OtpPurpose;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * Request body for the generic {@code POST /api/v1/auth/send-otp} endpoint.
 *
 * <p>Components:
 * <ul>
 *   <li>{@code phone} — target phone number in E.164 or local 10-15 digit format</li>
 *   <li>{@code purpose} — the reason for the OTP, which determines TTL and which downstream
 *       endpoint can consume it (e.g., {@link OtpPurpose#FIRST_LOGIN} leads to /setup-pin,
 *       {@link OtpPurpose#PIN_RESET} leads to /reset-pin/set)</li>
 * </ul>
 */
public record SendOtpRequest(
        @NotBlank @Pattern(regexp = "\\+?\\d{10,15}", message = "phone must be 10-15 digits") String phone,
        @NotNull OtpPurpose purpose
) {}
