package com.cognilogistic.auth.controller;

import com.cognilogistic.auth.dto.*;
import com.cognilogistic.auth.model.OtpPurpose;
import com.cognilogistic.auth.security.AuthPrincipal;
import com.cognilogistic.auth.security.CurrentUser;
import com.cognilogistic.auth.service.AuthService;
import com.cognilogistic.config.OpenApiConfig;
import com.cognilogistic.platform.api.ApiResponse;
import com.cognilogistic.platform.api.ClientRequestContext;
import com.cognilogistic.platform.api.ControllerRequestLogging;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller exposing the TP-user authentication endpoints under {@code /api/v1/auth}.
 *
 * <p>Authentication for TP (Transport Provider) app users follows a two-step flow:
 * <ol>
 *   <li>Send OTP → verify OTP → receive a short-lived {@code temp_token}</li>
 *   <li>Exchange {@code temp_token} for a PIN setup (first-time) or direct login</li>
 * </ol>
 *
 * <p>Most endpoints are pre-auth and publicly accessible; {@code GET/PATCH /profile}
 * require a valid JWT. See {@link com.cognilogistic.config.SecurityConfig} for the
 * permit-all list.
 *
 * <p>Note: Customer Portal authentication is handled separately by
 * {@link com.cognilogistic.order.controller.portal.PortalAuthController}; it uses OTP-only
 * (no PIN) and issues a CUSTOMER-role JWT.
 */
