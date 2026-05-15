package com.cognilogistic.notificationclient.service;

import com.cognilogistic.notificationclient.channel.RenderedMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Renders notification templates by name, with optional per-locale variants.
 *
 * <p><strong>UAT implementation: built-in templates.</strong> The pilot ships a small
 * fixed set of templates in code (see {@link #BUILTIN_TEMPLATES}) so no template
 * authoring CMS / template-resource files are needed for v1. Once the set grows
 * past ~15 templates, this should move to {@code /resources/notification-templates/}
 * (notification.md §10).
 *
 * <p>Variable substitution is intentionally tiny — {@code {key}} placeholders are
 * replaced from the {@code params} map; missing keys are left as {@code {key}} so
 * a template authoring bug surfaces in the rendered output rather than silently
 * dropping the value.
 *
 * <p><strong>Localisation:</strong> the configured locale ({@code notifications.templates.locale},
 * default {@code hi-IN}) is the primary; {@code en-US} is the fallback if a Hindi
 * variant doesn't exist for a given template name.
 */
@Service
public class TemplateService {

    private static final Logger log = LoggerFactory.getLogger(TemplateService.class);

    /**
     * Built-in template strings keyed by {@code (name, locale)}. Hindi entries cover
     * the customer-facing order lifecycle; English entries are the fallback. New
     * templates added during iteration should keep this map alphabetised by name.
     */
    private static final Map<String, Map<String, String>> BUILTIN_TEMPLATES = Map.ofEntries(

            Map.entry("ORDER_CREATED", Map.of(
                    "hi-IN", "नमस्ते {customerName}! आपका ऑर्डर #{orderId} दर्ज हो गया है।",
                    "en-US", "Hello {customerName}! Your order #{orderId} has been placed.")),

            Map.entry("ORDER_ACKNOWLEDGED", Map.of(
                    "hi-IN", "आपका ऑर्डर #{orderId} {tpName} द्वारा स्वीकार कर लिया गया है।",
                    "en-US", "Your order #{orderId} has been acknowledged by {tpName}.")),

            Map.entry("ORDER_FLEET_CONFIRMED", Map.of(
                    "hi-IN", "ऑर्डर #{orderId} के लिए वाहन {vehicleReg} तय हो गया है।",
                    "en-US", "Vehicle {vehicleReg} confirmed for order #{orderId}.")),

            Map.entry("ORDER_IN_TRANSIT", Map.of(
                    "hi-IN", "आपका ऑर्डर #{orderId} रवाना हो गया है। वाहन {vehicleReg}.",
                    "en-US", "Your order #{orderId} is in transit. Vehicle {vehicleReg}.")),

            Map.entry("ORDER_DELIVERED", Map.of(
                    "hi-IN", "ऑर्डर #{orderId} सफलतापूर्वक डिलीवर हो गया है।",
                    "en-US", "Order #{orderId} has been delivered successfully.")),

            Map.entry("ORDER_CANCELLED", Map.of(
                    "hi-IN", "ऑर्डर #{orderId} रद्द कर दिया गया है। कारण: {reason}",
                    "en-US", "Order #{orderId} has been cancelled. Reason: {reason}")),

            Map.entry("TENDER_PUBLISHED", Map.of(
                    "hi-IN", "नया टेंडर #{tenderId}: {origin} → {destination}, वाहन {vehicleType}, मूल्य ₹{refPrice}.",
                    "en-US", "New tender #{tenderId}: {origin} → {destination}, vehicle {vehicleType}, ref ₹{refPrice}.")),

            Map.entry("TENDER_AWARDED", Map.of(
                    "hi-IN", "बधाई हो! टेंडर #{tenderId} आपको ₹{amount} पर मिल गया है।",
                    "en-US", "Congratulations! Tender #{tenderId} has been awarded to you at ₹{amount}.")),

            Map.entry("BID_REJECTED", Map.of(
                    "hi-IN", "टेंडर #{tenderId} पर आपकी बोली स्वीकार नहीं हुई।",
                    "en-US", "Your bid on tender #{tenderId} was not accepted.")),

            Map.entry("TP_ACCOUNT_APPROVED", Map.of(
                    "hi-IN", "स्वागत है! आपका CogniLogistic खाता स्वीकृत हो गया है।",
                    "en-US", "Welcome! Your CogniLogistic account has been approved.")),

            Map.entry("TP_ACCOUNT_REJECTED", Map.of(
                    "hi-IN", "आपका CogniLogistic खाता आवेदन अस्वीकृत कर दिया गया है।",
                    "en-US", "Your CogniLogistic account application was not approved.")),

            Map.entry("TP_PLAN_CHANGED", Map.of(
                    "hi-IN", "आपकी योजना {oldPlan} से {newPlan} कर दी गई है।",
                    "en-US", "Your plan has been changed from {oldPlan} to {newPlan}."))
    );

    private final String primaryLocale;
    private final String fallbackLocale;

    public TemplateService(@Value("${notifications.templates.locale:hi-IN}") String primaryLocale,
                           @Value("${notifications.templates.fallback-locale:en-US}") String fallbackLocale) {
        this.primaryLocale = primaryLocale;
        this.fallbackLocale = fallbackLocale;
    }

    /**
     * Renders a named template using the recipient's effective locale. Falls back
     * to the application's primary then fallback locale if the recipient's locale
     * is missing for that template.
     *
     * @param templateName    the template key (e.g. {@code "ORDER_DELIVERED"})
     * @param recipientLocale the recipient's BCP-47 locale, or {@code null} to use the platform primary
     * @param params          {@code {key}} placeholder values; may be empty
     * @return a rendered message ready to hand to a channel
     * @throws IllegalArgumentException if the template name is unknown — fail loud so a typo
     *                                  in a listener becomes a stack trace, not a silent miss
     */
    public RenderedMessage render(String templateName, String recipientLocale, Map<String, String> params) {
        Map<String, String> variants = BUILTIN_TEMPLATES.get(templateName);
        if (variants == null) {
            throw new IllegalArgumentException("Unknown notification template: " + templateName);
        }

        // Locale fallback chain: recipient → primary → fallback → first-available.
        String body = pickVariant(variants, recipientLocale);
        if (body == null) body = pickVariant(variants, primaryLocale);
        if (body == null) body = pickVariant(variants, fallbackLocale);
        if (body == null) {
            log.warn("Template {} has no variants for any configured locale; using arbitrary first entry.", templateName);
            body = variants.values().iterator().next();
        }

        return RenderedMessage.textOnly(templateName, substitute(body, params));
    }

    /** Returns the variant for {@code locale}, or null if absent. {@code null} locale is tolerated. */
    private static String pickVariant(Map<String, String> variants, String locale) {
        return locale == null ? null : variants.get(locale);
    }

    /** Replaces every {@code {key}} placeholder with {@code params.get(key)}, leaving unknown keys untouched. */
    private static String substitute(String template, Map<String, String> params) {
        if (params == null || params.isEmpty()) return template;
        String result = template;
        for (Map.Entry<String, String> e : params.entrySet()) {
            // Plain string replace — no regex, no escaping headaches.
            result = result.replace("{" + e.getKey() + "}", String.valueOf(e.getValue()));
        }
        return result;
    }
}
