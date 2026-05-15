package com.cognilogistic.notificationclient.repository;

import com.cognilogistic.notificationclient.model.NotificationPreference;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for the {@code notification_preferences} table.
 *
 * <p>The PK is {@code user_id} so {@link #findById(Object)} doubles as
 * {@code findByUserId} — there's only ever one row per user. Callers wrap the result
 * in an Optional and create defaults on first access via
 * {@link com.cognilogistic.notificationclient.model.NotificationPreference#defaultsFor(String)}.
 */
@Repository
public interface NotificationPreferenceRepository extends JpaRepository<NotificationPreference, String> {
}
