package com.cognilogistic.auth.repository;

import com.cognilogistic.auth.model.OtpLog;
import com.cognilogistic.auth.model.OtpPurpose;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for {@link OtpLog}.
 *
 * <p>OTP lookup always uses the most-recently-created row for a phone+purpose, so the
 * client can request a fresh OTP at any time and the previous one is implicitly orphaned
 * (the older row's {@link OtpLog#isVerified()} stays false and it ages out via TTL).
 *
 * <p>The id type is {@link String} (CHAR(36) UUID at the schema level).
 */
public interface OtpLogRepository extends JpaRepository<OtpLog, String> {

    /**
     * Returns the most-recently-created OTP row for the given phone and purpose.
     * Used by both send (resend-cooldown enforcement) and verify (hash comparison).
     *
     * @param phone   the phone number the OTP was sent to
     * @param purpose the flow context — see {@link OtpPurpose} (FIRST_LOGIN | PIN_RESET)
     * @return the most recently created row, or empty if none exists
     */
    Optional<OtpLog> findTopByPhoneAndPurposeOrderBySentAtDesc(String phone, OtpPurpose purpose);

    /**
     * Returns every OTP row for a phone+purpose combination, newest first. Useful for
     * audit queries and rate-limiting analysis (e.g. "how many OTPs have we sent to this
     * phone in the last hour?").
     *
     * @param phone   the target phone
     * @param purpose the OTP purpose
     * @return list ordered newest-first; empty if no rows exist
     */
    List<OtpLog> findByPhoneAndPurposeOrderBySentAtDesc(String phone, OtpPurpose purpose);
}
