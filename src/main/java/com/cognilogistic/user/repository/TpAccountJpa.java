package com.cognilogistic.user.repository;

import com.cognilogistic.user.model.TpAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Internal Spring Data JPA repository for {@link TpAccount}.
 *
 * <p>Not used directly by other modules — they consume {@link TpAccountRepository}
 * instead. Keeping the JPA repo internal preserves the abstraction boundary that lets
 * us substitute a different persistence layer if ever needed.
 *
 * <p>Generic types: {@code <TpAccount, String>} — id is a CHAR(36) UUID per v5.0.
 */
public interface TpAccountJpa extends JpaRepository<TpAccount, String> {

    /**
     * Looks up the TP account owned by a given primary user. Used during onboarding
     * to retrieve the account immediately after creation.
     *
     * @param primaryUserId the user id (UUID) of the TP_ADMIN
     * @return the TP account if found, or empty
     */
    Optional<TpAccount> findByPrimaryUserId(String primaryUserId);
}
