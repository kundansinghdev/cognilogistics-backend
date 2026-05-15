package com.cognilogistic.auth.service;

import com.cognilogistic.auth.dto.AuthUser;
import com.cognilogistic.auth.dto.LoginResponse;
import com.cognilogistic.auth.dto.ShadowContext;
import com.cognilogistic.auth.dto.TokenPair;
import com.cognilogistic.auth.dto.UpdateProfileRequest;
import com.cognilogistic.auth.dto.VerifyOtpResponse;
import com.cognilogistic.auth.model.AuthCredentials;
import com.cognilogistic.auth.model.DeviceType;
import com.cognilogistic.auth.model.OtpPurpose;
import com.cognilogistic.auth.model.RefreshToken;
import com.cognilogistic.auth.model.User;
import com.cognilogistic.auth.model.UserRole;
import com.cognilogistic.auth.repository.AuthCredentialsRepository;
import com.cognilogistic.auth.repository.UserRepository;
import com.cognilogistic.auth.support.AuthPhoneNormalizer;
import com.cognilogistic.auth.security.AuthPrincipal;
import com.cognilogistic.platform.api.ApiException;
import com.cognilogistic.platform.api.ErrorCode;
import com.cognilogistic.user.repository.TpAccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

/**
 * Core authentication orchestrator for the TP-user (Transport Provider) app.
 *
 * <p>Coordinates the multi-step auth flows by delegating to specialised services
 * ({@link OtpService}, {@link PinService}, {@link TokenService},
 * {@link TempTokenService}). Shadow-account phones are rejected at the earliest
 * point so customers can't accidentally register through the TP flow.
 *
 * <p><strong>Profile completion</strong> (BACKEND_GAPS §1.1): {@link #updateProfile}
 * is the destination of the first-login {@code PATCH /auth/profile} — it sets
 * {@code users.name}, {@code users.whatsapp_number}, {@code users.onboarding_step=3},
 * and (for TP-side users) overwrites the placeholder {@code tp_accounts.name}
 * supplied at signup with the user-typed organisation name.
 *
 * <p><strong>AuthUser payload</strong>: built by {@link #buildAuthUser(User)} on
 * every login / setup-pin / reset-pin response so the front-end has everything it
 * needs in one round-trip (role, plan, account status, profile-complete flag,
 * org name, role-specific id fields).
 */
@Service
public class AuthService {

    private final UserRepository users;
    private final AuthCredentialsRepository creds;
    private final TpAccountRepository tpAccounts;
    private final OtpService otpService;
    private final PinService pinService;
    private final TokenService tokenService;
    private final TempTokenService tempTokens;
    private final AuthProperties props;
    /** Read on every setup-pin to validate the FE-supplied version strings. */
    private final com.cognilogistic.legal.repository.LegalDocVersionRepository legalDocVersions;
    /** Append-only audit log for T&C / Privacy consent events. */
    private final com.cognilogistic.legal.repository.UserConsentRepository userConsents;
    /** §1.10 shadow-context lookup — `tp_accounts.name` for sponsoring sender display. */
    private final com.cognilogistic.user.repository.TpAccountJpa tpAccountJpa;
    /** §1.10 shadow-context lookup — partner profile's `company_name` + future sponsor edge. */
    private final com.cognilogistic.tender.repository.PartnerTpProfileRepository partnerProfiles;
    /** §1.10 shadow-context lookup — customer's `legal_name` and `created_by_tp_id` for sponsor name. */
    private final com.cognilogistic.order.repository.CustomerRepository customerRepo;

    public AuthService(UserRepository users,
                       AuthCredentialsRepository creds,
                       TpAccountRepository tpAccounts,
                       OtpService otpService,
                       PinService pinService,
                       TokenService tokenService,
                       TempTokenService tempTokens,
                       AuthProperties props,
                       com.cognilogistic.legal.repository.LegalDocVersionRepository legalDocVersions,
                       com.cognilogistic.legal.repository.UserConsentRepository userConsents,
                       com.cognilogistic.user.repository.TpAccountJpa tpAccountJpa,
                       com.cognilogistic.tender.repository.PartnerTpProfileRepository partnerProfiles,
                       com.cognilogistic.order.repository.CustomerRepository customerRepo) {
        this.users = users;
        this.creds = creds;
        this.tpAccounts = tpAccounts;
        this.otpService = otpService;
        this.pinService = pinService;
        this.tokenService = tokenService;
        this.tempTokens = tempTokens;
        this.props = props;
        this.legalDocVersions = legalDocVersions;
        this.userConsents = userConsents;
        this.tpAccountJpa = tpAccountJpa;
        this.partnerProfiles = partnerProfiles;
        this.customerRepo = customerRepo;
    }

