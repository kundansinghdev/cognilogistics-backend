package com.cognilogistic.user.repository;

import com.cognilogistic.auth.repository.UserRepository;
import com.cognilogistic.user.model.AccountStatus;
import com.cognilogistic.user.model.Plan;
import com.cognilogistic.user.model.TpAccount;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Default implementation of {@link TpAccountRepository} backed by Spring Data JPA.
 *
 * <p>Delegates persistence to {@link TpAccountJpa} and reads onboarding-step state from
 * {@link UserRepository}. Cross-module read coupling — auth module owns the User entity
 * but doesn't expose a service of its own that we'd consume; reaching directly into the
 * {@code UserRepository} from this user-module class is acceptable because the dependency
 * direction (user → auth) is already well-established (TP_ADMIN signup writes both).
 */
@Repository
public class TpAccountRepositoryImpl implements TpAccountRepository {

    private final TpAccountJpa jpa;
    private final UserRepository users;

    @Autowired
    public TpAccountRepositoryImpl(TpAccountJpa jpa, UserRepository users) {
        this.jpa = jpa;
        this.users = users;
    }

    /**
     * Creates a new TP account row owned by the given primary user.
     *
     * <p>The auth module's setup-pin flow calls this synchronously during a brand-new
     * TP signup. The new account starts in {@link AccountStatus#PENDING} (BR-PLN-02 —
     * Platform Admin must approve before business actions unlock) on the {@link Plan#BASIC}
     * tier (the default — only COGNILOGISTIC_ADMIN can upgrade via the Admin Portal).
     *
     * <p>The {@link TpAccount#getName() name} column is NOT NULL at the DB level but the
     * user hasn't supplied an organisation name yet — they'll do that during onboarding
     * step 2 (the profile-completion flow). We supply a temporary placeholder
     * ({@code "Pending Setup"}) so the row passes the NOT NULL constraint; the placeholder
     * is overwritten by {@code PATCH /auth/profile} as soon as the user fills in the form.
     *
     * @param primaryUserId the UUID of the TP_ADMIN user creating the account
     * @return the new TP account's UUID, to be stamped onto {@code users.tp_account_id}
     */
    @Override
    @Transactional
    public String createForPrimaryUser(String primaryUserId) {
        TpAccount tp = new TpAccount();
        tp.ensureId();
        tp.setPrimaryUserId(primaryUserId);
        // Placeholder name — overwritten when the user completes onboarding step 2.
        // The DB column is NOT NULL so we need *something* here at INSERT time.
        tp.setName("Pending Setup");
        // Defaults applied explicitly so the intent is obvious at the call site.
        // (The entity already defaults these; restating here makes the row's initial
        // state self-documenting and protects against future entity-level default changes.)
        tp.setPlan(Plan.BASIC);
        tp.setAccountStatus(AccountStatus.PENDING);
        tp.setNoGst(false);
        tp.setFleetOwner(false);
        return jpa.save(tp).getId();
    }

    /**
     * Returns the current onboarding step (1, 2, or 3) for the given user.
     *
     * <p>v5.0 places {@code onboarding_step} on the {@code users} table, not
     * {@code tp_accounts}. This implementation reads it from the user record so the
     * cross-module facade stays narrow.
     *
     * @param userId the user's UUID
     * @return the current step (1–3), or {@code null} if the user is not found
     */
    @Override
    public Integer getOnboardingStep(String userId) {
        return users.findById(userId)
                .map(u -> u.getOnboardingStep())
                .orElse(null);
    }

    @Override
    public java.util.Optional<TpAccountSummary> findSummary(String tpAccountId) {
        if (tpAccountId == null) return java.util.Optional.empty();
        return jpa.findById(tpAccountId).map(tp -> new TpAccountSummary(
                tp.getId(),
                tp.getName(),
                // Plan and account_status are emitted as the enum's name() string so the
                // auth module can consume them without an upward dep on Plan / AccountStatus.
                tp.getPlan() == null ? null : tp.getPlan().name(),
                tp.getAccountStatus() == null ? null : tp.getAccountStatus().name()));
    }

    @Override
    @Transactional
    public void updateName(String tpAccountId, String newName) {
        TpAccount tp = jpa.findById(tpAccountId)
                .orElseThrow(() -> new com.cognilogistic.platform.api.ApiException(
                        com.cognilogistic.platform.api.ErrorCode.OFFICE_NOT_FOUND,
                        "TP account not found: " + tpAccountId));
        tp.setName(newName);
        jpa.save(tp);
    }
}
