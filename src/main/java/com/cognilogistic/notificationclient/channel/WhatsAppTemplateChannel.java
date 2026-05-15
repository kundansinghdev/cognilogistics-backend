package com.cognilogistic.notificationclient.channel;

import com.cognilogistic.notificationclient.model.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * WhatsApp delivery — pilot mode (template generation only).
 *
 * <p>The pilot does not have a BSP (Business Service Provider) integration, so this
 * channel does not actually deliver WhatsApp messages. Instead it produces a
 * {@code wa.me/{phone}?text={body}} deep link — the TP user taps the link in the
 * in-app feed and forwards the templated text to their customer / partner manually.
 *
 * <p>Therefore the result is {@link ChannelDispatchResult#generated()} — not SENT —
 * to make the manual-delivery handoff explicit on the audit log row. The
 * {@link com.cognilogistic.notificationclient.service.NotificationService} encodes
 * the template body + wa.me URL as a small JSON blob on
 * {@code notification_log.template} so the in-app feed can render the link.
 *
 * <p>Post-UAT switch: set {@code notifications.whatsapp.mode=BSP} and inject the
 * BSP HTTP client; this class either grows a branch on the property, or gets
 * paired with a {@code WhatsAppBspChannel} sibling using
 * {@code @ConditionalOnProperty} for a clean compile-time split.
 */
@Component
public class WhatsAppTemplateChannel implements NotificationChannel {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppTemplateChannel.class);

    /** Mode flag — TEMPLATE_ONLY (UAT) or BSP (post-UAT). Currently informational only. */
    private final String mode;

    public WhatsAppTemplateChannel(@Value("${notifications.whatsapp.mode:TEMPLATE_ONLY}") String mode) {
        this.mode = mode;
    }

    @Override
    public Channel channel() {
        return Channel.WHATSAPP;
    }

    @Override
    public ChannelDispatchResult send(Recipient recipient, RenderedMessage message) {
        // wa.me requires the phone number without "+" or non-digit characters.
        String phoneDigits = recipient.effectiveWhatsappNumber().replaceAll("[^0-9]", "");
        String waLink = "https://wa.me/" + phoneDigits + "?text="
                + URLEncoder.encode(message.body(), StandardCharsets.UTF_8);

        log.info("[WA TEMPLATE] mode={} to={} link={}", mode, recipient.effectiveWhatsappNumber(), waLink);

        // Persist a small JSON blob on notification_log.template so the in-app feed
        // can render the link. The shape is intentionally minimal — column is VARCHAR(100)
        // so we keep keys short. See NotificationLog.java class-level Javadoc on the
        // payload-storage workaround.
        String payloadJson = "{\"name\":\"" + escapeJson(message.templateName()) + "\","
                + "\"link\":\"" + escapeJson(waLink) + "\"}";

        return ChannelDispatchResult.generated(payloadJson);
    }

    /** Escapes the two characters that would break a single-line JSON string literal. */
    private static String escapeJson(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
