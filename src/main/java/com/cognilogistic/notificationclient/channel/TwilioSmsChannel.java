package com.cognilogistic.notificationclient.channel;

import com.cognilogistic.notificationclient.model.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * SMS delivery via Twilio.
 *
 * <p><strong>UAT default — mocked.</strong> {@code notifications.sms.mock=true} (the
 * default) makes this adapter log the SMS and return {@link ChannelDispatchResult#sent()}
 * without calling Twilio — local dev and UAT runs incur no SMS cost. Set the property
 * to {@code false} once Twilio credentials are configured to switch to real delivery.
 *
 * <p>Why this is a single bean rather than two ({@code MockSmsChannel} +
 * {@code TwilioSmsChannel} like {@link com.cognilogistic.auth.service.OtpProvider}):
 * the Twilio Java SDK isn't on the classpath yet for the SMS path (only Verify is
 * planned for OTPs), and adding it just to gate via {@code @ConditionalOnProperty}
 * adds a heavy dep for what is ultimately one HTTP call. The mock toggle stays inside
 * the same class until the Twilio Messages SDK lands; at that point the conditional-bean
 * split mirrors {@code OtpProvider}'s pattern.
 *
 * <p>The OTP infra (auth module's {@link com.cognilogistic.auth.service.OtpProvider})
 * uses Twilio Verify, not Twilio Messages — different APIs, different rate limits.
 * SMS notifications use Messages, which this adapter will wrap when implemented.
 */
@Component
public class TwilioSmsChannel implements NotificationChannel {

    private static final Logger log = LoggerFactory.getLogger(TwilioSmsChannel.class);

    /** When true, the channel logs and returns success — no real Twilio call. */
    private final boolean mockMode;

    /** The {@code From} phone number Twilio Messages will use. Required when {@link #mockMode} is false. */
    private final String fromNumber;

    public TwilioSmsChannel(@Value("${notifications.sms.mock:true}") boolean mockMode,
                            @Value("${notifications.sms.from:}") String fromNumber) {
        this.mockMode = mockMode;
        this.fromNumber = fromNumber;
    }

    @Override
    public Channel channel() {
        return Channel.SMS;
    }

    @Override
    public ChannelDispatchResult send(Recipient recipient, RenderedMessage message) {
        if (mockMode) {
            // Logs the body in full so devs and QA can verify template wording without paying for SMS.
            log.info("[MOCK SMS] to={} body={}", recipient.phone(), message.body());
            return ChannelDispatchResult.sent();
        }

        // The Twilio Messages SDK call is intentionally a TODO — we keep this stub returning
        // FAILED so a misconfiguration (mock=false but no SDK wired) surfaces as a notification_log
        // FAILED row instead of a silent no-op. Replace with the SDK call when integration lands.
        log.warn("Twilio SMS Messages SDK not yet integrated; returning FAILED. recipient={} from={}",
                recipient.phone(), fromNumber);
        return ChannelDispatchResult.failed("twilio_sms_not_configured");
    }
}
