package com.cognilogistic.auth.repository;

import com.cognilogistic.auth.model.DeviceTrust;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Spring Data JPA repository for {@link DeviceTrust}.
 *
 * <p>The dominant query is "is (user, device) currently trusted?" — answered by checking
 * whether a row exists and {@code trustedAt + 15 days > now}. The window threshold is
 * applied in service code rather than the query so the duration can be tuned without a
 * migration.
 */
public interface DeviceTrustRepository extends JpaRepository<DeviceTrust, String> {

    /**
     * Looks up the trust marker for a specific user-device pair, if one exists.
     *
     * @param userId   the user's CHAR(36) UUID
     * @param deviceId the client-supplied device identifier
     * @return the trust row, or empty if the device has not been trusted (or was deleted on user logout-all)
     */
    Optional<DeviceTrust> findByUserIdAndDeviceId(String userId, String deviceId);

    /**
     * Removes every trust marker for a user. Invoked on PIN reset (lost-device protection):
     * when a user resets their PIN, every trusted device must re-prove identity with a new
     * PIN entry on its next login.
     *
     * @param userId the user whose trust markers should be wiped
     * @return number of rows deleted
     */
    long deleteByUserId(String userId);
}
