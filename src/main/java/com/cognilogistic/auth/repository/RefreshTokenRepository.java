package com.cognilogistic.auth.repository;

import com.cognilogistic.auth.model.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for {@link RefreshToken}.
 *
 * <p>Manages refresh-token rotation, per-device session tracking, and bulk-revoke flows
 * (logout-all on PIN reset). Bulk-revoke methods use JPQL UPDATE so they execute as a
 * single SQL statement rather than loading entities into memory.
 */
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, String> {

    /**
     * Looks up a refresh token by its SHA-256 hex hash. Called during {@code /auth/refresh}
     * and {@code /auth/logout}. The DB has a UNIQUE constraint on token_hash so the result
     * is at most one row.
     *
     * @param tokenHash the hex-encoded SHA-256 digest of the raw refresh token string
     * @return the matching token row, or empty if not found
     */
    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /**
     * Returns every token row for a given user-device pair (for audit / debugging).
     * In normal operation there is at most one ACTIVE row per pair (DB UNIQUE constraint
     * enforces it), but historical revoked rows may exist.
     *
     * @param userId   the user's CHAR(36) UUID
     * @param deviceId the client-supplied device identifier
     * @return all token rows for that user+device, active or revoked
     */
    List<RefreshToken> findByUserIdAndDeviceId(String userId, String deviceId);

    /**
     * Bulk-revokes every active refresh token for a user, regardless of device.
     * Invoked on PIN reset to log the user out of every device simultaneously
     * (lost-device protection — see auth.md §3.3).
     *
     * @param userId the user whose tokens should be revoked
     * @param now    the revocation timestamp (typically {@code Instant.now()})
     * @return number of rows updated
     */
    @Modifying
    @Query("UPDATE RefreshToken r SET r.revokedAt = :now WHERE r.userId = :userId AND r.revokedAt IS NULL")
    int revokeAllForUser(@Param("userId") String userId, @Param("now") Instant now);

    /**
     * Bulk-revokes the active refresh token for a specific user-device pair. Invoked
     * before issuing a new token for the same device so the DB UNIQUE constraint on
     * {@code (user_id, device_id)} doesn't trip.
     *
     * @param userId   the user's UUID
     * @param deviceId the device whose token should be revoked
     * @param now      the revocation timestamp
     * @return number of rows updated (typically 0 or 1)
     */
    @Modifying
    @Query("UPDATE RefreshToken r SET r.revokedAt = :now WHERE r.userId = :userId AND r.deviceId = :deviceId AND r.revokedAt IS NULL")
    int revokeAllForUserDevice(@Param("userId") String userId, @Param("deviceId") String deviceId, @Param("now") Instant now);
}
