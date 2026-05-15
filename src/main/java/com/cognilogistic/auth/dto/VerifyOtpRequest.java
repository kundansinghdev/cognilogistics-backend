package com.cognilogistic.auth.dto;

import com.cognilogistic.auth.model.DeviceType;
import com.cognilogistic.auth.model.OtpPurpose;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request body for {@code POST /api/v1/auth/verify-otp}.
 *
 * <p>Components:
 * <ul>
 *   <li>{@code phone} — phone number the OTP was sent to</li>
 *   <li>{@code otp} — exactly 6-digit numeric OTP (OTP is always 6 digits)</li>
 *   <li>{@code purpose} — must match the purpose used when the OTP was sent; rejects
 *       mismatches to prevent purpose-confusion attacks</li>
 *   <li>{@code deviceId} / {@code deviceType} — optional. <strong>Required for OTP-only
 *       roles</strong> ({@link com.cognilogistic.auth.model.UserRole#COGNILOGISTIC_ADMIN})
 *       because the server mints tokens directly from this call (no /setup-pin step).
 *       Optional for PIN-flow roles since they re-supply these on /setup-pin.</li>
 *   <li>{@code roleHint} — optional disambiguator from the FE's role-picker landing page.
 *       Tolerated; informational only. Future enrichment.</li>
 * </ul>
 *
 * <p><strong>Two response shapes</strong> ({@link VerifyOtpResponse}):
 * <ul>
 *   <li>PIN-flow roles → {@code { tempToken, isNewUser, session: null }}</li>
 *   <li>OTP-only roles → {@code { tempToken: null, isNewUser: false, session: LoginResponse }}</li>
 * </ul>
 *
 * @param phone      phone number in E.164 or local 10–15 digit
 * @param otp        6-digit OTP
 * @param purpose    OTP purpose (must match what was sent)
 * @param deviceId   optional client device id; required for OTP-only roles
 * @param deviceType optional WEB / MOBILE; required for OTP-only roles
 * @param roleHint   optional role hint from the FE's role picker
 */
public record VerifyOtpRequest(
        @NotBlank @Pattern(regexp = "\\+?\\d{10,15}", message = "phone must be 10–15 digits, optional leading +") String phone,
        @NotBlank @Pattern(regexp = "\\d{6}", message = "OTP must be 6 digits") String otp,
        @NotNull OtpPurpose purpose,
        @Size(max = 255, message = "deviceId must be at most 255 characters") String deviceId,
        DeviceType deviceType,
        @Size(max = 64, message = "roleHint must be at most 64 characters") String roleHint
) {}
