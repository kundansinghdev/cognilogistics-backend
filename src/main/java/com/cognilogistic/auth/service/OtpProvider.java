package com.cognilogistic.auth.service;

/**
 * SPI for OTP delivery. {@link LoggingOtpProvider} logs codes for local/UAT;
 * {@link TwilioOtpProvider} sends via Twilio Verify in production.
 */
public interface OtpProvider {
    /**
     * Deliver the OTP. Implementations may ignore the code if delivery is opaque
     * (e.g. Twilio Verify generates the code itself — in that case, return the
     * Twilio-generated code from this call so the server can hash and store it).
     */
    String send(String phone, String code);
}
