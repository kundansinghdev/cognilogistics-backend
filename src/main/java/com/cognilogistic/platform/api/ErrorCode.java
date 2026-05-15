package com.cognilogistic.platform.api;

import org.springframework.http.HttpStatus;

/**
 * Exhaustive enumeration of all domain-level error codes in the CogniLogistic platform.
 *
 * <p>Each constant maps to an HTTP status code that the {@link GlobalExceptionHandler}
 * uses when converting an {@link ApiException} into an HTTP response.
 * Client applications should switch on the {@code code} string in the JSON error response
 * rather than on the HTTP status, since the same status may carry multiple distinct codes.
 */
public enum ErrorCode {

    /** A state-machine transition that is not allowed for the current order status (BR-01). */
    INVALID_TRANSITION(HttpStatus.UNPROCESSABLE_ENTITY),

    /** An attempt to cancel an order that is already IN_TRANSIT or DELIVERED (BR-02). */
    CANCELLATION_NOT_ALLOWED(HttpStatus.UNPROCESSABLE_ENTITY),

    /** The submitted PIN is not exactly 4 digits or does not match the stored hash. */
    INVALID_PIN(HttpStatus.BAD_REQUEST),

    /** The submitted OTP does not match the stored hash. */
    INVALID_OTP(HttpStatus.BAD_REQUEST),

    /** The OTP's validity window has passed. */
    OTP_EXPIRED(HttpStatus.BAD_REQUEST),

    /** The OTP has already been successfully consumed (single-use enforcement). */
    OTP_USED(HttpStatus.BAD_REQUEST),

    /** The account is temporarily locked after too many failed PIN attempts. */
    ACCOUNT_LOCKED(HttpStatus.LOCKED),

    /** The phone number belongs to a shadow (placeholder) account that cannot log in. */
    SHADOW_ACCOUNT(HttpStatus.FORBIDDEN),

    /**
     * Self-service auth (signup, OTP onboarding, PIN reset) was attempted for a
     * {@code COGNILOGISTIC_ADMIN} phone. Platform admins are provisioned in the
     * database and may sign in only via {@code POST /auth/login}.
     */
    ADMIN_LOGIN_ONLY(HttpStatus.FORBIDDEN),

