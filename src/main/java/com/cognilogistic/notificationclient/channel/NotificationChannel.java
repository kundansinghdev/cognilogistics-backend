package com.cognilogistic.notificationclient.channel;

import com.cognilogistic.notificationclient.model.Channel;

/**
 * SPI implemented by every notification delivery adapter (SMS, WhatsApp, Push, Email,
 * In-App). The {@link com.cognilogistic.notificationclient.service.NotificationService}
 * dispatcher discovers all implementations via Spring autowiring and routes each
 * notification by {@link #channel()}.
 *
 * <p><strong>Why an interface and not an enum-of-strategies:</strong> some channels
 * (Push, real-WhatsApp BSP) need their own configuration / SDK clients injected, which
 * Spring can do for an {@code @Service} bean but not for an enum constant. Keeping
 * this an interface also lets us add channels (post-UAT) without touching the dispatcher.
 *
 * <p><strong>Contract:</strong> implementations MUST be idempotent at the channel level
 * if the underlying provider is — Twilio is idempotent on its side via the Twilio
 * idempotency-key header (post-UAT). For UAT the
 * {@link com.cognilogistic.notificationclient.service.NotificationService} dedup cache
 * is the only safety net, so the dispatcher (not the channel) handles that.
 *
 * <p>Implementations should NEVER throw — error paths are returned via
 * {@link ChannelDispatchResult#failed(String)} so the dispatcher can record an
 * {@code error_message} on the log row instead of unwinding the listener.
 */
public interface NotificationChannel {

    /**
     * Identifies which {@link Channel} value this implementation handles. Used by the
     * dispatcher to look up the right adapter for a given notification.
     *
     * @return the channel value this implementation handles
     */
    Channel channel();

    /**
     * Delivers (or for WhatsApp-pilot, generates) the message to the recipient.
     *
     * <p>MUST NOT throw. Recoverable failures return
     * {@link ChannelDispatchResult#failed(String)}; unexpected programmer errors are
     * caught by the dispatcher and logged as FAILED rows.
     *
     * @param recipient the user identity / phone / WhatsApp number / locale
     * @param message   the rendered template payload
     * @return the dispatch outcome
     */
    ChannelDispatchResult send(Recipient recipient, RenderedMessage message);
}