    /**
     * Returns whether a {@code users} row exists for the given phone (after E.164 normalisation).
     * Used by {@code POST /auth/check-phone} so login, signup, and reset-PIN flows can
     * validate the number before MPIN or OTP steps.
     */
    public boolean isPhoneRegistered(String phone) {
        return checkPhone(phone).registered();
    }

    /**
     * Phone lookup for pre-auth flows. {@code loginOnly} is {@code true} for provisioned
     * platform admins so the FE can hide signup / PIN-reset affordances.
     */
    public com.cognilogistic.auth.dto.CheckPhoneResponse checkPhone(String phone) {
        String normalized = AuthPhoneNormalizer.normalize(phone);
        User user = users.findByPhone(normalized).orElse(null);
        boolean registered = user != null;
        boolean loginOnly = user != null
                && !user.isShadow()
                && user.getRole() == UserRole.COGNILOGISTIC_ADMIN;
        return new com.cognilogistic.auth.dto.CheckPhoneResponse(registered, loginOnly);
    }

    private void rejectPlatformAdminSelfService(User user, String action) {
        if (user != null && !user.isShadow() && user.getRole() == UserRole.COGNILOGISTIC_ADMIN) {
            throw new ApiException(ErrorCode.ADMIN_LOGIN_ONLY,
                    "Platform admin accounts are login-only. " + action
                    + " is not available — use POST /auth/login with your assigned credentials.");
        }
    }

    private void rejectPlatformAdminSelfService(String phone, String action) {
        String normalized = AuthPhoneNormalizer.normalize(phone);
        rejectPlatformAdminSelfService(users.findByPhone(normalized).orElse(null), action);
    }

    public void sendOtp(String phone, OtpPurpose purpose) {
        String normalized = AuthPhoneNormalizer.normalize(phone);
        rejectPlatformAdminSelfService(normalized, "OTP sign-in and PIN reset");
        // PIN reset: only registered phones may receive an OTP (no row → no SMS).
        if (purpose == OtpPurpose.PIN_RESET && users.findByPhone(normalized).isEmpty()) {
            throw new ApiException(ErrorCode.PHONE_NOT_REGISTERED, "Mobile number not found.");
        }
        // BR-AUTH-11 PARTIALLY RETIRED 2026-05-10 (BACKEND_GAPS §1.10):
        //   Old behaviour rejected shadow phones at send-otp with SHADOW_ACCOUNT 403.
        //   New behaviour: shadow phones are allowed through send-otp + verify-otp +
        //   setup-pin to ACTIVATE themselves (verify-otp returns shadow context, FE
        //   pre-fills the org name, setup-pin flips is_shadow → false).
        //
        //   The SHADOW_ACCOUNT throw is KEPT on /auth/login (PIN-only path) so a
        //   shadow user who taps "Login" instead of "Sign up" gets a clear branch
        //   error rather than a confusing INVALID_PIN.
        otpService.send(normalized, purpose);
    }

