package com.cognilogistic.config;

import com.cognilogistic.auth.service.AuthProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Set;

/**
 * Fails fast when production-like profiles run with insecure auth defaults
 * (placeholder JWT secret, log-only OTP delivery, OTP codes in logs).
 */
@Component
public class ProductionSecurityValidator {

    private static final Logger log = LoggerFactory.getLogger(ProductionSecurityValidator.class);

    private static final String INSECURE_JWT_PLACEHOLDER =
            "change-me-32-bytes-minimum-secret-for-uat-only-do-not-use-in-prod";

    private static final Set<String> STRICT_PROFILES = Set.of("prod", "production", "pilot");

    private final Environment env;
    private final AuthProperties auth;

    public ProductionSecurityValidator(Environment env, AuthProperties auth) {
        this.env = env;
        this.auth = auth;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void validate() {
        if (!isStrictProfile()) {
            return;
        }
        String secret = auth.jwt().secret();
        if (secret == null || secret.isBlank() || secret.equals(INSECURE_JWT_PLACEHOLDER)) {
            throw new IllegalStateException(
                    "Production profile requires a strong JWT_SECRET (not the UAT placeholder).");
        }
        if (secret.length() < 32) {
            throw new IllegalStateException("JWT_SECRET must be at least 32 characters in production.");
        }
        String delivery = env.getProperty("otp.delivery", "log");
        if (!"twilio".equalsIgnoreCase(delivery)) {
            throw new IllegalStateException(
                    "Production profile requires otp.delivery=twilio (mobile SMS), not log delivery.");
        }
        if (env.getProperty("otp.log-codes-in-server-logs", Boolean.class, Boolean.TRUE)) {
            throw new IllegalStateException(
                    "Production profile requires otp.log-codes-in-server-logs=false.");
        }
        log.info("Production security validation passed");
    }

    private boolean isStrictProfile() {
        return Arrays.stream(env.getActiveProfiles())
                .anyMatch(p -> STRICT_PROFILES.contains(p.toLowerCase()));
    }
}
