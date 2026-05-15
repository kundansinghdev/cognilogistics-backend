package com.cognilogistic.notificationclient.channel;

import com.cognilogistic.notificationclient.model.Channel;
import org.springframework.stereotype.Component;

/**
 * In-app notification channel — the user-facing feed.
 *
 * <p>There is no out-of-band delivery: the dispatcher's act of writing the
 * {@code notification_log} row IS the delivery, because the mobile / web app reads
 * the feed via {@code GET /api/v1/notifications}. So this adapter does nothing
 * itself — its only job is to return {@link ChannelDispatchResult#sent()} so the
 * dispatcher writes a {@link com.cognilogistic.notificationclient.model.NotificationStatus#SENT}
 * row.
 *
 * <p>Why have an adapter at all if it's a no-op: keeps the
 * {@link com.cognilogistic.notificationclient.service.NotificationService} dispatch
 * loop uniform — it iterates over channels and calls {@link #send} on each. Special-casing
 * IN_APP outside of the SPI would force two code paths that have to stay in sync.
 *
 * <p>{@link com.cognilogistic.notificationclient.model.Channel#IN_APP} is also never
 * gated by {@link com.cognilogistic.notificationclient.model.NotificationPreference} —
 * the user can turn off SMS / WhatsApp / Push but the in-app feed always works,
 * because that's what the app falls back to.
 */
@Component
public class InAppChannel implements NotificationChannel {

    @Override
    public Channel channel() {
        return Channel.IN_APP;
    }

    @Override
    public ChannelDispatchResult send(Recipient recipient, RenderedMessage message) {
        // The act of writing the notification_log row IS the delivery. The dispatcher
        // does the write; we just confirm SENT so it stamps the row's status correctly.
        return ChannelDispatchResult.sent();
    }
}
