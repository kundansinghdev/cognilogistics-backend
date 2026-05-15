package com.cognilogistic.user.repository;

/**
 * Domain-facing facade for TP account persistence operations needed by the auth module.
 *
 * <p>Lives in the {@code user} module but is consumed by the {@code auth} module — the
 * facade pattern keeps auth from depending on JPA-specific repository types. The
 * implementation ({@link TpAccountRepositoryImpl}) delegates to {@link TpAccountJpa}.
 *
 * <p>This narrow interface is intentionally minimal: only the operations auth needs
 * during the signup → setup-pin → login flows. Wider TP-account management lives in
 * a separate user-module service that is not consumed cross-module.
 *
 * <p><strong>v5.0 alignment:</strong> all id parameters and return types are
 * CHAR(36) UUID {@link String}.
 */
public interface TpAccountRepository {

    /**
     * Creates a new TP account row owned by the given primary user.
     *
     * <p>Called from {@code AuthService.setupPin} during the first-login flow. The new
     * account starts with {@code account_status = 'PENDING'} per BR-PLN-02 — a Platform
     * Admin must approve before business actions are unlocked.
     *
     * @param primaryUserId the user id (UUID) of the TP_ADMIN who is creating the account
     * @return the newly-created TP account's id (UUID), to be stamped onto the user's
     *         {@code tp_account_id} column
     */
    String createForPrimaryUser(String primaryUserId);

    /**
     * Returns the current onboarding step (1, 2, or 3) for the given TP user.
     *
     * <p>Surfaced to the front-end via {@code LoginResponse.onboardingStep} so the app
     * routes to the right screen — banner / wizard / dashboard.
     *
     * <p><strong>v5.0 note:</strong> the onboarding_step column lives on {@code users},
     * not {@code tp_accounts} (which is what older drafts had). The argument here is
     * the user id, not the TP account id, despite the historical method name.
     *
     * @param userId the user's UUID
     * @return the current step (1–3), or {@code null} if the user is not found
     */
    Integer getOnboardingStep(String userId);

    /**
     * Returns a small read-only snapshot of a TP account: name, plan, and account status.
     *
     * <p>Used by the auth module to populate the {@code AuthUser} payload on login /
     * profile responses without dragging the auth module into the user-module entity
     * types ({@code TpAccount}, {@code Plan}, {@code AccountStatus}). Plan and status
     * are returned as their {@code Enum#name()} string form.
     *
     * @param tpAccountId the TP account's UUID
     * @return the summary if found, or empty otherwise
     */
    java.util.Optional<TpAccountSummary> findSummary(String tpAccountId);

    /**
     * Overwrites the {@code tp_accounts.name} (organisation name) for the given TP.
     * Used by the {@code PATCH /auth/profile} flow when the user supplies their
     * organisation name during onboarding step 2 — the placeholder
     * {@code "Pending Setup"} written at signup is replaced with the real value here.
     *
     * @param tpAccountId the TP account's UUID
     * @param newName     the new organisation name (NOT NULL, max 255 chars per schema)
     */
    void updateName(String tpAccountId, String newName);

    /**
     * Read-only projection of a TP account, returned by {@link #findSummary}.
     *
     * @param id            the TP account UUID
     * @param name          organisation name
     * @param plan          plan tier as the {@code Plan} enum's name() string
     *                      ({@code "BASIC"} / {@code "PREMIUM"} / {@code "ENTERPRISE"})
     * @param accountStatus account workflow state as the {@code AccountStatus} enum's name()
     *                      ({@code "PENDING"} / {@code "APPROVED"} / {@code "REJECTED"})
     */
    record TpAccountSummary(String id, String name, String plan, String accountStatus) {}
}
