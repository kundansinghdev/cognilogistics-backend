package com.cognilogistic.auth.support;

import java.util.regex.Pattern;

/**
 * Normalises phone numbers for auth flows so OTP logs, user lookups, and tokens
 * always use the same canonical wire form (E.164 for Indian mobiles).
 *
 * <p>Callers should still rely on Jakarta Bean Validation on DTOs; this layer
 * handles whitespace, pasted {@code 91} prefixes, and mixed formatting before
 * persistence and OTP matching.
 */
public final class AuthPhoneNormalizer {

    private static final Pattern INDIAN_LOCAL = Pattern.compile("[6-9]\\d{9}");

    private AuthPhoneNormalizer() {}

    /**
     * @param raw phone from the HTTP body (may include spaces, +91, etc.)
     * @return canonical value for Indian mobiles ({@code +91XXXXXXXXXX}); otherwise
     *         a best-effort digit-only or trimmed pass-through for international numbers
     */
    public static String normalize(String raw) {
        if (raw == null) {
            return "";
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            return "";
        }
        String digits = trimmed.chars()
                .filter(Character::isDigit)
                .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
                .toString();
        if (digits.length() == 12 && digits.startsWith("91")) {
            String local = digits.substring(2);
            if (INDIAN_LOCAL.matcher(local).matches()) {
                return "+91" + local;
            }
        }
        if (digits.length() == 10 && INDIAN_LOCAL.matcher(digits).matches()) {
            return "+91" + digits;
        }
        if (trimmed.startsWith("+") && !digits.isEmpty()) {
            return "+" + digits;
        }
        if (digits.length() >= 10 && digits.length() <= 15) {
            return digits;
        }
        return trimmed.replaceAll("\\s+", "");
    }
}
