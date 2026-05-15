package com.cognilogistic.auth.service;

import com.cognilogistic.platform.api.ApiException;
import com.cognilogistic.platform.api.ErrorCode;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.regex.Pattern;

/**
 * Domain service for 4-digit PIN validation, hashing, and comparison.
 *
 * <p>PINs in this system are always exactly 4 decimal digits. BCrypt (cost 10) is used
 * for hashing, providing adequate brute-force resistance for the 4-digit key space combined
 * with the account lockout policy enforced by {@link AuthService}.
 */
@Service
public class PinService {

    /** Regex pattern enforcing the 4-digit PIN contract. */
    private static final Pattern FOUR_DIGITS = Pattern.compile("\\d{4}");

    /** BCrypt encoder with cost factor 10 — balances security vs. login latency. */
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(10);

    /**
     * Validates that the given PIN is exactly 4 decimal digits.
     * Throws immediately on failure; called before any hashing or database operation.
     *
     * @param pin the PIN string to validate
     * @throws com.cognilogistic.platform.api.ApiException with INVALID_PIN if the format is wrong
     */
    public void validateFormat(String pin) {
        String p = pin == null ? "" : pin.trim();
        if (!FOUR_DIGITS.matcher(p).matches()) {
            throw new ApiException(ErrorCode.INVALID_PIN, "PIN must be exactly 4 digits");
        }
    }

    /**
     * Validates format and returns the BCrypt hash of the PIN.
     * The hash is suitable for storage in the {@code auth_credentials.pin_hash} column.
     *
     * @param pin the raw 4-digit PIN to hash
     * @return BCrypt hash string
     */
    public String hash(String pin) {
        validateFormat(pin);
        return encoder.encode(pin.trim());
    }

    /**
     * Checks whether the raw PIN matches the stored BCrypt hash.
     * Returns {@code false} (rather than throwing) if either argument is null,
     * keeping the login path simple for the caller.
     *
     * @param pin  the raw PIN submitted by the user
     * @param hash the BCrypt hash stored in the database
     * @return {@code true} if the PIN is correct
     */
    public boolean matches(String pin, String hash) {
        if (pin == null || hash == null) return false;
        return encoder.matches(pin.trim(), hash);
    }
}
