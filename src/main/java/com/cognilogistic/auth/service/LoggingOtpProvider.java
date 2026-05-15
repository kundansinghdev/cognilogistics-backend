package com.cognilogistic.auth.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Non-SMS OTP delivery for local, UAT, and integration tests: the server-generated code
 * from {@link OtpService} is written to logs (when enabled) and retained in memory for
 * test helpers. When {@code auth.otp.fixed-code} is set (local/test), that value is used
 * instead of a random code from {@link Hashing}.
 *
 * <p>Active when {@code otp.delivery=log} (default). Set {@code otp.delivery=twilio} for
 * production mobile delivery via {@link TwilioOtpProvider}.
 */
@Service
@ConditionalOnProperty(name = "otp.delivery", havingValue = "log", matchIfMissing = true)
public class LoggingOtpProvider implements OtpProvider {

    private static final Logger log = LoggerFactory.getLogger(LoggingOtpProvider.class);

    /** phone → last OTP issued (integration tests only). */
    private final Map<String, String> lastSent = new ConcurrentHashMap<>();

    private final boolean logCodesInServerLogs;

    public LoggingOtpProvider(
            @Value("${otp.log-codes-in-server-logs:true}") boolean logCodesInServerLogs) {
        this.logCodesInServerLogs = logCodesInServerLogs;
    }

    @Override
    public String send(String phone, String code) {
        if (logCodesInServerLogs) {
            log.info(
                    "[OTP] phone={} code={} (log delivery — not sent via SMS; use this code to verify)",
                    phone,
                    code);
        } else {
            log.info(
                    "[OTP] phone={} code issued (log delivery — code hidden; set otp.log-codes-in-server-logs=true to log it)",
                    phone);
        }
        lastSent.put(phone, code);
        return code;
    }

    /** Test helper: last OTP sent to {@code phone}, or {@code null} if none. */
    public String lastCodeFor(String phone) {
        return lastSent.get(phone);
    }
}
