package com.cognilogistic.notificationclient.service;

import com.cognilogistic.notificationclient.dto.UpdatePreferencesRequest;
import com.cognilogistic.notificationclient.model.NotificationPreference;
import com.cognilogistic.notificationclient.repository.NotificationPreferenceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read / patch operations on {@code notification_preferences}, the user-facing surface
 * of the notification module.
 *
 * <p>Auto-create on read: the first time a user fetches their preferences and no row
 * exists, a defaults row is persisted so subsequent reads / patches operate on a real
 * row rather than re-creating defaults each call. The same lazy-create lives in
 * {@link NotificationService#loadOrCreatePreferences(String)} — pulled into a separate
 * service here so the controller doesn't pull in the dispatcher's whole dependency graph.
 */
@Service
public class PreferenceService {

    private final NotificationPreferenceRepository repository;

    public PreferenceService(NotificationPreferenceRepository repository) {
        this.repository = repository;
    }

    /**
     * Returns the user's preferences row, creating a defaults row if none exists.
     *
     * @param userId the authenticated user id (CHAR(36))
     * @return the persisted preferences row
     */
    @Transactional
    public NotificationPreference getOrCreate(String userId) {
        return repository.findById(userId)
                .orElseGet(() -> repository.save(NotificationPreference.defaultsFor(userId)));
    }

    /**
     * Applies a partial update to the user's preferences. Each null field on the request
     * means "leave the existing value alone."
     *
     * @param userId  the authenticated user id
     * @param request which channels to toggle
     * @return the updated row
     */
    @Transactional
    public NotificationPreference update(String userId, UpdatePreferencesRequest request) {
        NotificationPreference prefs = getOrCreate(userId);
        if (request.smsEnabled() != null) prefs.setSmsEnabled(request.smsEnabled());
        if (request.whatsappEnabled() != null) prefs.setWhatsappEnabled(request.whatsappEnabled());
        if (request.pushEnabled() != null) prefs.setPushEnabled(request.pushEnabled());
        return repository.save(prefs);
    }
}