    /**
     * Verifies the OTP and issues a short-lived {@code tempToken} for the PIN-flow
     * (setup-pin or reset-pin). Provisioned {@link UserRole#COGNILOGISTIC_ADMIN}
     * phones are rejected — admins sign in only via {@link #login}.
     *
     * <p><strong>Two response shapes</strong> — see {@link VerifyOtpResponse}.
     *
     * @param phone      verified phone
     * @param code       the 6-digit OTP
     * @param purpose    OTP purpose
     * @param deviceId   client device id; required for OTP-only roles (otherwise
     *                   the server has nothing to scope a refresh token to)
     * @param deviceType WEB / MOBILE; required for OTP-only roles
     */
    @Transactional
    public VerifyOtpResponse verifyOtp(String phone,
                                       String code,
                                       OtpPurpose purpose,
                                       String deviceId,
                                       DeviceType deviceType) {
        phone = AuthPhoneNormalizer.normalize(phone);
        String otpCode = code == null ? "" : code.trim();
        otpService.verify(phone, purpose, otpCode);

        // ⚠ DESIGN NOTE — DEFERRED USER CREATION (changed 2026-05-08):
        //   Pre-2026-05-08 this method called users.save(User.newPrimary(phone))
        //   for every new phone, eagerly creating a stub users row with
        //   onboarding_step=1 and no auth_credentials. Abandoned signups
        //   (verify-otp without setup-pin) left orphan rows that polluted
        //   the table and confused subsequent retries.
        //
        //   Now: verify-otp NEVER writes to users for fresh signups. The
        //   tempToken alone proves "this phone has been OTP-verified", and
        //   /auth/setup-pin creates the users row atomically with
        //   auth_credentials + tp_accounts + user_consents. If signup is
        //   abandoned, no row is left behind.
        //
        //   We still LOOK UP the user by phone here for shadow detection
        //   (BACKEND_GAPS §1.10) — see below — and OTP-only fast-path (legacy,
        //   §1.7-superseded but kept inert for one release cycle).
        User user = users.findByPhone(phone).orElse(null);
        boolean isNewUser = user == null;

        // BR-AUTH-11 PARTIALLY RETIRED 2026-05-10 (BACKEND_GAPS §1.10):
        //   Shadow accounts no longer rejected on verify-otp. Instead the
        //   shadow row is surfaced as ShadowContext on the response so the FE
        //   can skip role-pick + pre-fill the business name. The activation
        //   itself (is_shadow → false) happens in setupPin once the user
        //   completes the wizard. SHADOW_ACCOUNT throw still lives on /auth/login
        //   so a shadow user who taps "Sign in" instead of "Sign up" gets a
        //   clear redirect signal.

        // PIN_RESET requires an existing user — no row to reset a PIN on otherwise.
        if (purpose == OtpPurpose.PIN_RESET && isNewUser) {
            throw new ApiException(ErrorCode.PHONE_NOT_REGISTERED, "Mobile number not found.");
        }

        rejectPlatformAdminSelfService(user, "OTP sign-in and PIN reset");

        // PIN-flow: hand back a temp token the client exchanges at /setup-pin
        // (or /reset-pin/set for PIN reset). Token carries phone always; userId
        // only when an existing users row backs it (PIN reset, returning user
        // including shadow rows being activated).
        String tempToken;
        if (purpose == OtpPurpose.PIN_RESET) {
            tempToken = tempTokens.issueResetPin(user.getId(), phone);
        } else if (user == null) {
            // Fresh signup — phone-only token, no users row yet.
            tempToken = tempTokens.issueSetupPinForPhone(phone);
        } else {
            // Returning PIN-flow user OR shadow user being activated. Carry userId
            // so setupPin can find the existing row (and flip is_shadow=false).
            tempToken = tempTokens.issueSetupPin(user.getId(), phone);
        }

        // §1.10 shadow context: if the user row was pre-created with is_shadow=TRUE
        // (Sender → partner network or Sender → company master), surface enough
        // info for the FE to skip role-pick and pre-fill the business name.
        ShadowContext shadow = (user != null && user.isShadow())
                ? buildShadowContext(user)
                : null;

        return shadow != null
                ? VerifyOtpResponse.pinFlowWithShadow(tempToken, isNewUser, shadow)
                : VerifyOtpResponse.pinFlow(tempToken, isNewUser);
    }

    /**
     * Resolves the {@link ShadowContext} for a verified shadow user. Returns
     * {@code null} if the role doesn't have a known shadow shape (today only
     * PARTNER_TP and CUSTOMER do — the Sender-side flows that create them).
     *
     * <p>{@code sponsoringSenderName} resolution:
     * <ul>
     *   <li><strong>CUSTOMER shadow</strong>: walks {@code users.customer_id}
     *       → {@code customers.created_by_tp_id} → {@code tp_accounts.name}.
     *       Fully wired today.</li>
     *   <li><strong>PARTNER_TP shadow</strong>: would walk via
     *       {@code tp_partner_network} but that entity isn't migrated yet.
     *       Returns {@code null} sponsor name today; FE banner gracefully
     *       degrades to "You've been added to a partner network" wording.
     *       Wires up when the partner-network creation flow lands.</li>
     * </ul>
     */
    private ShadowContext buildShadowContext(User user) {
        UserRole role = user.getRole();
        String orgName = null;
        String sponsoringSender = null;

        if (role == UserRole.PARTNER_TP && user.getPartnerTpProfileId() != null) {
            orgName = partnerProfiles.findById(user.getPartnerTpProfileId())
                    .map(p -> p.getCompanyName())
                    .orElse(null);
            // TODO(partner-network-shadow-sponsor): once tp_partner_network
            //   entity + repo land, look up the sender that added this partner
            //   and pass through tp_accounts.name. Until then sponsor stays null.
        } else if (role == UserRole.CUSTOMER && user.getCustomerId() != null) {
            var customer = customerRepo.findById(user.getCustomerId()).orElse(null);
            if (customer != null) {
                orgName = customer.getName();
                if (customer.getCreatedByTp() != null) {
                    sponsoringSender = tpAccountJpa.findById(customer.getCreatedByTp())
                            .map(tp -> tp.getName())
                            .orElse(null);
                }
            }
        }
        // Other roles (TP_ADMIN, TP_TRANSPORT_MANAGER, COGNILOGISTIC_ADMIN) have
        // no documented shadow-creation path. Returning null here keeps the FE
        // surface simple — they'd fall through the organic 5-step signup.
        return new ShadowContext(role, orgName, sponsoringSender);
    }

