package com.cognilogistic.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Request body for {@code POST /api/v1/auth/reset-pin/send-otp}.
 *
 * <p>Initiates the PIN-reset flow for an existing TP user who has forgotten their 4-digit PIN.
 * The purpose (PIN_RESET) is hard-coded on the server side; clients do not specify it here.
 *
 * <p>Components:
 * <ul>
 *   <li>{@code phone} — registered phone number of the account whose PIN should be reset</li>
 * </ul>
 */
public record ResetPinSendOtpRequest(
        @NotBlank @Pattern(regexp = "\\+?\\d{10,15}", message = "phone must be 10–15 digits, optional leading +") String phone
) {}
