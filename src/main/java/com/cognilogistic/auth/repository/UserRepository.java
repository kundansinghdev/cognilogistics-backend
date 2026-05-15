package com.cognilogistic.auth.repository;

import com.cognilogistic.auth.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Spring Data JPA repository for {@link User}.
 *
 * <p>The dominant lookup key is {@code phone}, not the database id, because every auth
 * flow identifies users by their phone number (DD-01).
 *
 * <p>The id type is {@link String} (CHAR(36) UUID at the schema level) — see
 * {@link User#getId()} for the rationale.
 */
public interface UserRepository extends JpaRepository<User, String> {

    /**
     * Finds a user by their globally-unique phone number. Used by every auth flow to
     * resolve a phone to a User entity before checking credentials or issuing tokens.
     *
     * @param phone the phone number (E.164 format)
     * @return the matching user, or empty if no account exists for that phone
     */
    Optional<User> findByPhone(String phone);

    /**
     * Quick existence check without loading the full entity. Used by deduplication
     * guards (e.g. before bulk user creation) and by the customer portal flow that
     * upgrades a shadow customer.
     *
     * @param phone the phone number to check
     * @return {@code true} if a user with that phone exists
     */
    boolean existsByPhone(String phone);
}
