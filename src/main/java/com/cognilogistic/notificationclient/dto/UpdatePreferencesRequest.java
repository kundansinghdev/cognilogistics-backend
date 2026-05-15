package com.cognilogistic.notificationclient.dto;

/**
 * Wire request for {@code PATCH /api/v1/me/notification-preferences}.
 *
 * <p>All fields are {@link Boolean} (not {@code boolean}) so the JSON deserialiser
 * can distinguish "field absent" (don't change) from "field set to false" (turn off).
 * The controller treats {@code null} as "leave the existing value alone" — partial
 * updates are the dominant use case (the user toggles one channel at a time).
 *
 * @param smsEnabled      new SMS opt-in, or {@code null} to leave unchanged
 * @param whatsappEnabled new WhatsApp opt-in, or {@code null} to leave unchanged
 * @param pushEnabled     new push opt-in, or {@code null} to leave unchanged
 */
public record UpdatePreferencesRequest(
        Boolean smsEnabled,
        Boolean whatsappEnabled,
        Boolean pushEnabled) {
}