    /** The client is requesting OTPs too frequently (resend cooldown not elapsed). */
    RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS),

    /**
     * {@code POST /auth/reset-pin/send-otp} (or verify) for a phone that has no row
     * in {@code users}. No OTP is sent — avoids spamming arbitrary numbers.
     */
    PHONE_NOT_REGISTERED(HttpStatus.NOT_FOUND),

    /** The Vahan registry API is unavailable or returned an error. */
    VAHAN_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE),

    /** The Sarathi (driver licence registry) API is unavailable or returned an error. */
    SARATHI_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE),

    /**
     * Fleet confirmation / lookup was attempted for an FTL order without a prior
     * Vahan/Sarathi consent log (BR-10). 422 (UNPROCESSABLE_ENTITY) — the request
     * was syntactically valid but a required prior step is missing.
     *
     * <p>Renamed 2026-05-08 from {@code CONSENT_REQUIRED} to free that name up
     * for the legal-consent flow (T&amp;C / Privacy at signup, see
     * {@link #CONSENT_REQUIRED} below). Front-end may need to update its
     * switch-on-error-code if it was matching the legacy name for Vahan failures.
     */
    INTEGRATION_CONSENT_REQUIRED(HttpStatus.UNPROCESSABLE_ENTITY),

    /**
     * Signup is missing one or both of the required legal-doc consent fields
     * (T&amp;C version, Privacy version). Raised by {@code POST /auth/setup-pin}
     * when {@code acceptedTermsVersion} or {@code acceptedPrivacyVersion} is
     * null/blank. Front-end shows the consent checkbox tick-required banner
     * and re-submits. See BACKEND_SPEC_TC_CONSENT.md §4.1.
     */
    CONSENT_REQUIRED(HttpStatus.BAD_REQUEST),

    /**
     * Signup-supplied consent versions don't match the currently published
     * versions in {@code legal_doc_versions}. Catches stale browser tabs:
     * user opened the signup page yesterday, T&amp;C was republished overnight.
     * Front-end re-fetches {@code GET /api/v1/legal/current-versions}, prompts
     * re-acceptance, then re-submits with the fresh version strings.
     */
    CONSENT_VERSION_MISMATCH(HttpStatus.BAD_REQUEST),

    /**
     * {@code POST /auth/setup-pin} was called without a valid {@code orgName}.
     * Per BACKEND_GAPS §1.9 (2026-05-09 design call), business name must be
     * captured at signup time — the FE collects it on the {@code /confirm-name}
     * step between OTP verification and PIN setup, then submits it on
     * {@code setup-pin}. Required string, 2-100 chars after trim. Backend
     * persists it to {@code tp_accounts.name} so the new TP account is named
     * immediately rather than waiting for the post-login profile-completion
     * banner. Front-end maps this code to a friendly inline field error.
     */
    ORG_NAME_REQUIRED(HttpStatus.BAD_REQUEST),

    /** The requested order does not exist or does not belong to the caller's TP account. */
    ORDER_NOT_FOUND(HttpStatus.NOT_FOUND),

    /** The requested company does not exist or does not belong to the caller's TP account. */
    COMPANY_NOT_FOUND(HttpStatus.NOT_FOUND),

    /** The requested branch office does not exist or does not belong to the caller's TP account. */
    OFFICE_NOT_FOUND(HttpStatus.NOT_FOUND),

    /** The office code already exists for this TP account — codes must be unique per account (BR-OFF-02). */
    OFFICE_CODE_EXISTS(HttpStatus.CONFLICT),

    /**
     * Another branch office already uses the same name, city, and state under this TP account
     * (application-level duplicate guard; distinct from {@link #OFFICE_CODE_EXISTS}).
     */
    OFFICE_LOCATION_DUPLICATE(HttpStatus.CONFLICT),

    /** Deactivation blocked: the office has orders that are not DELIVERED or CANCELLED (BR-OFF-06). */
    OFFICE_HAS_ACTIVE_ORDERS(HttpStatus.UNPROCESSABLE_ENTITY),

    /** Company Master already has this GSTIN for the TP account (matches DB {@code uq_company_tp_gstin}). */
    COMPANY_GSTIN_EXISTS(HttpStatus.CONFLICT),

    /** A no-GST company with the same legal name already exists for this TP account. */
    COMPANY_LEGAL_NAME_EXISTS(HttpStatus.CONFLICT),

    /** The provided GSTIN does not match the standard 15-character Indian GSTIN format (BR-OFF-03). */
    INVALID_GSTIN(HttpStatus.BAD_REQUEST),

    /** The vehicle registration number + pickup date combination is already used by another active order. */
    VEHICLE_DATE_CONFLICT(HttpStatus.UNPROCESSABLE_ENTITY),

    /** The customer does not have portal access enabled, or the account does not exist. */
    PORTAL_ACCESS_DENIED(HttpStatus.FORBIDDEN),

    // -------------------------------------------------------------------------
    // v2.2 additions — plan / multi-tenant / impersonation gating
    // (See platform.md §3.2 and admin.md for the rules that raise these.)
    // -------------------------------------------------------------------------

    /**
     * The TP account is in {@code PENDING} status — a CogniLogistic Platform Admin has
     * not yet approved the signup. All business actions (create order, post tender,
     * confirm fleet, etc.) are blocked until the account is APPROVED.
     *
     * <p>Read endpoints can stay open so the user sees an empty / banner state.
     * Raised by the cross-cutting account-status gate that every state-changing
     * controller method invokes. See user.md §3.1 (BR-PLN-02).
     */
    ACCOUNT_PENDING_APPROVAL(HttpStatus.FORBIDDEN),

    /**
     * The TP account has been REJECTED by a Platform Admin. The user can still log in
     * (so the front-end can show a "your application was declined" screen) but every
     * business endpoint refuses with this error. See admin.md §3.2.
     */
    ACCOUNT_REJECTED(HttpStatus.FORBIDDEN),

    /**
     * The caller's plan does not grant access to the requested module. For example,
     * a TP on the BASIC plan trying to access the Order or Branch Office module.
     *
     * <p>Source of truth for which modules require which plan is the
     * {@code plan_access_rules} table; the check is performed by
     * {@code PlanAccessService.canAccess(tpAccountId, moduleKey)}. See user.md §3.2 (BR-PLN-01).
     */
    PLAN_UPGRADE_REQUIRED(HttpStatus.FORBIDDEN),

    /**
     * The TP is on the BASIC plan and has reached the monthly tender-creation cap
     * (5 tenders per calendar month, tracked via {@code plan_usage.tender_count}).
     * The user must wait until next month or upgrade to PREMIUM/ENTERPRISE.
     *
     * <p>Raised by {@code PlanUsageService} at the moment a tender is published
     * (DRAFT → IN_PROGRESS), not at draft-creation time. See tender.md §3.3 (BR-PLN-03).
     */
    TENDER_LIMIT_REACHED(HttpStatus.UNPROCESSABLE_ENTITY),

    /**
     * The caller attempted an admin-impersonation action but is not a
     * {@code COGNILOGISTIC_ADMIN}. Only Platform Admins can call the
     * {@code /api/v1/admin/impersonate*} endpoints. See admin.md §3.4 (BR-IMP-05).
     */
    IMPERSONATION_NOT_ALLOWED(HttpStatus.FORBIDDEN),

    /**
     * A Platform Admin attempted to impersonate a customer record where
     * {@code customers.is_shadow = TRUE}. Shadow customers have no real session
     * to enter, so impersonation is meaningless and explicitly blocked.
     * See admin.md §3.4 (BR-IMP-04).
     */
    SHADOW_IMPERSONATION_BLOCKED(HttpStatus.FORBIDDEN),

    // -------------------------------------------------------------------------
    // Authentication / authorisation (generic)
    // -------------------------------------------------------------------------

    /** No valid JWT was provided, or the JWT is expired/tampered. */
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED),

    /** A valid JWT was provided but the user lacks permission for the requested resource. */
    FORBIDDEN(HttpStatus.FORBIDDEN),

    /** The request body or query parameters failed Bean Validation. */
    VALIDATION_ERROR(HttpStatus.BAD_REQUEST),

    /** An unexpected server-side error occurred; see server logs for details. */
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR);

    /** The HTTP status code to use when this error is returned to the client. */
    private final HttpStatus status;

    ErrorCode(HttpStatus status) {
        this.status = status;
    }

    /**
     * Returns the HTTP status code associated with this error code.
     *
     * @return the corresponding {@link HttpStatus}
     */
    public HttpStatus status() {
        return status;
    }
}