    /**
     * Completes first-login registration: consumes the temp token, stores the hashed
     * PIN, ensures a TP account exists, records the user's T&amp;C / Privacy consent,
     * and issues a full {@link LoginResponse} (token pair + identity).
     *
     * <p>BACKEND_GAPS §1.4 — new TP signups land on {@code Plan=BASIC} +
     * {@code account_status=PENDING}; the {@link AuthUser#profileComplete} flag is
     * {@code false} so the FE renders the welcome banner / profile-pending CTA.
     *
     * <p><strong>Consent capture (BACKEND_SPEC_TC_CONSENT.md):</strong> the two
     * version strings are validated against {@code legal_doc_versions} and a row
     * per doc type is appended to {@code user_consents} in the same transaction
     * as the user / TP-account inserts. The whole flow is atomic — if the
     * consent insert fails, the user/TP rows roll back.
     *
     * @param tempToken              single-use SETUP_PIN-scoped token
     * @param pin                    4-digit PIN
     * @param deviceId               stable device identifier
     * @param deviceType             WEB / MOBILE
     * @param orgName                business / organisation name (BACKEND_GAPS §1.9, required, 2-100 chars)
     * @param acceptedTermsVersion   FE-submitted T&amp;C version string; must equal current
     * @param acceptedPrivacyVersion FE-submitted Privacy version string; must equal current
     * @param clientIp               server-extracted client IP for the consent rows; nullable
     * @param userAgent              raw User-Agent header for the consent rows; nullable
     */
    @Transactional
    public LoginResponse setupPin(String tempToken,
                                  String pin,
                                  String deviceId,
                                  DeviceType deviceType,
                                  String orgName,
                                  String acceptedTermsVersion,
                                  String acceptedPrivacyVersion,
                                  String clientIp,
                                  String userAgent) {

        // Validate orgName + consent BEFORE consuming the temp token so a stale
        // browser tab doesn't burn the user's only setup-pin credential.
        // §1.9: business name is mandatory at signup — surfaces a friendly inline
        // error on the FE rather than the post-login profile-completion banner.
        String trimmedOrgName = orgName == null ? "" : orgName.trim();
        if (trimmedOrgName.length() < 2 || trimmedOrgName.length() > 100) {
            throw new ApiException(ErrorCode.ORG_NAME_REQUIRED,
                    "Business name is required (2–100 characters).");
        }
        requireConsentVersionsMatch(acceptedTermsVersion, acceptedPrivacyVersion);

        // ⚠ DEFERRED USER CREATION (since 2026-05-08):
        //   The temp token may carry only a phone (fresh signup — no users row
        //   yet) or both phone+userId (returning-PIN-less case OR a shadow user
        //   being activated, since verify-otp now issues userId-bearing tokens
        //   for shadow rows). Read both via consumeEntry so we can handle either
        //   case without two store hits.
        TempTokenService.Entry entry = tempTokens.consumeEntry(tempToken, TempTokenService.Scope.SETUP_PIN);

        // Resolve OR create the users row. The look-up-by-phone fallback covers
        // a race: someone re-runs verify-otp + setup-pin twice in parallel — the
        // second setup-pin's token has userId=null but the row was created by the
        // first call. We just adopt the existing row.
        User user;
        if (entry.userId() != null) {
            user = users.findById(entry.userId()).orElse(null);
        } else {
            user = users.findByPhone(entry.phone()).orElse(null);
        }
        boolean createdHere = (user == null);
        boolean activatedShadow = false;
        rejectPlatformAdminSelfService(user, "Self-service registration");

        if (createdHere) {
            // First-time signup — create the users row INSIDE this @Transactional
            // setupPin block so the whole signup (user + auth_credentials +
            // tp_accounts + user_consents) is atomic. If anything fails, nothing
            // persists. This is the design fix for the abandoned-signup orphan
            // problem (User.newPrimary used to be called from verifyOtp).
            user = User.newPrimary(entry.phone());
            user = users.save(user);
        } else if (user.isShadow()) {
            // §1.10 SHADOW ACTIVATION: pre-created row gets flipped to active.
            // The shadow row's ROLE is canonical — any client-supplied roleHint
            // is ignored. We DON'T overwrite user.name / partnerProfileId /
            // customerId — those were set when the row was created and are
            // load-bearing for downstream queries.
            user.setShadow(false);
            user = users.save(user);
            activatedShadow = true;
        }

        AuthCredentials c = creds.findByUserId(user.getId()).orElseGet(AuthCredentials::new);
        c.setUserId(user.getId());
        c.setPinHash(pinService.hash(pin));
        c.setFailedAttempts(0);
        c.setLockedUntil(null);
        creds.save(c);

        // First-time signup: create the TP account synchronously now that the user has a PIN.
        // BR-PLN-02: starts in account_status=PENDING; BR-PLN-04: starts on plan=BASIC. Both
        // defaults applied by TpAccountRepositoryImpl.createForPrimaryUser.
        //
        // Shadow activation: a CUSTOMER- or PARTNER_TP-shadow user already has a
        // role anchored elsewhere (customers row / partner_tp_profiles row); they
        // typically don't OWN a tp_account themselves, so the create-if-missing
        // guard naturally skips. TP_ADMIN shadow (rare today) would create one.
        String resolvedTpId = user.getTpAccountId();
        if (resolvedTpId == null && user.getRole() == UserRole.TP_ADMIN) {
            resolvedTpId = tpAccounts.createForPrimaryUser(user.getId());
            user.setTpAccountId(resolvedTpId);
            users.save(user);
        }

        // §1.9 — persist the FE-supplied orgName onto tp_accounts.name. Only
        // applies when this user has a TP account (TP_ADMIN signups). For
        // PARTNER_TP / CUSTOMER shadow activations the orgName is informational —
        // their canonical name lives on partner_tp_profiles / customers and was
        // set at the original create-shadow time. We DON'T overwrite that.
        if (resolvedTpId != null) {
            tpAccounts.updateName(resolvedTpId, trimmedOrgName);
        }

        // Record both consent rows in the SAME transaction as the user / TP inserts.
        // If either fails the whole signup rolls back — no orphan users without
        // consent, no orphan consent without users (spec §4.1 atomic-rollback rule).
        recordConsent(user.getId(), com.cognilogistic.legal.model.DocType.TERMS,
                acceptedTermsVersion, clientIp, userAgent);
        recordConsent(user.getId(), com.cognilogistic.legal.model.DocType.PRIVACY,
                acceptedPrivacyVersion, clientIp, userAgent);

        // Quiet-flag log of shadow activation so ops can confirm activations are
        // happening and the partner-network creation flow (when it lands) is
        // correctly populating shadows that get picked up here.
        if (activatedShadow) {
            // SLF4J-friendly — no PII logged at INFO level (just role + id prefix).
            // (Keeping bare comment for now; add a logger field in a separate PR.)
        }

        TokenPair pair = issueTokens(user, deviceId, deviceType);
        return buildLoginResponse(user, pair);
    }

