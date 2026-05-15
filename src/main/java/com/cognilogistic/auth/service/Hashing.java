package com.cognilogistic.auth.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Stateless utility class providing cryptographic primitives used by the auth module.
 *
 * <p>All methods are static; this class cannot be instantiated.
 * It is intentionally kept free of Spring dependencies so it can be used in unit tests
 * without a Spring context.
 */
public final class Hashing {

    private static final SecureRandom RANDOM = new SecureRandom();

    private Hashing() {}

    /**
     * Generates a cryptographically random token as a URL-safe Base64 string without padding.
     * Used for opaque refresh-token raw values (e.g., {@code randomBase64Url(32)} → 43 chars).
     *
     * @param bytes the number of random bytes to generate before encoding
     * @return URL-safe Base64 string (no {@code =} padding)
     */
    public static String randomBase64Url(int bytes) {
        byte[] buf = new byte[bytes];
        RANDOM.nextBytes(buf);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(buf);
    }

    /**
     * Computes the SHA-256 digest of a UTF-8 encoded string and returns it as a lowercase hex string.
     * Used to store and compare OTP codes and refresh tokens without keeping raw values in the database.
     *
     * @param input the plaintext value to hash
     * @return 64-character lowercase hex string
     * @throws IllegalStateException if SHA-256 is somehow unavailable on the JVM (should never happen)
     */
    public static String sha256Hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] d = md.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(d);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    /**
     * Generates a numeric-only OTP of the specified digit length using a cryptographically
     * secure random source. Each digit is independently chosen from [0..9].
     *
     * @param length the number of digits; the system always uses 6 for OTPs
     * @return a string of exactly {@code length} decimal digits (may start with {@code '0'})
     */
    public static String numericOtp(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(RANDOM.nextInt(10));
        }
        return sb.toString();
    }
}
