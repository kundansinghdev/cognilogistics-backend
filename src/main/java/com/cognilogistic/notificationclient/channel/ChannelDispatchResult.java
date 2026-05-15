package com.cognilogistic.notificationclient.channel;

import com.cognilogistic.notificationclient.model.NotificationStatus;

/**
 * Outcome of a single {@link NotificationChannel#send} call.
 *
 * <p>{@link com.cognilogistic.notificationclient.service.NotificationService} translates
 * this directly into a row on {@code notification_log} — {@link #status} maps to the
 * column of the same name, {@link #errorMessage} to {@code error_message}, and
 * {@link #templateOverride} (when non-null) replaces the default
 * {@code RenderedMessage.templateName} value written to {@code notification_log.template}.
 *
 * <p>Channels return {@link NotificationStatus#SENT} on success,
 * {@link NotificationStatus#FAILED} with a non-null {@link #errorMessage} on failure,
 * or — specifically for {@link WhatsAppTemplateChannel} in the pilot —
 * {@link NotificationStatus#GENERATED} with a {@link #templateOverride} JSON blob
 * carrying the rendered text + {@code wa.me} link.
 *
 * <p>Channels do NOT return {@link NotificationStatus#SKIPPED_PREFERENCE} —
 * preference filtering is performed by the dispatcher before any channel is called.
 *
 * @param status           dispatch outcome
 * @param errorMessage     diagnostic when {@link #status} is {@link NotificationStatus#FAILED};
 *                         {@code null} otherwise
 * @param templateOverride optional value to write to {@code notification_log.template} —
 *                         {@link WhatsAppTemplateChannel} uses this to persist the wa.me link.
 *                         {@code null} means "use the default templateName from the rendered message"
 */
public record ChannelDispatchResult(
        NotificationStatus status,
        String errorMessage,
        String templateOverride) {

    /** Convenience for the success path with no template override. */
    public static ChannelDispatchResult sent() {
        return new ChannelDispatchResult(NotificationStatus.SENT, null, null);
    }

    /**
     * Convenience for the WhatsApp pilot path. The {@code templateOverride} carries
     * the JSON blob (rendered body + wa.me link) that the in-app feed renders.
     *
     * @param templateOverride the JSON to write to {@code notification_log.template}
     */
    public static ChannelDispatchResult generated(String templateOverride) {
        return new ChannelDispatchResult(NotificationStatus.GENERATED, null, templateOverride);
    }

    /** Convenience for the failure path with a diagnostic message. */
    public static ChannelDispatchResult failed(String reason) {
        return new ChannelDispatchResult(NotificationStatus.FAILED, reason, null);
    }
}