    /**
     * Validates that the FE-supplied T&amp;C / Privacy version strings are present
     * and equal the current published versions in {@code legal_doc_versions}.
     * Raises {@link ErrorCode#CONSENT_REQUIRED} for missing fields and
     * {@link ErrorCode#CONSENT_VERSION_MISMATCH} for stale values.
     */
    private void requireConsentVersionsMatch(String terms, String privacy) {
        if (terms == null || terms.isBlank() || privacy == null || privacy.isBlank()) {
            throw new ApiException(ErrorCode.CONSENT_REQUIRED,
                    "You must accept the Terms of Service and Privacy Policy to continue.");
        }
        String currentTerms = legalDocVersions.findById(com.cognilogistic.legal.model.DocType.TERMS)
                .orElseThrow(() -> new ApiException(ErrorCode.INTERNAL_ERROR,
                        "Terms of Service version not configured on the server."))
                .getVersion();
        String currentPrivacy = legalDocVersions.findById(com.cognilogistic.legal.model.DocType.PRIVACY)
                .orElseThrow(() -> new ApiException(ErrorCode.INTERNAL_ERROR,
                        "Privacy Policy version not configured on the server."))
                .getVersion();
        if (!currentTerms.equals(terms.trim()) || !currentPrivacy.equals(privacy.trim())) {
            throw new ApiException(ErrorCode.CONSENT_VERSION_MISMATCH,
                    "The Terms or Privacy Policy have been updated. Please review and accept the latest version.");
        }
    }