@Tag(name = "Auth (TP)", description = "Transport-provider auth: OTP, PIN, JWT pair, profile. "
        + "Phones in JSON are E.164 (+91…). Public: check-phone, send/verify OTP, setup-pin, login, refresh, reset-pin/*; "
        + "JWT required: logout, GET/PATCH profile.")
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final AuthService auth;

    public AuthController(AuthService auth) {
        this.auth = auth;
    }

    /**
     * Returns whether the phone already has a {@code users} row. Callers use it to gate
     * login (must exist), signup (must not exist), and reset PIN (must exist).
     */
    @PostMapping("/check-phone")
    public ApiResponse<CheckPhoneResponse> checkPhone(@Valid @RequestBody CheckPhoneRequest req) {
        log.info("[ENTRY] checkPhone | phone={}", ControllerRequestLogging.maskPhone(req.phone()));
        return ControllerRequestLogging.withExitLog(AuthController.class, "checkPhone", () ->
                auth.checkPhone(req.phone()));
    }

    /**
     * Step 1 of the first-login and PIN-reset flows: request a 6-digit OTP via SMS/WhatsApp.
     *
     * @param req contains the phone number and the purpose (FIRST_LOGIN or PIN_RESET via generic endpoint)
     * @return empty success wrapper; the OTP is delivered out-of-band
     */
    @PostMapping("/send-otp")
    public ApiResponse<java.util.Map<String, Object>> sendOtp(@Valid @RequestBody SendOtpRequest req) {
        log.info("[ENTRY] sendOtp | phone={} | purpose={}", ControllerRequestLogging.maskPhone(req.phone()), req.purpose());
        return ControllerRequestLogging.withExitLog(AuthController.class, "sendOtp", () -> {
            auth.sendOtp(req.phone(), req.purpose());
            // FE's apiClient unwraps res.data.data and expects a {sent: boolean} body.
            // Returning bare {"success":true} (no data) makes res.data.data === undefined
            // and the OTP-screen navigation breaks (BACKEND_GAPS §1.1 envelope drift).
            return java.util.Map.of("sent", true);
        });
    }

    /**
     * Step 2 of the first-login flow: verify the OTP and receive a {@code temp_token}
     * that must be exchanged at {@code /setup-pin} within its short TTL.
     *
     * @param req phone, 6-digit OTP, and purpose
     * @return temp_token and a flag indicating whether this is a brand-new user
     */
    @PostMapping("/verify-otp")
    public ApiResponse<VerifyOtpResponse> verifyOtp(@Valid @RequestBody VerifyOtpRequest req) {
        log.info("[ENTRY] verifyOtp | phone={} | purpose={}", ControllerRequestLogging.maskPhone(req.phone()), req.purpose());
        // deviceId / deviceType are optional here; PIN-flow callers re-supply them on
        // /setup-pin. Provisioned COGNILOGISTIC_ADMIN phones are rejected in the service.
        return ControllerRequestLogging.withExitLog(AuthController.class, "verifyOtp", () -> auth.verifyOtp(
                req.phone(), req.otp(), req.purpose(),
                req.deviceId(), req.deviceType()));
    }

    /**
     * Step 3 of the first-login flow: consume the {@code temp_token} and set the user's
     * 4-digit PIN. Returns a full {@link LoginResponse} (tokens + identity payload) so
     * the client is immediately logged in and the FE has everything it needs to render
     * the welcome banner / profile-pending CTA without an extra round-trip
     * (BACKEND_GAPS §1.4).
     *
     * @param req temp_token from verify-otp, 4-digit PIN, device identifier and type
     *            (plus an optional ignored {@code roleHint} per BACKEND_GAPS §1.2)
     * @return tokens + onboarding step + AuthUser identity payload
     */
    @PostMapping("/setup-pin")
    public ApiResponse<LoginResponse> setupPin(@Valid @RequestBody SetupPinRequest req,
                                               HttpServletRequest httpRequest) {
        log.info("[ENTRY] setupPin | deviceId present={}", req.deviceId() != null && !req.deviceId().isBlank());
        // Capture IP and User-Agent server-side for the user_consents audit rows.
        // Per BACKEND_SPEC_TC_CONSENT.md §4.1 these MUST come from the request,
        // never from the client body — we don't trust client-supplied values.
        String clientIp = ClientRequestContext.resolveClientIp(httpRequest);
        String userAgent = ClientRequestContext.resolveUserAgent(httpRequest);
        return ControllerRequestLogging.withExitLog(AuthController.class, "setupPin", () -> auth.setupPin(
                req.tempToken(), req.pin(), req.deviceId(), req.deviceType(),
                req.orgName(),                                  // §1.9
                req.acceptedTermsVersion(), req.acceptedPrivacyVersion(),
                clientIp, userAgent));
    }

    /**
     * Standard day-to-day login using phone + 4-digit PIN. Includes an
     * {@code onboardingStep} field so the mobile app knows which screen to navigate to.
     *
     * @param req phone, 4-digit PIN, device identifier and type
     * @return full login response including token pair and onboarding step
     */
    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest req) {
        log.info("[ENTRY] login | phone={}", ControllerRequestLogging.maskPhone(req.phone()));
        return ControllerRequestLogging.withExitLog(AuthController.class, "login", () -> auth.login(req.phone(), req.pin(), req.deviceId(), req.deviceType()));
    }

    /**
     * Silent token refresh. Consumes the single-use refresh token (rotation pattern)
     * and issues a new access + refresh pair for the same device.
     *
     * @param req the raw (unhashed) refresh token string
     * @return new token pair
     */
    @PostMapping("/refresh")
    public ApiResponse<TokenPair> refresh(@Valid @RequestBody RefreshRequest req) {
        log.info("[ENTRY] refresh | (refresh token body omitted from logs)");
        return ControllerRequestLogging.withExitLog(AuthController.class, "refresh", () -> auth.refresh(req.refreshToken()));
    }

    /**
     * Revokes the supplied refresh token, effectively ending the session on that device.
     *
     * @param req the raw refresh token to revoke
     * @return empty success wrapper
     */
    @PostMapping("/logout")
    @SecurityRequirement(name = OpenApiConfig.BEARER_JWT)
    public ApiResponse<java.util.Map<String, Object>> logout(@Valid @RequestBody LogoutRequest req) {
        log.info("[ENTRY] logout | (refresh token body omitted from logs)");
        return ControllerRequestLogging.withExitLog(AuthController.class, "logout", () -> {
            auth.logout(req.refreshToken());
            // Non-empty data so res.data.data is a real object on the FE side.
            return java.util.Map.of("loggedOut", true);
        });
    }

    /**
     * PIN-reset flow step 1: request a PIN_RESET OTP. Hard-codes purpose = PIN_RESET
     * so clients cannot accidentally trigger a different flow via this endpoint.
     *
     * @param req phone number of the account whose PIN should be reset
     * @return empty success wrapper
     */
    @PostMapping("/reset-pin/send-otp")
    public ApiResponse<java.util.Map<String, Object>> resetSendOtp(@Valid @RequestBody ResetPinSendOtpRequest req) {
        log.info("[ENTRY] resetSendOtp | phone={}", ControllerRequestLogging.maskPhone(req.phone()));
        // Purpose is always PIN_RESET here; the generic /send-otp endpoint requires explicit purpose.
        return ControllerRequestLogging.withExitLog(AuthController.class, "resetSendOtp", () -> {
            auth.sendOtp(req.phone(), OtpPurpose.PIN_RESET);
            return java.util.Map.of("sent", true);
        });
    }

    /**
     * PIN-reset flow step 2: verify the PIN_RESET OTP and receive a {@code reset_token}.
     *
     * @param req phone and 6-digit OTP
     * @return reset_token to be used on /reset-pin/set
     */
    @PostMapping("/reset-pin/verify-otp")
    public ApiResponse<VerifyOtpResponse> resetVerifyOtp(@Valid @RequestBody ResetPinVerifyOtpRequest req) {
        log.info("[ENTRY] resetVerifyOtp | phone={}", ControllerRequestLogging.maskPhone(req.phone()));
        // PIN reset is a PIN-flow path only (admin has no PIN to reset). Pass
        // null deviceId/deviceType — the service's OTP-only branch is gated on
        // role and won't fire here.
        return ControllerRequestLogging.withExitLog(AuthController.class, "resetVerifyOtp", () -> auth.verifyOtp(
                req.phone(), req.otp(), OtpPurpose.PIN_RESET, null, null));
    }

    /**
     * PIN-reset flow step 3: consume the {@code reset_token} and replace the stored PIN.
     * All existing refresh tokens for this user are revoked (lost-device protection).
     *
     * <p>Returns a full {@link LoginResponse} for shape parity with /login and
     * /setup-pin — the FE can use the same identity-store update logic on every
     * "session-start" path.
     *
     * @param req reset_token, new 4-digit PIN, device identifier and type
     * @return tokens + onboarding step + AuthUser identity payload
     */
    @PostMapping("/reset-pin/set")
    public ApiResponse<LoginResponse> resetPin(@Valid @RequestBody ResetPinSetRequest req) {
        log.info("[ENTRY] resetPin | deviceId present={}", req.deviceId() != null && !req.deviceId().isBlank());
        return ControllerRequestLogging.withExitLog(AuthController.class, "resetPin", () -> auth.resetPin(req.resetToken(), req.newPin(), req.deviceId(), req.deviceType()));
    }

    /**
     * Returns the authenticated user's current profile snapshot (same {@link AuthUser}
     * shape as login) so the SPA can refresh its client-side identity cache after
     * navigation, tab restore, or admin-side account changes.
     */
    @GetMapping("/profile")
    @SecurityRequirement(name = OpenApiConfig.BEARER_JWT)
    public ApiResponse<ProfileResponse> getProfile(@CurrentUser AuthPrincipal me) {
        log.info("[ENTRY] getProfile | userId={} | role={}", me != null ? me.userId() : null, me != null ? me.role() : null);
        return ControllerRequestLogging.withExitLog(AuthController.class, "getProfile", () -> {
            AuthUser user = auth.getProfile(me);
            return new ProfileResponse(user);
        });
    }

    /**
     * Profile completion (BACKEND_GAPS §1.1) — sets organisation name, full name,
     * and WhatsApp number, and advances the user from onboarding step 2 to 3 so
     * the FE drops the welcome banner and the "Profile pending" badges.
     *
     * <p>For TP-side users, {@code orgName} overwrites the placeholder {@code "Pending Setup"}
     * stamped on the {@code tp_accounts} row at signup. For other roles
     * (PARTNER_TP, CUSTOMER, COGNILOGISTIC_ADMIN) the {@code orgName} field is
     * accepted but ignored — those roles have no TP account row.
     *
     * @param me  the authenticated user (from JWT)
     * @param req the profile fields the user filled in
     * @return {@code { user: AuthUser }} with {@code profileComplete: true} and
     *         {@code onboardingStep: 3}
     */
    @PatchMapping("/profile")
    @SecurityRequirement(name = OpenApiConfig.BEARER_JWT)
    public ApiResponse<ProfileResponse> updateProfile(@CurrentUser AuthPrincipal me,
                                                       @Valid @RequestBody UpdateProfileRequest req) {
        log.info("[ENTRY] updateProfile | userId={} | role={}", me != null ? me.userId() : null, me != null ? me.role() : null);
        return ControllerRequestLogging.withExitLog(AuthController.class, "updateProfile", () -> {
            AuthUser user = auth.updateProfile(me, req);
            return new ProfileResponse(user);
        });
    }
}
