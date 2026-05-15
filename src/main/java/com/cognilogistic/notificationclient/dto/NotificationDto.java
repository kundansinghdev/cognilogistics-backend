package com.cognilogistic.notificationclient.dto;

import com.cognilogistic.notificationclient.model.Channel;
import com.cognilogistic.notificationclient.model.NotificationLog;
import com.cognilogistic.notificationclient.model.NotificationStatus;

import java.time.Instant;

/**
 * Wire shape for a single in-app feed entry — the response of
 * {@code GET /api/v1/notifications}.
 *
 * <p>For UAT we return the {@link NotificationLog} fields verbatim. Read-state is
 * client-side (notification.md §10.4) so there's no {@code readAt} on the wire yet.
 *
 * @param id           the notification log row id (UUID)
 * @param channel      always IN_APP for the feed; included for forward-compatibility
 * @param template     template name (or, for WhatsApp GENERATED rows, a JSON blob —
 *                     the client renders the link from this)
 * @param status       SENT for normal entries; FAILED / GENERATED carry their own meaning
 * @param sentAt       when the notification was logged
 * @param errorMessage diagnostic when status=FAILED; null otherwise
 */
public record NotificationDto(
        String id,
        Channel channel,
        String template,
        NotificationStatus status,
        Instant sentAt,
        String errorMessage) {

    /** Maps a {@link NotificationLog} entity to its wire shape. */
    public static NotificationDto from(NotificationLog n) {
        return new NotificationDto(
                n.getId(),
                n.getChannel(),
                n.getTemplate(),
                n.getStatus(),
                n.getSentAt(),
                n.getErrorMessage());
    }
}