    /**
     * Persists a single consent row. Catches the UNIQUE-constraint violation on
     * {@code (user_id, doc_type, doc_version)} and translates it to a no-op —
     * idempotent re-submission of the same accept (replay) is a success, not a
     * 500. Any other persistence error bubbles up so the surrounding
     * {@code @Transactional} setupPin call rolls back the whole signup.
     */
    private void recordConsent(String userId,
                               com.cognilogistic.legal.model.DocType docType,
                               String docVersion,
                               String clientIp,
                               String userAgent) {
        com.cognilogistic.legal.model.UserConsent row = new com.cognilogistic.legal.model.UserConsent();
        row.ensureId();
        row.setUserId(userId);
        row.setDocType(docType);
        row.setDocVersion(docVersion.trim());
        row.setAcceptedAt(Instant.now());
        row.setIpAddress(clientIp);
        row.setUserAgent(userAgent);
        try {
            userConsents.save(row);
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            // UNIQUE on (user_id, doc_type, doc_version) — the user already has this
            // consent recorded. Idempotent: silently absorb the duplicate.
        }
    }

    /** noRollbackFor ApiException: failed-PIN path persists the increment then throws. */
    @Transactional(noRollbackFor = ApiException.class)
    public LoginResponse login(String phone, String pin, String deviceId, DeviceType deviceType) {
        phone = AuthPhoneNormalizer.normalize(phone);
        pinService.validateFormat(pin);
        User user = users.findByPhone(phone)
                .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHORIZED, "Invalid credentials"));
        if (user.isShadow()) {
            throw new ApiException(ErrorCode.SHADOW_ACCOUNT, "Phone belongs to a shadow account");
        }

        AuthCredentials c = creds.findByUserId(user.getId())
                .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHORIZED, "Invalid credentials"));

        Instant now = Instant.now();
        if (c.isLocked(now)) {
            throw new ApiException(ErrorCode.ACCOUNT_LOCKED,
                    "Account locked until " + c.getLockedUntil());
        }
        if (!pinService.matches(pin, c.getPinHash())) {
            int attempts = c.getFailedAttempts() + 1;
            c.setFailedAttempts(attempts);
            if (attempts >= props.pin().maxFailedAttempts()) {
                c.setLockedUntil(now.plus(Duration.ofMinutes(props.pin().lockoutMinutes())));
                c.setFailedAttempts(0);
            }
            creds.save(c);
            throw new ApiException(ErrorCode.UNAUTHORIZED, "Invalid credentials");
        }
        c.setFailedAttempts(0);
        c.setLockedUntil(null);
        creds.save(c);

        TokenPair pair = issueTokens(user, deviceId, deviceType);
        return buildLoginResponse(user, pair);
    }

    @Transactional
    public TokenPair refresh(String refreshToken) {
        RefreshToken rt = tokenService.consumeRefreshToken(refreshToken);
        User user = users.findById(rt.getUserId())
                .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHORIZED, "User not found"));
        return issueTokens(user, rt.getDeviceId(), rt.getDeviceType());
    }

    @Transactional
    public void logout(String refreshToken) {
        if (refreshToken != null && !refreshToken.isBlank()) {
            tokenService.consumeRefreshToken(refreshToken);
        }
    }

    /**
     * Completes the PIN-reset flow. Returns a {@link LoginResponse} so the FE
     * gets the same shape on every "session-start" path (login / setupPin / resetPin).
     */
    @Transactional
    public LoginResponse resetPin(String resetToken, String newPin, String deviceId, DeviceType deviceType) {
        String userId = tempTokens.consume(resetToken, TempTokenService.Scope.RESET_PIN);
        User user = users.findById(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHORIZED, "User not found"));
        rejectPlatformAdminSelfService(user, "PIN reset");

        AuthCredentials c = creds.findByUserId(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHORIZED, "Credentials not found"));
        c.setPinHash(pinService.hash(newPin));
        c.setFailedAttempts(0);
        c.setLockedUntil(null);
        creds.save(c);

        // Lost-device protection — every existing refresh token is revoked on PIN reset.
        tokenService.revokeAllForUser(userId);

        TokenPair pair = issueTokens(user, deviceId, deviceType);
        return buildLoginResponse(user, pair);
    }

    /**
     * Updates a user's profile (BACKEND_GAPS §1.1). Sets {@code users.name},
     * {@code users.whatsapp_number}, advances {@code users.onboarding_step} to 3,
     * and (for TP-side users) overwrites {@code tp_accounts.name} so the
     * placeholder ({@code "Pending Setup"}) is replaced with the real org name.
     *
     * @param principal the authenticated caller (from the JWT — supplies userId)
     * @param request   the profile fields the user filled in on the welcome banner
     * @return the freshly-rebuilt {@link AuthUser} reflecting the update; the caller
     *         (controller) wraps it in a {@code ProfileResponse} envelope
     */
    @Transactional
    public AuthUser updateProfile(AuthPrincipal principal, UpdateProfileRequest request) {
        if (principal == null || principal.userId() == null) {
            throw new ApiException(ErrorCode.UNAUTHORIZED, "No authenticated user");
        }
        User user = users.findById(principal.userId())
                .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHORIZED, "User not found"));

        user.setName(request.fullName());
        user.setWhatsappNumber(request.whatsappNumber());
        // Step 3 = "complete" — drives profileComplete=true on the AuthUser payload
        // so the FE drops the welcome banner / profile-pending CTA.
        user.setOnboardingStep(3);
        users.save(user);

        // TP-side users: overwrite the placeholder org name supplied at signup.
        // For PARTNER_TP / CUSTOMER / COGNILOGISTIC_ADMIN the orgName field is
        // ignored — those roles have no tp_accounts row to write to.
        if (user.getTpAccountId() != null && request.orgName() != null && !request.orgName().isBlank()) {
            tpAccounts.updateName(user.getTpAccountId(), request.orgName());
        }

        return buildAuthUser(user);
    }

    /**
     * Returns the caller's current identity as an {@link AuthUser}, resolving TP-side
     * fields from {@code tp_accounts} the same way as login. When the JWT is an
     * admin-impersonation session, impersonation flags are copied from the principal
     * onto the payload so the client banner / profile UI stay consistent.
     *
     * @param principal the authenticated caller (from JWT)
     * @return a fresh {@link AuthUser} snapshot for the client cache
     */
    @Transactional(readOnly = true)
    public AuthUser getProfile(AuthPrincipal principal) {
        if (principal == null || principal.userId() == null) {
            throw new ApiException(ErrorCode.UNAUTHORIZED, "No authenticated user");
        }
        User user = users.findById(principal.userId())
                .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHORIZED, "User not found"));
        AuthUser auth = buildAuthUser(user);
        if (principal.isImpersonated()) {
            return new AuthUser(
                    auth.id(),
                    auth.phone(),
                    auth.role(),
                    auth.name(),
                    auth.whatsappNumber(),
                    auth.email(),
                    auth.onboardingStep(),
                    auth.profileComplete(),
                    auth.tpAccountId(),
                    auth.orgName(),
                    auth.plan(),
                    auth.accountStatus(),
                    auth.partnerId(),
                    auth.customerId(),
                    true,
                    principal.impersonatedByUserId());
        }
        return auth;
    }

    /** Issues an access token and a fresh refresh token, paired with their expiries. */
    private TokenPair issueTokens(User user, String deviceId, DeviceType deviceType) {
        return issueTokens(user, deviceId, deviceType, null);
    }

    /**
     * Issues a token pair, optionally stamping the access token with an
     * {@code imp} claim that records the COGNILOGISTIC_ADMIN's user id (used by
     * the admin-impersonation flow — PR R6 / BACKEND_GAPS §7).
     */
    private TokenPair issueTokens(User user, String deviceId, DeviceType deviceType, String impersonatedByUserId) {
        String access = tokenService.issueAccessToken(user, deviceType, impersonatedByUserId);
        TokenService.IssuedRefresh r = tokenService.issueRefreshToken(user, deviceId, deviceType);
        int accessTtl = deviceType == DeviceType.MOBILE
                ? props.jwt().mobileAccessTtlMinutes()
                : props.jwt().webAccessTtlMinutes();
        return new TokenPair(access,
                r.rawToken(),
                Instant.now().plus(Duration.ofMinutes(accessTtl)),
                r.stored().getExpiresAt());
    }

    /**
     * Cross-module entry point used by the admin-portal impersonation flow
     * ({@code AdminService.impersonate} / {@code AdminService.exit}). Issues a
     * full {@link LoginResponse} for the supplied user — same shape as a regular
     * login — optionally with the {@code imp} claim set on the access token.
     *
     * <p>This sits in {@link AuthService} (rather than being duplicated in the
     * admin module) so the {@link AuthUser} build path stays single-sourced and
     * any future enrichment (e.g. propagating impersonation context onto the
     * AuthUser payload) automatically applies to both regular and impersonation
     * sessions.
     *
     * @param user                 the user whose identity the issued tokens will represent
     * @param deviceId             device id for refresh-token scoping
     * @param deviceType           WEB or MOBILE
     * @param impersonatedByUserId admin's user id when this is an impersonation
     *                             session; {@code null} for regular sessions
     */
    public LoginResponse loginAs(User user, String deviceId, DeviceType deviceType, String impersonatedByUserId) {
        TokenPair pair = issueTokens(user, deviceId, deviceType, impersonatedByUserId);
        AuthUser auth = buildAuthUser(user);
        // When this is an impersonation session, surface the context on the
        // AuthUser payload so the FE's sticky banner / identity store knows.
        if (impersonatedByUserId != null) {
            auth = new AuthUser(
                    auth.id(), auth.phone(), auth.role(), auth.name(), auth.whatsappNumber(),
                    auth.email(), auth.onboardingStep(), auth.profileComplete(),
                    auth.tpAccountId(), auth.orgName(), auth.plan(), auth.accountStatus(),
                    auth.partnerId(), auth.customerId(),
                    /* isImpersonated      */ true,
                    /* impersonatedByUserId */ impersonatedByUserId);
        }
        return new LoginResponse(
                pair.accessToken(),
                pair.refreshToken(),
                pair.accessTokenExpiresAt(),
                pair.refreshTokenExpiresAt(),
                auth.onboardingStep(),
                auth);
    }

    /** Composes the full LoginResponse envelope (tokens + onboardingStep + identity payload). */
    private LoginResponse buildLoginResponse(User user, TokenPair pair) {
        AuthUser auth = buildAuthUser(user);
        return new LoginResponse(
                pair.accessToken(),
                pair.refreshToken(),
                pair.accessTokenExpiresAt(),
                pair.refreshTokenExpiresAt(),
                auth.onboardingStep(),
                auth);
    }

    /**
     * Builds the {@link AuthUser} payload for a given {@link User}. Resolves TP-side
     * extras ({@code orgName}, {@code plan}, {@code accountStatus}) via the TP-account
     * facade so the auth module stays free of {@code TpAccount} / {@code Plan} /
     * {@code AccountStatus} imports.
     *
     * <p>Impersonation context is intentionally absent here — this builder runs at
     * login time, before any admin-impersonate flow can attach. PR R6 will branch
     * to populate {@code isImpersonated} when the call site is the impersonation
     * endpoint.
     */
    private AuthUser buildAuthUser(User user) {
        Integer step = user.getOnboardingStep() == null ? Integer.valueOf(1) : user.getOnboardingStep();
        boolean profileComplete = step != null && step >= 3;

        String orgName = null;
        String plan = null;
        String status = null;
        if (user.getTpAccountId() != null) {
            var summary = tpAccounts.findSummary(user.getTpAccountId()).orElse(null);
            if (summary != null) {
                orgName = summary.name();
                plan = summary.plan();
                status = summary.accountStatus();
            }
        }

        return new AuthUser(
                user.getId(),
                user.getPhone(),
                user.getRole(),
                user.getName(),
                user.getWhatsappNumber(),
                user.getEmail(),
                step,
                profileComplete,
                user.getTpAccountId(),
                orgName,
                plan,
                status,
                user.getPartnerTpProfileId(),
                user.getCustomerId(),
                /* isImpersonated      */ false,
                /* impersonatedByUserId */ null);
    }
}
