package com.cognilogistic.auth.repository;

import com.cognilogistic.auth.model.AuthCredentials;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Spring Data JPA repository for {@link AuthCredentials}.
 *
 * <p>Note that the entity's primary key IS the user id — there is no surrogate id column.
 * Therefore {@link #findById(String)} from the parent interface returns the credentials
 * for that user. We expose {@link #findByUserId(String)} as a synonym for callers that
 * find "by user id" more readable.
 */
public interface AuthCredentialsRepository extends JpaRepository<AuthCredentials, String> {

    /**
     * Returns the credentials row for the given user, or empty if the user has not yet
     * set a PIN (e.g. CUSTOMER portal users — by design they never have a row here).
     *
     * <p>Identical to {@code findById(userId)} since {@code user_id} IS the PK; both names
     * are valid Spring Data JPA derivations of the same query. We keep this method around
     * for self-documenting call-site readability.
     *
     * @param userId the user's CHAR(36) UUID
     * @return the credentials row, or empty
     */
    Optional<AuthCredentials> findByUserId(String userId);
}
