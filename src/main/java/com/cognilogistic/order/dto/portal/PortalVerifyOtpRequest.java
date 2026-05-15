package com.cognilogistic.order.dto.portal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Request body for {@code POST /api/v1/portal/auth/verify-otp}.
 *
 * <p>Components:
 * <ul>
 *   <li>{@code phone} — the customer's phone number (must match the one used in send-otp)</li>
 *   <li>{@code otp} — the 6-digit one-time password received by the customer</li>
 * </ul>
 */
public record PortalVerifyOtpRequest(
        @NotBlank
        @Pattern(regexp = "\\+?\\d{10,15}", message = "phone must be 10–15 digits, optional leading +")
        String phone,
        @NotBlank
        @Pattern(regexp = "\\d{6}", message = "OTP must be 6 digits")
        String otp
) {}
