package com.cognilogistic.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Request body for {@code POST /api/v1/auth/reset-pin/verify-otp}.
 *
 * <p>Components:
 * <ul>
 *   <li>{@code phone} — phone number of the account requesting PIN reset</li>
 *   <li>{@code otp} — exactly 6-digit OTP received via SMS/WhatsApp (OTP is always 6 digits)</li>
 * </ul>
 */
public record ResetPinVerifyOtpRequest(
        @NotBlank @Pattern(regexp = "\\+?\\d{10,15}") String phone,
        @NotBlank @Pattern(regexp = "\\d{6}", message = "OTP must be 6 digits") String otp
) {}
