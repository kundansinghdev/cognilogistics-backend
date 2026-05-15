package com.cognilogistic.notificationclient.repository;

import com.cognilogistic.notificationclient.model.DeviceRegistration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for {@code device_registrations}.
 *
 * <p>Two access patterns drive the interface:
 * <ul>
 *   <li>The push-fanout loop reads every device a user has registered —
 *       {@link #findByUserId(String)}.</li>
 *   <li>Re-registration of an existing device updates in place; the lookup is
 *       {@link #findByUserIdAndDeviceId(String, String)} which leverages the
 *       UNIQUE on {@code (user_id, device_id)}.</li>
 * </ul>
 *
 * <p>Post-UAT feature; in pilot the {@code PushChannel} adapter is a no-op so this
 * repo is exercised only by the device-registration endpoint when it lands.
 */
@Repository
public interface DeviceRegistrationRepository extends JpaRepository<DeviceRegistration, String> {

    /** All push-eligible devices for a single user. Used by push fanout. */
    List<DeviceRegistration> findByUserId(String userId);

    /**
     * Locates an existing registration for a given (user, device) pair so re-registration
     * can update the row in place rather than insert a duplicate. The DB-level UNIQUE on
     * the same columns is the safety net for races.
     */
    Optional<DeviceRegistration> findByUserIdAndDeviceId(String userId, String deviceId);
}
