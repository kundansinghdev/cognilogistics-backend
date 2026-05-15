package com.cognilogistic.notificationclient.channel;

/**
 * The minimal set of fields a {@link NotificationChannel} needs to identify and reach
 * a recipient. Constructed by
 * {@link com.cognilogistic.notificationclient.service.NotificationService} from the
 * caller's {@code users} row before delegating to a channel.
 *
 * <p>Why a record and not the {@code User} entity: the channel layer should not depend
 * on auth's entity model — keeps the SPI small and unit-testable without an EntityManager.
 *
 * @param userId         CHAR(36) UUID — the recipient's user id
 * @param phone          phone number in E.164 (e.g. {@code +919876543210}); SMS / WhatsApp default
 * @param whatsappNumber optional separate WhatsApp number; falls back to {@code phone} when null
 * @param locale         BCP-47 locale tag (e.g. {@code hi-IN}); template selection uses this
 */
public record Recipient(
        String userId,
        String phone,
        String whatsappNumber,
        String locale) {

    /**
     * Returns the WhatsApp-effective number — either the user's separate WhatsApp
     * number, or their primary phone as fallback. Used by
     * {@link com.cognilogistic.notificationclient.channel.WhatsAppTemplateChannel}
     * to build the {@code wa.me/} deep link.
     */
    public String effectiveWhatsappNumber() {
        return (whatsappNumber == null || whatsappNumber.isBlank()) ? phone : whatsappNumber;
    }
}
