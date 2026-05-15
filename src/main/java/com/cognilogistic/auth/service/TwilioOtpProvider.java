package com.cognilogistic.auth.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Production OTP delivery implementation via Twilio Verify, active when {@code otp.delivery=twilio}.
 *
 * <p>Currently a stub: the Twilio Verify SDK integration is planned before UAT cutover.
 * When implemented, this class will delegate to the Twilio Verify API, which generates
 * and delivers its own OTP code — the server will receive the Twilio-generated code back
 * from {@link #send} so it can store the hash for later verification.
 *
 * <p>In production Twilio Verify mode, the {@code code} parameter passed in may be ignored
 * by Twilio (Twilio generates the code on their side). The returned value from the API call
 * should be what gets hashed and stored.
 */
@Service
@ConditionalOnProperty(name = "otp.delivery", havingValue = "twilio")
public class TwilioOtpProvider implements OtpProvider {

    private static final Logger log = LoggerFactory.getLogger(TwilioOtpProvider.class);

    /**
     * Sends an OTP to the specified phone via Twilio Verify (currently a logged stub).
     * Post-UAT: replace this body with a real Twilio Verify SDK call.
     *
     * @param phone the destination phone number in E.164 format
     * @param code  the server-generated OTP (may be replaced by Twilio's own code in real impl)
     * @return the OTP code that was effectively sent (for hashing and storage)
     */
    @Override
    public String send(String phone, String code) {
        // TODO: integrate Twilio Verify SDK before UAT cutover. For now, log and return code.
        log.info("[TWILIO STUB] would send OTP to {} (code suppressed)", phone);
        return code;
    }
}
