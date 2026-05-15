package com.cognilogistic.notificationclient.service;

import com.cognilogistic.notificationclient.model.Channel;

import java.util.List;
import java.util.Map;

/**
 * One unit of work for the notification dispatcher: "send template X to user Y on
 * channels Z..., keyed by event {@code eventKey} so re-runs are idempotent."
 *
 * <p>{@link com.cognilogistic.notificationclient.service.EventListeners} builds these
 * from incoming domain events. The dispatcher
 * ({@link NotificationService#dispatch(NotificationDispatchRequest)}) iterates over
 * the channels, applies preference filtering, renders the template once, and writes
 * one {@code notification_log} row per channel.
 *
 * <p><strong>The {@code eventKey}.</strong> A stable string the dispatcher uses to
 * dedup re-runs of the same event — typically of the shape {@code "OrderDelivered:<orderId>"}
 * or {@code "TenderAwarded:<tenderId>:<bidId>"}. If the dispatcher sees the same key
 * within the in-memory cache TTL it skips dispatch entirely (no log rows). This is
 * the application-level workaround for the v5.0 schema dropping
 * {@code notification_log.event_id} (notification.md §10.1).
 *
 * @param eventKey      stable idempotency key for this event (see class-level Javadoc)
 * @param recipientUserId the user to notify (CHAR(36) UUID)
 * @param channels      channels to attempt for this recipient — preference filtering
 *                      runs per-channel inside the dispatcher
 * @param templateName  name of the template to render (must match a key in
 *                      {@link TemplateService}'s built-in map)
 * @param params        {@code {key}} placeholder values for the template; may be empty
 */
public record NotificationDispatchRequest(
        String eventKey,
        String recipientUserId,
        List<Channel> channels,
        String templateName,
        Map<String, String> params) {
}
