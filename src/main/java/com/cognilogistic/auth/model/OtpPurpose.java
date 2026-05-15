package com.cognilogistic.auth.model;

/**
 * Classifies why an OTP was issued, preventing cross-flow OTP reuse attacks.
 *
 * <p>The {@code OtpService} stores and verifies OTPs keyed on (phone, purpose).
 * An OTP issued for one purpose cannot be submitted against a different-purpose endpoint
 * because both the send and verify calls explicitly pass the same {@code OtpPurpose}.
 */
public enum OtpPurpose {

    /**
     * OTP for a brand-new TP user or an existing user performing their first login on a device.
     * Verified at {@code /auth/verify-otp}; result is exchanged at {@code /auth/setup-pin}.
     */
    FIRST_LOGIN,

    /**
     * OTP for resetting a forgotten 4-digit PIN.
     * Verified at {@code /auth/reset-pin/verify-otp}; result is exchanged at {@code /auth/reset-pin/set}.
     */
    PIN_RESET,

    /**
     * OTP for customer-portal login (OTP-only, no PIN).
     * Verified at {@code /portal/auth/verify-otp}; the result is a CUSTOMER-role JWT directly.
     */
    PORTAL_LOGIN
}
