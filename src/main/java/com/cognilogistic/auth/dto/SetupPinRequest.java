package com.cognilogistic.auth.dto;

import com.cognilogistic.auth.model.DeviceType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request body for {@code POST /api/v1/auth/setup-pin} — final step of first-login.
 *
 * <p>Components:
 * <ul>
 *   <li>{@code tempToken} — single-use token issued by /verify-otp; SETUP_PIN scope</li>
 *   <li>{@code pin} — the user's chosen 4-digit PIN</li>
 *   <li>{@code deviceId} — scopes the new refresh token to this device</li>
 *   <li>{@code deviceType} — WEB or MOBILE; controls access-token TTL</li>
 *   <li>{@code roleHint} — optional disambiguator from the FE's role-picker landing
 *       page. Accepted-and-ignored today; future enrichment once Partner / Customer
 *       registries are populated. (BACKEND_GAPS §1.2)</li>
 *   <li>{@code acceptedTermsVersion} — version string of the Terms of Service the
 *       user clicked to accept. <strong>Required.</strong> Must equal the current
 *       published version in {@code legal_doc_versions}, otherwise the service
 *       raises {@code CONSENT_VERSION_MISMATCH}. See BACKEND_SPEC_TC_CONSENT.md.</li>
 *   <li>{@code acceptedPrivacyVersion} — same shape, for the Privacy Policy.</li>
 *   <li>{@code orgName} — business name captured on the {@code /confirm-name} signup
 *       step. <strong>Required, 2-100 chars after trim</strong>. Persisted to
 *       {@code tp_accounts.name} so the new TP account is named immediately.
 *       Service raises {@code 400 ORG_NAME_REQUIRED} when missing/blank/too short.
 *       (BACKEND_GAPS §1.9, 2026-05-09 design call.)</li>
 * </ul>
 *
 * <p>Why the consent + orgName fields are not {@code @NotBlank} at the
 * bean-validation level: the {@link com.cognilogistic.auth.service.AuthService}
 * validates them itself and translates missing values into the spec-mandated
 * {@code CONSENT_REQUIRED} / {@code ORG_NAME_REQUIRED} error codes with HTTP 400.
 * A bean-validation failure here would emit {@code VALIDATION_ERROR} instead,
 * breaking the FE's switch on the specific error code.
 *
 * @param tempToken              single-use SETUP_PIN-scope token from /verify-otp
 * @param pin                    4-digit numeric PIN
 * @param deviceId               stable device identifier
 * @param deviceType             WEB or MOBILE
 * @param roleHint               optional role hint — tolerated, currently ignored
 * @param acceptedTermsVersion   accepted Terms of Service version (e.g. "2026-05-08")
 * @param acceptedPrivacyVersion accepted Privacy Policy version (e.g. "2026-05-08")
 * @param orgName                business / organisation name; 2-100 chars after trim;
 *                               lands in {@code tp_accounts.name}
 */
public record SetupPinRequest(
        @NotBlank
        @Pattern(regexp = "[a-fA-F0-9]{32}", message = "tempToken must be a 32-character hex string")
        String tempToken,
        @NotBlank @Pattern(regexp = "\\d{4}", message = "PIN must be 4 digits") String pin,
        @NotBlank @Size(max = 255, message = "deviceId must be at most 255 characters") String deviceId,
        @NotNull DeviceType deviceType,
        @Size(max = 64, message = "roleHint must be at most 64 characters") String roleHint,
        @Size(max = 64, message = "acceptedTermsVersion must be at most 64 characters") String acceptedTermsVersion,
        @Size(max = 64, message = "acceptedPrivacyVersion must be at most 64 characters") String acceptedPrivacyVersion,
        @Size(max = 255, message = "orgName must be at most 255 characters") String orgName
) {}
