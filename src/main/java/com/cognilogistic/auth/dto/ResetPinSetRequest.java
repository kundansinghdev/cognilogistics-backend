package com.cognilogistic.auth.dto;

import com.cognilogistic.auth.model.DeviceType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request body for {@code POST /api/v1/auth/reset-pin/set} — the final step of the PIN-reset flow.
 *
 * <p>Components:
 * <ul>
 *   <li>{@code resetToken} — single-use token issued by /reset-pin/verify-otp; scoped to RESET_PIN</li>
 *   <li>{@code newPin} — the user's replacement 4-digit PIN (digits only)</li>
 *   <li>{@code deviceId} — device identifier for which a new refresh token will be issued</li>
 *   <li>{@code deviceType} — WEB or MOBILE</li>
 * </ul>
 */
public record ResetPinSetRequest(
        @NotBlank
        @Pattern(regexp = "[a-fA-F0-9]{32}", message = "resetToken must be a 32-character hex string")
        String resetToken,
        @NotBlank @Pattern(regexp = "\\d{4}", message = "newPin must be exactly 4 digits") String newPin,
        @NotBlank @Size(max = 255, message = "deviceId must be at most 255 characters") String deviceId,
        @NotNull DeviceType deviceType
) {}
