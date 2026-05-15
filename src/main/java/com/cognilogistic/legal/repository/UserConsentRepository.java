package com.cognilogistic.legal.repository;

import com.cognilogistic.legal.model.UserConsent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Spring Data JPA repository for {@link UserConsent}.
 *
 * <p>Hot read: an admin asking for a user's full consent history, ordered most
 * recent first — see {@link #findByUserIdOrderByAcceptedAtDesc(String)}.
 * Backed by {@code idx_uc_user} on {@code (user_id)} plus an in-memory sort
 * on {@code accepted_at} (cheap; one user has 2–10 rows).
 */
@Repository
public interface UserConsentRepository extends JpaRepository<UserConsent, String> {

    /**
     * Returns every consent row for a single user, newest first. Drives the
     * {@code GET /api/v1/admin/users/{id}/consents} endpoint.
     *
     * @param userId the user's UUID
     * @return all consent rows for that user, ordered by {@code acceptedAt DESC}
     */
    List<UserConsent> findByUserIdOrderByAcceptedAtDesc(String userId);
}
