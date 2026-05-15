package com.cognilogistic.notificationclient.dto;

import com.cognilogistic.notificationclient.model.NotificationPreference;

/**
 * Wire response for {@code GET /api/v1/me/notification-preferences}.
 *
 * <p>One-to-one with the {@link NotificationPreference} entity columns. Uses primitive
 * booleans because the entity columns are {@code NOT NULL} — there are no tristate
 * (null = "not set") values to surface.
 *
 * @param smsEnabled      whether the user accepts SMS notifications
 * @param whatsappEnabled whether the user accepts WhatsApp template messages
 * @param pushEnabled     whether the user accepts push notifications (post-UAT)
 */
public record NotificationPreferenceDto(
        boolean smsEnabled,
        boolean whatsappEnabled,
        boolean pushEnabled) {

    /** Maps the entity to its wire form. */
    public static NotificationPreferenceDto from(NotificationPreference p) {
        return new NotificationPreferenceDto(p.isSmsEnabled(), p.isWhatsappEnabled(), p.isPushEnabled());
    }
}
