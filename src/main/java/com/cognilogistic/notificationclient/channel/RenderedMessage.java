package com.cognilogistic.notificationclient.channel;

/**
 * The fully-rendered, channel-ready content of one notification.
 *
 * <p>The {@link com.cognilogistic.notificationclient.service.TemplateService} produces
 * one of these from a template name + a parameter map; the
 * {@link com.cognilogistic.notificationclient.service.NotificationService} hands it
 * to whichever {@link NotificationChannel} matches the chosen
 * {@link com.cognilogistic.notificationclient.model.Channel}.
 *
 * <p>Why a record and not a String: WhatsApp template generation needs both the
 * rendered text AND a deep-link URL ({@code wa.me/...}) recorded together. The
 * record carries everything a channel might want without each adapter having to
 * re-render. {@link #subject} is a placeholder for the post-UAT email channel; in
 * pilot all channels ignore it.
 *
 * @param templateName name of the template that produced this message (recorded on
 *                     {@code notification_log.template} for traceability)
 * @param subject      optional subject line — used by EMAIL only (post-UAT). May be {@code null}
 * @param body         the rendered body text. For SMS this is the message; for WhatsApp
 *                     this is the body of the template the user will forward; for in-app
 *                     this is what the feed entry shows
 * @param deepLinkUrl  optional URL to embed (e.g. a {@code wa.me/...} link, or a
 *                     mobile-app deep link). May be {@code null}
 */
public record RenderedMessage(
        String templateName,
        String subject,
        String body,
        String deepLinkUrl) {

    /** Convenience for channels that don't need a subject or deep link. */
    public static RenderedMessage textOnly(String templateName, String body) {
        return new RenderedMessage(templateName, null, body, null);
    }
}
